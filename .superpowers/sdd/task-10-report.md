## Task 10 — ReviewRepository Report

### Files Changed
- **Created:** `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/ReviewRepository.kt`
- **Created:** `app/src/test/java/de/transio/hiuni/feature/mensa/review/data/ReviewRepositoryTest.kt`

### TDD Evidence
1. Wrote `ReviewRepositoryTest.kt` first — build failed with "Unresolved reference: ReviewRepository / aggregateFor / Dimension / RecipeAggregate" (RED confirmed).
2. Implemented `ReviewRepository.kt` with `Dimension`, `DimensionStat`, `RecipeAggregate`.
3. Re-ran: `BUILD SUCCESSFUL` (GREEN).

### Test Results
- `ReviewRepositoryTest` — 5 tests, all pass:
  - `submitReview persists signed event with own pubkey` (count=1, overall=4.0, repeatPct=100)
  - `aggregateFor returns null overall when no rows`
  - `submitReview adds entry to outbox`
  - `retract marks event retracted and adds outbox entry`
  - `mute inserts into muted_pubkeys`
  - `submitReview with dimension ratings populates byDimension`
- Regression sweep `--tests "*Review*"` → `BUILD SUCCESSFUL` (ReviewDaoTest + MyKeyManagerTest + ReviewRepositoryTest all green)

### Self-Review

**(a) Outbox payload contains a parseable signed event:**
The `outbox` test asserts `outboxEntry.payload.contains("hash2")` — the JSON-encoded `ReviewEvent` is stored verbatim via `json.encodeToString(event)`. Since `ReviewEvent` is `@Serializable` in `shared-events`, this round-trips cleanly. Retraction uses `json.encodeToString(ev)` on `RetractionEvent` which is also `@Serializable`.

**(b) `aggregateFor` returns null overall when n=0:**
The `if (rows.isEmpty())` branch returns `RecipeAggregate(recipeHash, null, 0, null, emptyMap())` — explicitly tested by `aggregateFor returns null overall when no rows`.

### Concerns
- `OutboxEntity` constructor requires `lastAttempt: Long?` with no default — the brief template omitted it, causing a one-field addition over the spec template. Corrected to `lastAttempt = null`.
- `TrustEntity` in the brief test used positional args; actual entity requires named args (depth, source fields are non-nullable). Both test and implementation use named args throughout.
- No Hilt injection test coverage — the `ReviewModule` wiring will be exercised in integration/e2e testing; isolated unit test uses direct construction which is sufficient for TDD purposes.

### Commit
`6c1c294` — `feat(reviews): ReviewRepository mit Aggregation + Submit + Retract`

---

## Fix-up: Rounding + Sig-Assertion (2026-06-28)

### Findings addressed
1. **Important** — `wouldOrderAgainPct` truncated via `.toInt()` (66 instead of 67 for 2/3 trues). Switched to `kotlin.math.roundToInt()` in `ReviewRepository.kt:42` (import already present at line 13).
2. **Minor** — Two `assertNotNull(event.sig)` calls on non-nullable `String`. **Already tightened** in the file: `ReviewRepositoryTest.kt:58` and `:91` use `assertTrue(event.sig.isNotEmpty())`. No further edit required — the previously cited line numbers (213/246) do not exist; file is 188 lines.

### New test
Added `wouldOrderAgainPct rounds 2 of 3 trues to 67 not 66` — generates 3 independent `Ed25519` keypairs, inserts each as `TrustEntity(depth=0)`, then inserts 3 `ReviewEventEntity` rows directly via `db.reviewDao().insert(...)` (votes: true, true, false). Asserts `agg.wouldOrderAgainPct == 67`.

### Test command
```
./gradlew :app:testDebugUnitTest --tests "*ReviewRepositoryTest*"
```

### Output (excerpt from build/test-results XML)
```
<testsuite name="...ReviewRepositoryTest" tests="7" skipped="0" failures="0" errors="0" time="1.47">
  <testcase name="wouldOrderAgainPct rounds 2 of 3 trues to 67 not 66" time="0.013"/>
  <testcase name="submitReview persists signed event with own pubkey" time="0.05"/>
  <testcase name="aggregateFor returns null overall when no rows" time="0.008"/>
  <testcase name="submitReview adds entry to outbox" time="0.01"/>
  <testcase name="retract marks event retracted and adds outbox entry" time="0.011"/>
  <testcase name="mute inserts into muted_pubkeys" time="1.368"/>
  <testcase name="submitReview with dimension ratings populates byDimension" time="0.01"/>
</testsuite>
BUILD SUCCESSFUL in 6s
```

### Confirmation
- Sig assertions: lines 58 and 91 already use `assertTrue(event.sig.isNotEmpty())` (no `assertNotNull(event.sig)` remains in the file — verified via `grep -rn "assertNotNull.*\.sig" app/src/test/` → no matches).

### Concerns
- The Minor finding referenced line numbers (213/246) that do not exist in the file (only 188 lines total) — the assertion tightening had already been applied in an earlier pass. Confirmed via grep that no `assertNotNull(event.sig)` remains anywhere in the test tree.
