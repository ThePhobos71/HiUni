# Task 9 Report: MyKeyManager with Android-Keystore-based secret-key wrapping

## Files Changed

| File | Action |
|------|--------|
| `app/src/main/java/.../review/data/MyKeyDao.kt` | Updated — replaced `insert/find/delete(pubkey)/getAll` with `upsert/get/delete()` (singleton semantics) |
| `app/src/main/java/.../review/di/ReviewModule.kt` | Updated — added `@Provides @Singleton fun keystoreWrap(impl: AndroidKeystoreWrap): KeystoreWrap = impl` |
| `app/src/main/java/.../review/trust/MyKeyManager.kt` | Created — `KeystoreWrap` interface + `MyKeyManager` class with `getOrNull`, `create`, `clear`, `restore` |
| `app/src/main/java/.../review/trust/AndroidKeystoreWrap.kt` | Created — AES/GCM/NoPadding, 256-bit hardware key, 12-byte IV prepended, 128-bit tag |
| `app/src/test/java/.../review/trust/MyKeyManagerTest.kt` | Created — 4 unit tests, plain JVM via IdentityWrap + FakeDao |

## TDD Evidence

**RED:** `./gradlew :app:testDebugUnitTest --tests "...MyKeyManagerTest"` → compilation errors (`Unresolved reference: MyKeyManager`, `'wrap' overrides nothing`) — confirmed no production code existed.

**GREEN:** After implementing `MyKeyManager.kt` and `KeystoreWrap` interface → `BUILD SUCCESSFUL`, all 4 tests pass.

## Build Outcome

`./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL** (no warnings on new code).

## Self-Review

### (a) `wrap(b)/unwrap(wrap(b)) == b` roundtrip in AndroidKeystoreWrap

- `wrap` calls `Cipher.getInstance("AES/GCM/NoPadding")`, init `ENCRYPT_MODE`, captures the auto-generated 12-byte IV, runs `doFinal`, returns `iv + ciphertext+tag` (28 + len bytes).
- `unwrap` splits at byte 12, reconstructs `GCMParameterSpec(128, iv)`, inits `DECRYPT_MODE` with the same key alias, `doFinal` strips the 16-byte tag and returns plaintext.
- The same Keystore key is fetched for both operations (`getOrCreateKey` is idempotent: returns existing key if alias is present). Roundtrip is correct on real hardware; Robolectric can't exercise this path, hence the IdentityWrap abstraction.

### (b) `create()` then `getOrNull()` returns the same keypair

- `create()`: generates keypair via Tink `Ed25519.generateKeypair()`, base64-encodes pubkey, calls `wrap.wrap(secretKey)`, upserts entity.
- `getOrNull()`: reads entity, calls `wrap.unwrap(secretKeyEncrypted)`, base64-decodes pubkey, returns `Keypair(pub, secret)`.
- With `IdentityWrap` (identity function), the bytes are stored and retrieved unchanged. Test `create persists key and returns same on second call` asserts `assertArrayEquals` on both `publicKey` and `secretKey` — passes.

## Concerns

1. **`MyKeyDao` is a breaking change**: the old `insert/find/delete(pubkey)/getAll` signatures were replaced. No other production code called the old methods (verified by grep), but if any in-flight branch adds usage of the old names a merge conflict will arise.
2. **AndroidKeystoreWrap is untested by JVM tests**: Robolectric doesn't implement `AndroidKeyStore`, so the production wrap path has no automated coverage. An instrumentation test (Task 9 extension) would be needed to verify the hardware path end-to-end.
3. **Single-key assumption**: `MyKeyDao.get()` uses `LIMIT 1` — if a previous schema version inserted multiple rows (with the old `insert` method), `get()` will silently return an arbitrary one. The old schema had no uniqueness constraint. This is acceptable given the singleton intent but worth noting.
