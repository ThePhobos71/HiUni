# Task 23 Report: AES-GCM + PBKDF2 Backup-Encryption

## Status
DONE — committed as `c3652bb feat(reviews): Backup-Blob mit AES-GCM + PBKDF2` on `feature/mensa-reviews`.

## Files created
- `/Users/kjell/AndroidStudioProjects/UniHi/shared-events/src/main/kotlin/de/transio/hiuni/events/Backup.kt`
- `/Users/kjell/AndroidStudioProjects/UniHi/shared-events/src/test/kotlin/de/transio/hiuni/events/BackupTest.kt`

## TDD evidence

### RED (test written first, no impl)
```
> Task :shared-events:compileTestKotlin FAILED
e: BackupTest.kt:11:20 Unresolved reference 'Backup'.
e: BackupTest.kt:12:24 Unresolved reference 'Backup'.
e: BackupTest.kt:18:20 Unresolved reference 'Backup'.
e: BackupTest.kt:19:20 Unresolved reference 'Backup'.
BUILD FAILED in 689ms
```

### GREEN (after Backup.kt)
```
> Task :shared-events:test
BUILD SUCCESSFUL in 1s
```

Full `:shared-events:test` suite also still BUILD SUCCESSFUL — no regressions on Ed25519/EventSigner/EventValidator/Canonical/RecipeHash/Serialization tests.

## Self-review

### (a) Wrong PIN returns null, does NOT throw
`Backup.decrypt(...)` wraps the whole pipeline in `runCatching { ... }.getOrNull()`. AES-GCM's auth-tag verification fails with `AEADBadTagException` on wrong PIN (because the PBKDF2-derived key differs) — `runCatching` catches it, `getOrNull()` returns `null`. Confirmed by test `wrong pin returns null` (assertNull, not assertThrows).

### (b) Salt + IV random per call
Each `encrypt(...)` invocation:
- generates a fresh 16-byte salt via `SecureRandom().nextBytes(...)`
- generates a fresh 12-byte IV via `SecureRandom().nextBytes(...)`

Same `(secret, pin, pubkey)` input therefore produces a different `BackupBlob` every call: different `salt`, different IV (and thus different `ciphertext`), and a newer `ts`. Only `pubkey` would repeat. AES-GCM safety property (never reuse IV with same key) is upheld because both salt (→ different key) and IV change.

### (c) Iterations: tests 1000, prod default 600.000
- `Backup.encrypt(..., iterations: Int = 600_000)` — production default per spec.
- `Backup.decrypt(..., iterations: Int = 600_000)` — symmetric default.
- Both tests pass `iterations = 1000` explicitly to keep test runtime ~1s instead of ~10s.

Caller responsibility: encrypt + decrypt must use the same iteration count. (Wire format does not embed iterations; this is fine for the Phase-5 Mail-Backup use case where iteration count is a build-time constant.)

## Concerns

1. **Iterations not in wire format.** `BackupBlob` carries `salt`, `ciphertext`, `pubkey`, `ts` but not the iteration count. If we ever bump 600k → 1M (or vice versa) and need to decrypt old blobs, we cannot. Acceptable for v1 (Phase-5 will pin a constant) but worth flagging: future migration would require either embedding the count or maintaining a list of fallbacks.

2. **PIN entropy.** 6-digit numeric PIN = ~20 bits entropy. PBKDF2-600k is the only barrier; a determined attacker with the IMAP draft can offline-brute-force in hours-to-days range on modern GPU. This is a design limit of the user-friendly PIN scheme, not a code defect — but worth keeping in mind for the Phase-5 UX (rate-limit decrypt attempts on device, prefer alphanumeric, or layer a server-side challenge later).

3. **`require(all.size > IV_BYTES)` inside `runCatching`.** A malformed `ciphertext` shorter than 12 bytes returns `null` (silent) rather than throwing. Matches the "never throw" contract but means callers can't distinguish "wrong PIN" from "corrupted blob". Acceptable per spec; if needed later, add a `decryptOrError` sibling that throws.

4. **No `@Suppress("ArrayInDataClass")`-style equals/hashCode on BackupBlob.** Fields are all `String`/`Long`, so Kotlin's generated equals works correctly — unlike `Keypair` which had to override because of ByteArray. No action needed; just noting we sidestepped that trap by storing base64.

5. Working tree contains many unrelated modifications from earlier tasks (Learnweb, Calendar, GlobalSearch, etc.) — left untouched; only the two new Backup files were staged for this commit.
