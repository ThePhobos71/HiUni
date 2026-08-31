### Task 3: Ed25519-Sign/Verify mit Tink

**Files:**
- Create: `shared-events/src/main/kotlin/de/transio/hiuni/events/Ed25519.kt`
- Create: `shared-events/src/test/kotlin/de/transio/hiuni/events/Ed25519Test.kt`

**Interfaces:**
- Produces:
  - `object Ed25519 { fun generateKeypair(): Keypair; fun sign(msg: ByteArray, sk: ByteArray): ByteArray; fun verify(msg: ByteArray, sig: ByteArray, pk: ByteArray): Boolean }`
  - `data class Keypair(val publicKey: ByteArray, val secretKey: ByteArray)`

- [ ] **Step 1: Failing Test:**

```kotlin
// Ed25519Test.kt
package de.transio.hiuni.events

import com.google.crypto.tink.signature.SignatureConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class Ed25519Test {
    companion object {
        @BeforeClass @JvmStatic
        fun init() { SignatureConfig.register() }
    }

    @Test
    fun `roundtrip sign and verify`() {
        val kp = Ed25519.generateKeypair()
        val msg = "hello world".toByteArray()
        val sig = Ed25519.sign(msg, kp.secretKey)
        assertTrue(Ed25519.verify(msg, sig, kp.publicKey))
    }

    @Test
    fun `verify rejects tampered message`() {
        val kp = Ed25519.generateKeypair()
        val sig = Ed25519.sign("hello".toByteArray(), kp.secretKey)
        assertFalse(Ed25519.verify("hellp".toByteArray(), sig, kp.publicKey))
    }
}
```

- [ ] **Step 2:** `./gradlew :shared-events:test` → Expected FAIL.

- [ ] **Step 3:** `Ed25519.kt` implementieren:

```kotlin
package de.transio.hiuni.events

import com.google.crypto.tink.*
import com.google.crypto.tink.signature.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

data class Keypair(val publicKey: ByteArray, val secretKey: ByteArray) {
    override fun equals(other: Any?): Boolean = other is Keypair &&
        publicKey.contentEquals(other.publicKey) && secretKey.contentEquals(other.secretKey)
    override fun hashCode(): Int = publicKey.contentHashCode() * 31 + secretKey.contentHashCode()
}

object Ed25519 {
    init { SignatureConfig.register() }

    fun generateKeypair(): Keypair {
        val handle = KeysetHandle.generateNew(KeyTemplates.get("ED25519"))
        val secretOut = ByteArrayOutputStream()
        CleartextKeysetHandle.write(handle, BinaryKeysetWriter.withOutputStream(secretOut))
        val publicHandle = handle.publicKeysetHandle
        val publicOut = ByteArrayOutputStream()
        CleartextKeysetHandle.write(publicHandle, BinaryKeysetWriter.withOutputStream(publicOut))
        return Keypair(publicKey = publicOut.toByteArray(), secretKey = secretOut.toByteArray())
    }

    fun sign(msg: ByteArray, sk: ByteArray): ByteArray {
        val handle = CleartextKeysetHandle.read(BinaryKeysetReader.withInputStream(ByteArrayInputStream(sk)))
        return handle.getPrimitive(PublicKeySign::class.java).sign(msg)
    }

    fun verify(msg: ByteArray, sig: ByteArray, pk: ByteArray): Boolean = runCatching {
        val handle = CleartextKeysetHandle.read(BinaryKeysetReader.withInputStream(ByteArrayInputStream(pk)))
        handle.getPrimitive(PublicKeyVerify::class.java).verify(sig, msg)
        true
    }.getOrDefault(false)
}
```

- [ ] **Step 4:** Tests laufen, Expected PASS.

- [ ] **Step 5:** Commit:

```bash
git add shared-events/
git commit -m "feat(reviews): Ed25519 Sign/Verify via Tink"
```

