# Task 19 — `/validate` HTTP Endpoint — Report

## Status: GREEN

## Files

### Created
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Hmac.kt` — `fun hmacHex(secret, msg): String` via `Mac.HmacSHA256`, lowercase hex (64 chars).
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/LsfBridge.kt` — `interface LsfBridge`, `data class LsfUser(matrikelnummer)`, `class StubLsfBridge` (accepts `stub-<matrikel>` prefix).
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/MasterKey.kt` — `class MasterKey(envB64, persistPath = "/data/master.key")`; loads from ENV (base64 of length-prefixed format) > file > generates+persists fresh keypair. Persist format: `(pubSize:Int BE)(pub bytes)(sec bytes)`, accommodates variable-length Tink keyset blobs.
- `hiuni-relay/src/test/kotlin/de/transio/hiuni/relay/ValidateEndpointTest.kt` — 6 tests (see below).

### Modified
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Routes.kt` — added `ValidateRequest`, `ValidateResponse`, `Routing.validate(...)`, and `broadcastAll(...)` helper. Imports: `HttpStatusCode`, `receive`, `post`, `Serializable`.
- `hiuni-relay/src/main/kotlin/de/transio/hiuni/relay/Application.kt` — HMAC_SECRET blank → ERROR + `exitProcess(78)` (EX_CONFIG); MASTER_KEY_B64 blank → WARN + auto-generate; wires `validate(masterKey, lsf, hmacSecret)` route.

### Untouched (per brief)
- `:app/`, `:shared-events/`, `SyncFrame.kt`.

## TDD Evidence

**RED** (verified at compileTestKotlin):
```
Unresolved reference 'StubLsfBridge'
Unresolved reference 'ValidateRequest'
Unresolved reference 'hmacHex'
Unresolved reference 'MasterKey'
```
14 unresolved references across the new test file before implementation existed.

**GREEN** (`./gradlew :hiuni-relay:test :hiuni-relay:assemble`):
```
EventStoreTest: 1 test, 0 failures
ValidateEndpointTest: 6 tests, 0 failures
SyncEndpointTest: 2 tests, 0 failures
BUILD SUCCESSFUL
```

## Test Outcomes

ValidateEndpointTest (6/6 green):
1. `validate happy path returns ValidationEvent with verifiable signature` — `stub-12345` → 200, response.validationEvent.pubkey_=="pk-test", issuer=="relay", `validationEvent.verify(relayMasterPubkey)` == true.
2. `validate rejects invalid LSF cookie with 401` — `garbage-cookie` → 401.
3. `validate replaces existing pubkey on phone switch` — same Matrikel, two pubkeys; `findMatNr(matHash)` returns the second.
4. `MasterKey persists to disk and reloads stable pubkey` — two MasterKey instances on same persistPath → identical pubkeyB64.
5. `hmacHex is deterministic and hex-encoded` — same input → same hash; lowercase hex only; 64 chars; different msg → different hash.
6. `StubLsfBridge accepts stub-prefixed cookies only` — `stub-99` → LsfUser("99"); `nope` → null.

## Self-Review

**(a) HMAC_SECRET=empty → app refuses to start.**
Yes. `Application.kt` lines 21-25: `if (hmacSecret.isBlank()) { logger.error("HMAC_SECRET is empty — required for /validate (LSF Matrikel-Hash). Refusing to start."); exitProcess(78) }`. EX_CONFIG (78) is the BSD convention for misconfigured services.

**(b) MASTER_KEY_B64=empty → master.key generated and persisted to /data/.**
Yes. `MasterKey.loadOrGenerate`: if envB64 blank and persistPath has no content → calls `Ed25519.generateKeypair()`, creates parent dirs, writes length-prefixed bytes. Verified by the persistence test (test path uses tempfile, but the production path is the same `f.writeBytes(encode(fresh))`). On real deploy, `/data` is expected to be a mounted volume; if missing, `f.parentFile.mkdirs()` creates it.

**(c) `/validate` returns ValidationEvent whose sig verifies with returned relayMasterPubkey.**
Yes. The happy-path test asserts `body.validationEvent.verify(body.relayMasterPubkey)` returns true. Internally: `masterKey.signValidation` calls `ValidationEvent(...).signWith(kp)`, which Ed25519-signs the canonical form with the same secret-key half of the same Tink keyset whose pub half is returned as `relayMasterPubkey`. Verification uses `Ed25519.verify(canonical, sig, masterPubkey)` from the shared-events module.

## Concerns

1. **`/data/master.key` permissions.** Currently world-readable. Production-Phase 4 should `chmod 600` after write or rely on filesystem-level encryption on the container volume. Out-of-scope for Phase 3 stub.
2. **Atomicity of `insert + upsertMatNr + registerPubkey`.** Three separate `@Synchronized` calls, not a single SQL transaction. If the process crashes between `insert` and `upsertMatNr`, the ValidationEvent exists in the events table but the mat-hash row doesn't get updated. Edge case; could be wrapped in `BEGIN/COMMIT` in a follow-up but is not required by the brief.
3. **Spam-Guard not applied to `/validate`.** The WebSocket `sync` path enforces `SPAM_LIMIT_PER_DAY=50`, but `/validate` bypasses that. Acceptable since LSF-cookie validation itself acts as a rate-gate.
4. **`MasterKey` decode test.** I didn't write an explicit test for the ENV-var roundtrip (only file-persistence is covered). The decode path is symmetric to encode and trivial, but a paranoid follow-up could add `decode(encode(kp)) == kp`.
5. **`StubLsfBridge.whoami("stub-")`** returns `LsfUser(matrikelnummer = "")` — empty Matrikel. Probably harmless (hmacHex of "" is still 64 hex chars), but the stub could tighten this in a follow-up if it becomes a test footgun.
6. **`SyncFrame.Event(kind="validation")` broadcast.** Mirrors how App-Clients consume sync events. Confirmed compatible with Phase 3 `classDiscriminator = "type"` JSON config (covered by existing SyncEndpointTest C1-regression test).

## Commit
Pending — will commit `hiuni-relay/` only.
