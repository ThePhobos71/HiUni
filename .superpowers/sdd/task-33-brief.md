### Task 33: Mail-Intro (Fallback)

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/MailIntro.kt`

**Interfaces:**
- Produces:
  - `class MailIntroSender(mail, issuer) { suspend fun sendIntro(toAddress, inviteePubkey): Result<Unit> }`
  - Mail-Subject-Parser für eingehende `HIUNI-INTRO-v1`-Mails (Hook in bestehenden Mail-Layer)

- [ ] **Step 1:** Sender:

```kotlin
class MailIntroSender @Inject constructor(
    private val mail: MailService,
    private val issuer: IntroIssuer,
    private val json: Json,
) {
    suspend fun sendIntro(toAddress: String, inviteePubkey: String): Result<Unit> = runCatching {
        val ev = issuer.issueIntro(inviteePubkey).getOrThrow()
        val body = """HIUNI-INTRO-v1
invitee: ${ev.invitee}
inviter: ${ev.inviter}
ts: ${ev.ts}
sig: ${ev.sig}
"""
        mail.send(to = toAddress, subject = "HIUNI-INTRO-v1", body = body).getOrThrow()
    }
}
```

- [ ] **Step 2:** Parser für eingehende Mails (Hook im bestehenden Mail-Layer, der Subject-Filter `HIUNI-INTRO-v1` durch eine neue Klasse `MailIntroReceiver` reicht):

```kotlin
class MailIntroReceiver @Inject constructor(
    private val trustDao: TrustDao,
    private val validatorFactory: ValidatorFactory,
) {
    fun parse(body: String): IntroEvent? {
        val m = body.lines().mapNotNull {
            val (k, v) = it.split(":", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } ?: return@mapNotNull null
            k to v
        }.toMap()
        return runCatching {
            IntroEvent(invitee = m["invitee"]!!, inviter = m["inviter"]!!,
                ts = m["ts"]!!.toLong(), sig = m["sig"]!!)
        }.getOrNull()
    }
    suspend fun import(ev: IntroEvent) {
        if (validatorFactory.create().accept(ev) is AcceptResult.Ok) {
            val parentDepth = trustDao.find(ev.inviter)?.depth ?: return
            if (parentDepth + 1 <= 2) trustDao.insert(TrustEntity(
                ev.invitee, ev.inviter, parentDepth + 1, ev.ts, ev.sig))
        }
    }
}
```

- [ ] **Step 3:** Mail-Scan im `StartupRefresher`: prüft alle 24h IMAP-Folder nach `HIUNI-INTRO-v1`-Subjects, importiert gefundene IntroEvents.

- [ ] **Step 4:** Commit:

```bash
git add app/
git commit -m "feat(reviews): Mail-Intro (Sender + Receiver + Mail-Scan-Hook)"
```

