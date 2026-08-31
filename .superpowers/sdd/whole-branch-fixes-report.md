# Whole-Branch Trivial-Fixes Report

Branch: `feature/mensa-reviews`
Base HEAD: `8b7a1b5`
New commit: `23ba536`

## Files modified

1. `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/MyKeyManager.kt`
   - Added `@Singleton` class annotation (`javax.inject.Singleton` was already imported).
2. `shared-events/src/main/kotlin/de/transio/hiuni/events/Events.kt`
   - Added `import kotlinx.serialization.SerialName`.
   - Annotated `ValidationEvent.pubkey_` with `@SerialName("pubkey")`.
   - Added `TODO(phase3-wire)` block above `sealed interface SignedEvent` (type-field finding).
3. `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/NutritionParser.kt`
   - Removed `?: numericField(per100, "caloric_value")` fallback; `caloricValue = extractKcal(...)` only (fail-closed).
4. `shared-events/src/main/kotlin/de/transio/hiuni/events/Ed25519.kt`
   - Added `TODO(phase3-wire)` block at the top of `generateKeypair()` (Tink-keyset wire-format).
5. `app/src/main/java/de/transio/hiuni/feature/mensa/review/data/OutboxEntity.kt`
   - Added `TODO(phase4-relay)` block above `@Entity` (type column).
6. `shared-events/src/main/kotlin/de/transio/hiuni/events/EventValidator.kt`
   - Added `TODO(phase4-relay)` block above the `countSince` call (spam-limit scope).

(Spec listed 4 fixes; #4 splits into 4 TODOs across 4 files → 6 files total.)

## Build + test results

- `./gradlew :shared-events:test :app:testDebugUnitTest --tests "*review*"`: BUILD SUCCESSFUL in 13s, 0 failures across 10 test classes (37 tests: CanonicalTest×2, EventValidatorTest×8, Ed25519Test×2, EventSignerTest×2, RecipeHashTest×9, MensaHoursTest×1, MovieSearchCandidatesTest×1, MyKeyManagerTest×4, ReviewDaoTest×1, ReviewRepositoryTest×7).
- One pre-existing compiler warning unrelated to this commit (`Icons.Outlined.MenuBook` deprecation in `GlobalSearchScreen.kt`, not part of staged files).

## Concerns / notes

- The working tree contained substantial unrelated in-progress work (Learnweb, Calendar, GlobalSearch, SettingsDataStore, new LearnwebICalParser + test). I stashed only the modified unrelated files for the commit, then restored the stash so the working tree state matches what was there before the task. The new untracked Learnweb files were never staged.
- An auto-regenerated `app/schemas/.../34.json` appeared after running the build; it is unrelated and remains unstaged for the user to handle.
- Fix 3 has no existing tests for `parseNutrition`, so no test updates were needed (the `Per100g`/`nutritionFingerprint` tests in `RecipeHashTest` construct `Per100g` directly).
- No spec for the Tink-keyset wire format yet — Phase 3 needs to make the call documented in the new `TODO(phase3-wire)` comment in `Ed25519.kt`.
