# Task 31 Report — IntroIssuer + Trust-Count

## Files modified / created

- Modified: `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/TrustDao.kt`
  — added `suspend fun countIntrosBy(pubkey: String): Int` (`SELECT COUNT(*) FROM trust WHERE source = :pubkey`).
- Created: `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/IntroIssuer.kt`
  — `@Singleton` class, `issueIntro(invitee): Result<IntroEvent>` with all 4 preconditions + outbox + optimistic trust update.
- Created: `app/src/test/java/de/transio/hiuni/feature/mensa/review/trust/IntroIssuerTest.kt`
  — 5 Robolectric tests against the real Room DAOs (in-memory AppDatabase) + IdentityWrap.

No Hilt module change required — Hilt resolves `MyKeyManager`, `TrustDao`, `OutboxDao` via existing bindings and `Json` is already provided by `NetworkModule.provideJson()`.

## TDD evidence

### RED — test file added before implementation

```
e: IntroIssuerTest.kt:35:34 Unresolved reference 'IntroIssuer'.
e: IntroIssuerTest.kt:53:18 Unresolved reference 'IntroIssuer'.
e: IntroIssuerTest.kt:69:29 Unresolved reference 'issueIntro'.
...
BUILD FAILED in 12s
```

### GREEN — after implementing `IntroIssuer.kt`

```
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 7s
```

Test report XML (`build/test-results/testDebugUnitTest/...IntroIssuerTest.xml`):

```
tests="5" skipped="0" failures="0" errors="0"
- happy path issues signed IntroEvent and updates outbox + trust    PASS
- fails when no own keypair                                         PASS
- fails when own depth is too deep to invite                        PASS
- fails when 5-invite lifetime limit is exhausted                   PASS
- fails when not yet validated (own pubkey not in trust)            PASS
```

`./gradlew :app:assembleDebug` also green.

## Test outcomes

5/5 pass. Coverage:

- happy path (depth 0 inviter → invitee at depth 1, sig non-empty, `ev.verify() == true`, outbox row exists w/ sig in payload, local trust row written optimistically)
- no-key (keys.getOrNull() == null)
- depth too deep (own depth = 2 → fail; outbox + trust untouched)
- 5-limit exhausted (5 prior trust rows w/ source = me → fail; new invitee not added)
- not yet validated (key exists but no self-trust row → fail)

## Self-review

**(a) issueIntro fails loudly at each precondition.** Yes — every precondition raises an `error(...)` (caught by `runCatching` → `Result.failure`) with a distinct, grep-able message: `"no key — onboarding required"`, `"not yet validated — cannot invite"`, `"depth too deep to invite"`, `"invite limit reached (5)"`. Self-invite is additionally guarded with `require(invitee != pub)`.

**(b) outbox payload contains a signed event.** Yes — `IntroEvent(..., sig = "").signWith(kp)` is called *before* serialization, so `json.encodeToString(ev)` embeds the populated `sig`. Test `happy path` asserts `outRow.payload.contains(ev.sig)` and `ev.sig.isEmpty()` is false; `ev.verify()` also passes against the inviter's own pubkey (`pubkey = inviter` on IntroEvent).

**(c) local trust table is optimistically updated.** Yes — `trustDao.insert(TrustEntity(invitee, source = me, depth = mine.depth + 1, ts, sig))` happens synchronously in the same suspend call, *before* `Result.success` returns. No relay roundtrip is required for the inviter to see the invitee as trusted. Test `happy path` confirms `trustDao.find(invitee)` returns a row with `depth = 1` and `source = pub` immediately.

## Concerns

1. **Optimistic insert can drift from the relay-side view.** If the IntroEvent never reaches the relay (e.g. outbox flusher gives up) the inviter still locally treats the invitee as trusted forever. A future "outbox-dead-letter → rollback trust" reconciliation may be desirable, but is out of scope for Task 31.
2. **`require(invitee != pub)` throws `IllegalArgumentException` instead of becoming a `Result.failure`.** `runCatching` *does* catch it, so the contract is preserved (Result.failure on self-invite), but I did not add a dedicated test for self-invite since the brief explicitly said "assert or just rely on UI". Easy to add later.
3. **The 5-invite limit is enforced by counting `trust.source = me` rows.** That includes optimistically-inserted rows from this very class. If a `delete(pubkey)` ever removes an invitee from trust (e.g. retraction), the slot is freed — which is probably desirable, but worth noting that the limit is "currently active introductions" rather than a strict lifetime counter. The brief's wording ("5-Invites lifetime limit, counted locally via `trust.source = me` count") matches this implementation.
4. **No IntroEvent-ingest path on the receiving side yet.** That belongs to a later task (probably Task 32+) — the relay flusher already broadcasts arbitrary outbox payloads; the receiver-side `EventValidator`/trust-mutator for incoming intros is a separate concern.
