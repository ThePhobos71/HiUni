### Task 23: AES-GCM-Encrypt mit PBKDF2 in `:shared-events`

**Files:**
- Create: `shared-events/src/main/kotlin/de/transio/hiuni/events/Backup.kt`
- Create: `shared-events/src/test/kotlin/de/transio/hiuni/events/BackupTest.kt`

**Interfaces:**
- Produces:
  - `object Backup { fun encrypt(secretBytes, pin: String, iterations: Int = 600_000): BackupBlob; fun decrypt(blob: BackupBlob, pin: String): ByteArray? }`
  - `data class BackupBlob(salt: String, ciphertext: String, pubkey: String, ts: Long)`

- [ ] **Step 1:** Test:

```kotlin
package de.transio.hiuni.events
import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    @Test fun `encrypt then decrypt with right pin returns original`() {
        val kp = Ed25519.generateKeypair()
        val blob = Backup.encrypt(kp.secretKey, "123456", pubkey = kp.publicKey.toBase64(), iterations = 1000)
        val restored = Backup.decrypt(blob, "123456", iterations = 1000)
        assertArrayEquals(kp.secretKey, restored)
    }
    @Test fun `wrong pin returns null`() {
        val kp = Ed25519.generateKeypair()
        val blob = Backup.encrypt(kp.secretKey, "123456", pubkey = kp.publicKey.toBase64(), iterations = 1000)
        assertNull(Backup.decrypt(blob, "999999", iterations = 1000))
    }
}
```

- [ ] **Step 2:** FAIL.

- [ ] **Step 3:** `Backup.kt`:

```kotlin
package de.transio.hiuni.events

import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

data class BackupBlob(val salt: String, val ciphertext: String, val pubkey: String, val ts: Long)

object Backup {
    fun encrypt(secret: ByteArray, pin: String, pubkey: String, iterations: Int = 600_000): BackupBlob {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(pin, salt, iterations)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(secret)
        return BackupBlob(
            salt = salt.toBase64(),
            ciphertext = (iv + ct).toBase64(),
            pubkey = pubkey,
            ts = System.currentTimeMillis(),
        )
    }
    fun decrypt(blob: BackupBlob, pin: String, iterations: Int = 600_000): ByteArray? = runCatching {
        val salt = blob.salt.fromBase64()
        val all = blob.ciphertext.fromBase64()
        val iv = all.copyOfRange(0, 12); val ct = all.copyOfRange(12, all.size)
        val key = deriveKey(pin, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.doFinal(ct)
    }.getOrNull()

    private fun deriveKey(pin: String, salt: ByteArray, iter: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iter, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
```

- [ ] **Step 4:** PASS.

- [ ] **Step 5:** Commit:

```bash
git add shared-events/
git commit -m "feat(reviews): Backup-Blob mit AES-GCM + PBKDF2"
```

