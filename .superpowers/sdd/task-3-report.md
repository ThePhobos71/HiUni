# Task 3 Report: Ed25519 Sign/Verify via Tink

## Files Changed
- `shared-events/src/main/kotlin/de/transio/hiuni/events/Ed25519.kt` — new: `Keypair` data class + `Ed25519` object
- `shared-events/src/test/kotlin/de/transio/hiuni/events/Ed25519Test.kt` — new: 2 tests

## TDD Evidence

### RED
```
./gradlew :shared-events:test --tests "de.transio.hiuni.events.Ed25519Test"
> compileTestKotlin FAILED
  e: Unresolved reference 'Ed25519'. (6 errors)
BUILD FAILED in 723ms
```

### GREEN (after implementing Ed25519.kt)
```
./gradlew :shared-events:test --tests "de.transio.hiuni.events.Ed25519Test"
BUILD SUCCESSFUL in 1s  — 4 tasks executed
```

Full module (incl. CanonicalTest):
```
./gradlew :shared-events:test
BUILD SUCCESSFUL in 730ms — no regressions
```

## Self-Review
- `verify()` uses `runCatching { ... }.getOrDefault(false)` — tampered-message test passes without throwing.
- `Keypair` overrides `equals`/`hashCode` correctly via `contentEquals`/`contentHashCode`.
- `SignatureConfig.register()` called both in `Ed25519.init {}` and in `@BeforeClass` (safe, idempotent).
- Keys are full Tink keysets (opaque blobs), not raw 32-byte curve points — consistent with brief note about Task 19 compatibility.

## Concerns
None. Implementation matches brief exactly.
