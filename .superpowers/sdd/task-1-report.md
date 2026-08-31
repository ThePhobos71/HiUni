# Task 1 Report: :shared-events Gradle-Modul

## Files Changed

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Added `tink = "1.15.0"` in [versions]; `google-tink` + `google-tink-android` in [libraries]; `kotlin-jvm` plugin in [plugins] |
| `settings.gradle.kts` | Added `:shared-events` to `include(...)` |
| `build.gradle.kts` (root) | Added `alias(libs.plugins.kotlin.jvm) apply false` — required to pre-register the plugin on the classpath before sub-module applies it |
| `shared-events/build.gradle.kts` | New file: JVM module with Tink + serialization deps, JDK 21 toolchain |
| `shared-events/src/main/kotlin/de/transio/hiuni/events/package-info.kt` | New file: package marker |

## Build Verification

```
./gradlew :shared-events:build

BUILD SUCCESSFUL in 4s
2 actionable tasks: 2 executed
```

Tasks executed: `compileKotlin`, `jar`. All others NO-SOURCE or UP-TO-DATE as expected for an empty module.

## Self-Review Against Brief

| Requirement | Status |
|-------------|--------|
| Step 1: tink version + libraries in catalog | DONE |
| Step 1: kotlinxSerializationCore version | SKIPPED — `serialization = "1.7.3"` already existed; `kotlinx-serialization-json` library also already existed. No duplication introduced. |
| Step 1: kotlinx-serialization-json library | Already present — not duplicated |
| Step 2: settings.gradle.kts include | DONE |
| Step 3: build.gradle.kts with correct plugins + deps | DONE |
| Step 4: Directory structure + package-info.kt | DONE |
| Step 5: BUILD SUCCESSFUL | DONE |
| Step 6: Commit with exact message | DONE |
| No Co-Authored-By trailer | DONE |
| kotlin-jvm uses version.ref = "kotlin" (2.0.21) | DONE |
| JDK 21 toolchain | DONE |
| No Android dependencies in :shared-events | DONE — uses `kotlin.jvm` plugin only |
| No BouncyCastle | DONE — only Google Tink |

## Concerns / Deviations

1. **Root build.gradle.kts modified (not in brief scope):** The brief listed only `settings.gradle.kts`, `gradle/libs.versions.toml`, and `shared-events/build.gradle.kts` as files to touch. However, adding `kotlin-jvm apply false` to the root build was unavoidable — without it, Gradle throws "plugin already on classpath with unknown version". This is standard Gradle multi-module practice and is safe.

2. **`kotlinxSerializationCore` version not added:** The brief asked to add a `kotlinxSerializationCore` version entry, but the catalog already has `serialization = "1.7.3"` at the exact same version, and `kotlinx-serialization-json` already references it. Adding a duplicate version alias would be confusing. The existing alias is used.

3. **No test sources created:** The brief only specified creating the test directory and a `package-info.kt` in the main source set. The test directory was created but no test file was written — the brief does not ask for one.
