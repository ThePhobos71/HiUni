# Task 5 Report: EventValidator

## Files Changed
- Created: `shared-events/src/main/kotlin/de/transio/hiuni/events/EventValidator.kt`
- Created: `shared-events/src/test/kotlin/de/transio/hiuni/events/EventValidatorTest.kt`

## TDD Evidence
1. **RED**: Wrote `EventValidatorTest.kt` first. Build failed with 14 `Unresolved reference` errors (EventValidator, AcceptResult, EventStore, TrustResolver all missing).
2. **GREEN**: Wrote `EventValidator.kt` with all interfaces and sealed class. `./gradlew :shared-events:test` → BUILD SUCCESSFUL.

## Test Count & Results
`EventValidatorTest`: **8 tests, 0 failures, 0 skipped**
- `accepts valid signed review` — PASS
- `rejects bad signature` — PASS
- `rejects unknown pubkey (no trust)` — PASS
- `rejects depth greater than 2` — PASS
- `rejects too old` — PASS
- `rejects future ts` — PASS
- `rejects dedupe` — PASS
- `rejects spam (more than 50 in day)` — PASS

Full suite (all prior tests still green): CanonicalTest 2, Ed25519Test 2, EventSignerTest 2, RecipeHashTest 9.

## Self-Review

### ValidationEvent trust-check skip
Correct. The `when` branch calls `e.verify(masterPubkey)` for `ValidationEvent`, using the master key rather than the event's `pubkey` field. The subsequent `if (e !is ValidationEvent)` guard fully skips the `trust.depthOf()` check — a ValidationEvent is authorised by the master alone, independent of the WoT graph.

### Spam threshold at exactly 50
Correct. The condition is `>= spamLimit` where `spamLimit = 50`. The test double returns `countSince(...) = 50`, which triggers the rejection. A count of 49 would pass.

### tooOld edge case
`tooOld = now - 91L * 24 * 3600_000` = 91 days ago. The past window is `maxPastSkewMs = 90L * 24 * 3600_000`. So `tooOld < now - maxPastSkewMs` → reject. One day past the 90-day cutoff, correctly rejected.

### future edge case
`future = now + 10 * 60_000` = +10 min. The future window is `maxFutureSkewMs = 5 * 60_000`. So `future > now + maxFutureSkewMs` → reject. Twice the 5-min cutoff, correctly rejected.

## Concerns
None. Implementation is straightforward policy code; all 8 spec tests pass. No mock frameworks used — pure anonymous Kotlin object impls as per brief.

## Commit
`9a8f744` feat(reviews): EventValidator mit Signatur-/Trust-/Spam-Regeln
