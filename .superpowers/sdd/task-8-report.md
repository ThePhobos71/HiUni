### Task 8 Report: Hilt-Bindings + :app konsumiert :shared-events

**Commit:** `1c56475` — feat(reviews): Hilt-Module + :app konsumiert :shared-events

---

#### Files Changed

| File | Change |
|---|---|
| `app/build.gradle.kts` | Added `project(":shared-events")` (with tink exclude) + `libs.google.tink.android` |
| `app/src/main/java/de/transio/hiuni/feature/mensa/review/di/ReviewModule.kt` | New — 6 `@Provides` for ReviewDao, TrustDao, OutboxDao, MyKeyDao, PeerCursorDao, MutedPubkeyDao |

---

#### DI Module Decision

Created a separate `ReviewModule` in `feature/mensa/review/di/` rather than extending `DatabaseModule`. Reasoning: the existing `DatabaseModule` is in `di/` (root-level, cross-feature concern) and also owns the `AppDatabase` singleton + encryption setup. The 6 review DAOs are scoped exclusively to the mensa-review feature. A co-located `di/ReviewModule` keeps the feature self-contained and follows the pattern the brief explicitly recommends.

---

#### Build Outcome

**BUILD SUCCESSFUL in 17s** (`:app:assembleDebug`)

First attempt failed with `Duplicate class` errors for `com.google.crypto.tink.*` — both `tink-1.15.0` (pulled transitively by `:shared-events`) and `tink-android-1.15.0` (added directly) ended up on the classpath. Fixed by adding an `exclude(group = "com.google.crypto.tink", module = "tink")` on the `:shared-events` dependency declaration. `tink-android` is a superset of the JVM `tink` artifact, so excluding the JVM one is correct.

---

#### Self-Review: :shared-events Classpath

`:shared-events` was already declared in `settings.gradle.kts` (`include(":app", ":shared-events")`). Adding `implementation(project(":shared-events"))` in `:app` worked cleanly at the Kotlin/Hilt level. The only issue was the tink duplicate, which was resolved with the exclude. No R8/ProGuard concerns at the debug build stage.

---

#### Concerns

- **ProGuard (release):** `tink-android` may require `-keep` rules for reflection-heavy primitives. Should be verified before release build in a later task.
- **`:shared-events` uses `libs.plugins.kotlin.jvm`** (pure JVM module). This is fine for `:app` to consume as an `implementation` dependency, but if `:shared-events` ever needs Android-specific APIs, the plugin will need to change to `kotlin.android` or `android.library`.
- **No `@Singleton` on DAO provides:** Consistent with existing `DatabaseModule` pattern (DAOs are cheap, `AppDatabase` itself is the singleton). Intentional.
