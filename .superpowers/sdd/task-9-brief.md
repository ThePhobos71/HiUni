### Task 9: KeyStore-gestütztes `MyKeyManager`

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/MyKeyManager.kt`
- Create: `app/src/test/java/de/transio/hiuni/feature/mensa/review/trust/MyKeyManagerTest.kt`

**Interfaces:**
- Produces:
  - `class MyKeyManager(myKeyDao: MyKeyDao, keystoreWrap: KeystoreWrap) { suspend fun getOrNull(): Keypair?; suspend fun create(): Keypair; suspend fun clear() }`
  - `interface KeystoreWrap { fun wrap(secret: ByteArray): ByteArray; fun unwrap(wrapped: ByteArray): ByteArray }` (zwei Impls: `AndroidKeystoreWrap` für Prod, `IdentityKeystoreWrap` für Tests)

- [ ] **Step 1: Test:**

```kotlin
package de.transio.hiuni.feature.mensa.review.trust

import de.transio.hiuni.feature.mensa.review.data.MyKeyDao
import de.transio.hiuni.feature.mensa.review.data.MyKeyEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MyKeyManagerTest {
    private class FakeDao : MyKeyDao {
        var entity: MyKeyEntity? = null
        override suspend fun upsert(e: MyKeyEntity) { entity = e }
        override suspend fun get(): MyKeyEntity? = entity
        override suspend fun delete() { entity = null }
    }
    private class IdentityWrap : KeystoreWrap {
        override fun wrap(s: ByteArray) = s
        override fun unwrap(s: ByteArray) = s
    }

    @Test fun `create persists key and returns same on second call`() = runBlocking {
        val mgr = MyKeyManager(FakeDao(), IdentityWrap())
        val first = mgr.create()
        val second = mgr.getOrNull()
        assertNotNull(second)
        assertArrayEquals(first.publicKey, second!!.publicKey)
    }

    @Test fun `getOrNull returns null when nothing stored`() = runBlocking {
        val mgr = MyKeyManager(FakeDao(), IdentityWrap())
        assertNull(mgr.getOrNull())
    }
}
```

- [ ] **Step 2:** FAIL.

- [ ] **Step 3:** `MyKeyManager.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.trust

import de.transio.hiuni.events.Ed25519
import de.transio.hiuni.events.Keypair
import de.transio.hiuni.feature.mensa.review.data.MyKeyDao
import de.transio.hiuni.feature.mensa.review.data.MyKeyEntity
import javax.inject.Inject

interface KeystoreWrap {
    fun wrap(secret: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
}

class MyKeyManager @Inject constructor(
    private val dao: MyKeyDao,
    private val wrap: KeystoreWrap,
) {
    suspend fun getOrNull(): Keypair? {
        val e = dao.get() ?: return null
        val secret = wrap.unwrap(e.secretKeyEncrypted)
        // Pubkey-recreation: storing pubkey unencrypted via e.pubkey
        return Keypair(publicKey = java.util.Base64.getDecoder().decode(e.pubkey), secretKey = secret)
    }

    suspend fun create(): Keypair {
        val kp = Ed25519.generateKeypair()
        dao.upsert(MyKeyEntity(
            pubkey = java.util.Base64.getEncoder().encodeToString(kp.publicKey),
            secretKeyEncrypted = wrap.wrap(kp.secretKey),
            createdAt = System.currentTimeMillis(),
        ))
        return kp
    }

    suspend fun clear() = dao.delete()
}
```

`MyKeyDao` muss `upsert`, `get` (singleton, da nur 1 Eintrag), `delete` definieren. Pubkey im Entity ist `@PrimaryKey`, beim Upsert mit `OnConflict.REPLACE`.

- [ ] **Step 4:** `AndroidKeystoreWrap` für Prod-Hilt:

```kotlin
// app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/AndroidKeystoreWrap.kt
package de.transio.hiuni.feature.mensa.review.trust

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

class AndroidKeystoreWrap @Inject constructor() : KeystoreWrap {
    private val alias = "hiuni-review-key"
    private val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keystore.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return gen.generateKey()
    }

    override fun wrap(secret: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(secret)
        return iv + ct
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val iv = wrapped.copyOfRange(0, 12)
        val ct = wrapped.copyOfRange(12, wrapped.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }
}
```

Im `ReviewModule`:

```kotlin
@Provides @Singleton fun keystoreWrap(impl: AndroidKeystoreWrap): KeystoreWrap = impl
```

- [ ] **Step 5:** Test PASS, Commit:

```bash
git add app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/ app/src/test/java/de/transio/hiuni/feature/mensa/review/trust/
git commit -m "feat(reviews): MyKeyManager mit Android-Keystore-Wrapping"
```

