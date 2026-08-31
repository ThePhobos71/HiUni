### Task 24: IMAP-Drafts: Backup schreiben & lesen

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/MailBackup.kt`
- Modify: `app/src/main/java/de/transio/hiuni/feature/email/...` (Hook in bestehenden Mail-Layer für IMAP-Search + Draft-Erstellung — Methoden hängen von existierender API ab)

**Interfaces:**
- Produces:
  - `class MailBackup(mailService, keys) { suspend fun ensureBackup(pin: String): Result<Unit>; suspend fun findBackup(): BackupBlob?; suspend fun restoreFromBackup(blob: BackupBlob, pin: String): Boolean }`

- [ ] **Step 1:** Den bestehenden Mail-Service finden:

```bash
grep -rn "imap\|IMAP\|jakarta.mail\|Store " app/src/main/java/de/transio/hiuni/feature/email/ | head -10
grep -rn "saveDraft\|createDraft\|Folder" app/src/main/java/de/transio/hiuni/feature/email/ | head
```

Die spezifischen Methoden-Signaturen aus dem bestehenden Mail-Layer in Methoden-Stubs notieren:
```
appendToDraftsFolder(subject: String, body: String): Result<MessageId>
searchDraftsBySubject(subject: String): Result<List<DraftMessage>>
deleteDraft(messageId: MessageId): Result<Unit>
```

Falls die Funktionen nicht 1:1 existieren, in dem Mail-Service-File ergänzen — Spec sagt "Mail-Layer hat IMAP-Support", also sind die Bausteine da.

- [ ] **Step 2:** `MailBackup.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.trust

import de.transio.hiuni.events.Backup
import de.transio.hiuni.events.BackupBlob
import de.transio.hiuni.events.toBase64
import de.transio.hiuni.feature.email.MailService    // bestehender Service
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class MailBackup @Inject constructor(
    private val mail: MailService,
    private val keys: MyKeyManager,
) {
    private val subject = "HIUNI-KEY-BACKUP-v1"
    private val json = Json

    suspend fun ensureBackup(pin: String): Result<Unit> = runCatching {
        val kp = keys.getOrNull() ?: error("no key")
        val blob = Backup.encrypt(kp.secretKey, pin, pubkey = kp.publicKey.toBase64())
        val body = """HIUNI-KEY-BACKUP-v1
salt: ${blob.salt}
ciphertext: ${blob.ciphertext}
pubkey: ${blob.pubkey}
ts: ${blob.ts}
"""
        mail.searchDraftsBySubject(subject).getOrNull()?.forEach {
            mail.deleteDraft(it.id)
        }
        mail.appendToDraftsFolder(subject, body).getOrThrow()
    }

    suspend fun findBackup(): BackupBlob? {
        val drafts = mail.searchDraftsBySubject(subject).getOrNull() ?: return null
        val latest = drafts.maxByOrNull { it.receivedAt } ?: return null
        return parseBody(latest.body)
    }

    suspend fun restoreFromBackup(blob: BackupBlob, pin: String): Boolean {
        val secret = Backup.decrypt(blob, pin) ?: return false
        // Pubkey wiederherstellen aus blob, secret als wrapped speichern
        keys.restore(pubkeyB64 = blob.pubkey, secretBytes = secret)
        return true
    }

    private fun parseBody(body: String): BackupBlob? {
        val m = body.lines().mapNotNull {
            val (k, v) = it.split(":", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } ?: return@mapNotNull null
            k to v
        }.toMap()
        return BackupBlob(
            salt = m["salt"] ?: return null,
            ciphertext = m["ciphertext"] ?: return null,
            pubkey = m["pubkey"] ?: return null,
            ts = m["ts"]?.toLongOrNull() ?: return null,
        )
    }
}
```

`MyKeyManager.restore` hinzufügen (PrivKey aus Bytes wrappen, speichern):

```kotlin
suspend fun restore(pubkeyB64: String, secretBytes: ByteArray) {
    dao.upsert(MyKeyEntity(
        pubkey = pubkeyB64,
        secretKeyEncrypted = wrap.wrap(secretBytes),
        createdAt = System.currentTimeMillis(),
    ))
}
```

- [ ] **Step 3:** UI-Hook in Onboarding: nach erfolgreichem `MyKeyManager.create()` (in `LsfOnboarding`) Dialog „Backup-PIN setzen — 6 Ziffern" → `MailBackup.ensureBackup(pin)`.

- [ ] **Step 4:** Health-Check im `StartupRefresher` (existierende Klasse): pro App-Start prüfen ob Backup-Draft existiert, falls Key vorhanden aber Backup fehlt → User-Hinweis.

- [ ] **Step 5:** Commit:

```bash
git add app/
git commit -m "feat(reviews): Mail-Backup via IMAP-Drafts mit PIN-Verschlüsselung"
```

