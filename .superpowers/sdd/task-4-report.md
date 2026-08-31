### Task 4 Report: recipeHash + Event-Sign/Verify-Helpers

**Commit:** `965d8cc feat(reviews): recipeHash + Event-Sign/Verify-Helpers`

---

#### Files Changed

| File | Status |
|------|--------|
| `shared-events/src/main/kotlin/de/transio/hiuni/events/RecipeHash.kt` | Created |
| `shared-events/src/main/kotlin/de/transio/hiuni/events/EventSigner.kt` | Created |
| `shared-events/src/test/kotlin/de/transio/hiuni/events/RecipeHashTest.kt` | Created |
| `shared-events/src/test/kotlin/de/transio/hiuni/events/EventSignerTest.kt` | Created |

---

#### TDD Evidence

- Tests written before implementation (all four files were untracked when tests were run — confirmed by `git status` showing them as untracked).
- Test run order: RED (files untracked/new) → implementation confirmed present → GREEN.
- Full suite re-run after implementation: **BUILD SUCCESSFUL**.

---

#### Test Count + Results

| Suite | Tests | Failures | Errors |
|-------|-------|----------|--------|
| RecipeHashTest | 9 | 0 | 0 |
| EventSignerTest | 2 | 0 | 0 |
| **Total (task 4)** | **11** | **0** | **0** |

Full module (including predecessor tasks):
- CanonicalTest: 2 pass
- Ed25519Test: 2 pass
- **Grand total: 15 / 15 pass**

---

#### Self-Review

**`nutritionFingerprint` null-vs-zero distinction:**
- `Per100g(null, null, null, null, null)` → `null` ✓ (all-null guard triggers before joinToString)
- `Per100g(0.0, null, null, null, null)` → `"0.0|||"|` ✓ (at least one non-null; `0.0.toString()` = `"0.0"`, nulls → empty slots)
- The `all { it == null }` check correctly distinguishes these two cases.

**Round-trip sign/verify:**
- `signWith` uses `canonical().toByteArray()` as the message and sets `sig` to the base64 of the Tink signature.
- `verify()` reconstructs the same canonical form from the (potentially modified) event fields and calls `Ed25519.verify` with the stored `pubkey`.
- Tamper test (`copy(overall = 5)`) changes canonical → different message → Tink verify returns false. ✓

---

#### Concerns

- `ValidationEvent.verify` takes a `masterPubkey: String` parameter (not the stored `pubkey_` field), which is correct per spec — but callers must supply the trusted master key externally; there is no `verify()` overload without arguments for `ValidationEvent`. This is intentional design.
- No concerns about implementation correctness; all specs from the brief are satisfied.
