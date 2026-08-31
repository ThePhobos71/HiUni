### Task 1: Gradle-Modul `:shared-events` anlegen + Tink-Dependency

**Files:**
- Create: `shared-events/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`

**Interfaces:**
- Produces: `:shared-events` Gradle-Modul, konsumierbar via `implementation(project(":shared-events"))`.

- [ ] **Step 1:** `gradle/libs.versions.toml` erweitern — Tink-Version + Library-Einträge:

```toml
# In [versions] adding:
tink = "1.15.0"
kotlinxSerializationCore = "1.7.3"

# In [libraries] adding:
google-tink = { group = "com.google.crypto.tink", name = "tink", version.ref = "tink" }
google-tink-android = { group = "com.google.crypto.tink", name = "tink-android", version.ref = "tink" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
```

- [ ] **Step 2:** `settings.gradle.kts` erweitern:

```kotlin
include(":app", ":shared-events")
```

- [ ] **Step 3:** `shared-events/build.gradle.kts` schreiben:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    implementation(libs.google.tink)
    implementation(libs.kotlinx.serialization.json)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.test)
}
```

(Plugin `kotlin.jvm` muss bereits via Plugin-Catalog erreichbar sein — falls nicht, dazu in `gradle/libs.versions.toml` unter `[plugins]`: `kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }`.)

- [ ] **Step 4:** Verzeichnisstruktur `shared-events/src/main/kotlin/de/transio/hiuni/events/` und `shared-events/src/test/kotlin/de/transio/hiuni/events/` anlegen mit einem leeren Marker:

```kotlin
// shared-events/src/main/kotlin/de/transio/hiuni/events/package-info.kt
package de.transio.hiuni.events
```

- [ ] **Step 5:** Build verifizieren:

```bash
./gradlew :shared-events:build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6:** Commit:

```bash
git add settings.gradle.kts gradle/libs.versions.toml shared-events/
git commit -m "feat(reviews): :shared-events Gradle-Modul mit Tink-Dependency"
```

