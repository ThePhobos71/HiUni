# HiUni — Library-Liste für Prof-Anfrage

> Komplette Liste aller relevanten Libraries für den Rebuild, sortiert nach Notwendigkeit. Mit Maven-Koordinaten, Alternativen und Aufwand-Schätzungen.

**Stand:** 2026-05-18

## ✅ STATUS-UPDATE: Prof-Regel ist klar

**Die Regel:**
> Alle Libraries sind okay, solange sie nur **Programmieraufwand reduzieren** (Helper, Tools, Frameworks).
> **Nicht erlaubt** sind Libraries, die im Prinzip *die ganze App* abdecken — also keine "Import-and-done"-Pakete.

**Damit ist faktisch ALLES freigegeben, was wir bisher diskutiert haben:**

| Library | Status | Grund |
|---|---|---|
| AndroidX/Jetpack (alle) | ✅ | Platform-Erweiterung |
| Compose, Material 3 | ✅ | UI-Framework |
| Hilt | ✅ | DI-Helper |
| Firebase (FCM, Crashlytics) | ✅ | Cloud-Helper (Datenschutz trotzdem klären) |
| kotlinx.coroutines, kotlinx.serialization | ✅ | Kotlin-Ökosystem |
| **Jakarta Mail (Angus Mail)** | ✅ | IMAP-Protokoll-Helper |
| **OkHttp** | ✅ | HTTP-Helper |
| **Jsoup** | ✅ | HTML-Parser-Helper |
| **Coil** | ✅ | Image-Loading-Helper |
| **Timber** | ✅ | Logging-Helper |
| **MockK, Turbine, Robolectric** | ✅ | Test-Helper |
| **Reorderable Compose** | ✅ | Drag&Drop-Helper |
| **Lottie** | ✅ | Animation-Helper |
| **LeakCanary** | ✅ | Debug-Helper |

**Was wäre NICHT erlaubt** (zur Orientierung, Edge-Case-Beispiele):
- Eine fertige "Uni-Companion-SDK" die HiUni komplett liefert
- Eine UI-Library mit fertigem Mail-Client-Screen (komplette UI als Black-Box)
- Calendar-Libraries mit fertiger Kalender-UI + Logik kombiniert
- Game-Engines (für eine Game-App wäre das "die ganze App")

**Konkrete Regel zum Selber-Prüfen:**
*"Wenn ich nach `import xxx` die Feature-Anforderung 1:1 erfüllt habe ohne eigene App-Logik zu schreiben — dann ist es zu viel."*

→ **Wir können alle Libraries dieser Doku ohne weitere Rückfrage nutzen.**

---

## Inhalt

1. [Tier 0 — AndroidX/Jetpack (sollte keine Exception brauchen)](#tier-0--androidxjetpack-sollte-keine-exception-brauchen)
2. [Tier 1 — Must-Have](#tier-1--must-have)
3. [Tier 2 — High-Value (sparen massiv Zeit)](#tier-2--high-value-sparen-massiv-zeit)
4. [Tier 3 — Quality-of-Life](#tier-3--quality-of-life)
5. [Tier 4 — Spezial-Features](#tier-4--spezial-features)
6. [Tier 5 — Backend / Cloud](#tier-5--backend--cloud)
7. [Anfrage-Strategie](#anfrage-strategie)
8. [Email-Vorlage für Prof](#email-vorlage-für-prof)

---

## Tier 0 — Google-Libraries (✅ ALLE ERLAUBT, keine Exception nötig)

Der Prof hat bestätigt: alles von Google geht.

### AndroidX / Jetpack (UI, Persistence, Lifecycle)

| Library | Wofür | Maven Coordinate |
|---|---|---|
| **Jetpack Compose** | UI Framework | `androidx.compose.*` (BOM-managed) |
| **Compose Material 3** | Material Design Komponenten | `androidx.compose.material3:material3` |
| **Material Icons Extended** | Icon Set | `androidx.compose.material:material-icons-extended` |
| **Navigation Compose** | In-App Navigation | `androidx.navigation:navigation-compose` |
| **Lifecycle ViewModel** | MVVM | `androidx.lifecycle:lifecycle-viewmodel-compose` |
| **Lifecycle Runtime KTX** | Lifecycle-aware Coroutines | `androidx.lifecycle:lifecycle-runtime-ktx` |
| **Activity Compose** | Compose-Activity-Integration | `androidx.activity:activity-compose` |
| **Room** | SQLite ORM | `androidx.room:room-runtime` + `room-ktx` + `room-compiler` (KSP) |
| **DataStore Preferences** | Modern SharedPreferences-Ersatz | `androidx.datastore:datastore-preferences` |
| **WorkManager** | Background Jobs | `androidx.work:work-runtime-ktx` |
| **Security Crypto** | EncryptedSharedPreferences | `androidx.security:security-crypto` |
| **Window Manager** | WindowSizeClass für Responsive | `androidx.window:window` + `androidx.compose.material3:material3-window-size-class` |
| **Core KTX** | Kotlin Extensions für Android | `androidx.core:core-ktx` |
| **Splashscreen** | Splash Screen API | `androidx.core:core-splashscreen` |
| **CameraX** | Camera Use-Cases (falls Bonus-Feature) | `androidx.camera:camera-camera2` + `camera-lifecycle` + `camera-view` |

### Dependency Injection (Google)

| Library | Wofür | Maven Coordinate |
|---|---|---|
| **Hilt (Dagger)** | DI Framework, eliminiert ViewModel-Factories | `com.google.dagger:hilt-android:2.51` + `hilt-compiler` (KSP) |
| **Hilt Navigation Compose** | `hiltViewModel()` in Compose | `androidx.hilt:hilt-navigation-compose:1.2.0` |
| **Hilt Work** | Hilt-Integration für WorkManager | `androidx.hilt:hilt-work:1.2.0` |

→ **Empfehlung:** Hilt nutzen! Spart Boilerplate, sieht professional aus → Architecture-Punkte.

### Firebase (Cloud, nur wenn gewünscht)

| Library | Wofür | Maven Coordinate |
|---|---|---|
| **Firebase BOM** | Version-Alignment | `com.google.firebase:firebase-bom:32.7.0` |
| **Firebase Cloud Messaging** | Push Notifications | `com.google.firebase:firebase-messaging-ktx` |
| **Firebase Crashlytics** | Crash Reporting | `com.google.firebase:firebase-crashlytics-ktx` |
| **Firebase Analytics** | Usage-Tracking | `com.google.firebase:firebase-analytics-ktx` |

⚠️ **Datenschutz beachten:** Firebase = Daten-Transfer an Google. Bei einer Uni-App mit echten Nutzerdaten (Email-Credentials!) **vorher klären**, ob Firebase okay ist. FCM für Push-Notifications ist okay, aber Analytics würde ich für Uni-Projekt skippen.

### Testing (Google / AndroidX)

| Library | Wofür | Maven Coordinate |
|---|---|---|
| **JUnit 4** | Unit Test Framework | `junit:junit:4.13.2` |
| **AndroidX Test** | Instrumented Test Base | `androidx.test:core` + `runner` + `rules` |
| **Espresso** | UI Tests | `androidx.test.espresso:espresso-core:3.5.1` |
| **Compose UI Test** | Compose-spezifische UI Tests | `androidx.compose.ui:ui-test-junit4` |
| **Room Testing** | Migration Test Helper | `androidx.room:room-testing:2.6.1` |
| **Coroutines Test** | Test-Dispatcher etc. | `org.jetbrains.kotlinx:kotlinx-coroutines-test` (technisch JetBrains — siehe unten) |

---

## Tier 1 — Email & Network Foundation (alle freigegeben)

| Library | Wofür | Maven Coordinate |
|---|---|---|
| **Jakarta Mail (Angus Mail)** | IMAP/SMTP Email-Client | `org.eclipse.angus:angus-mail:2.0.3` + `jakarta.mail:jakarta.mail-api:2.1.3` |

```kotlin
implementation("org.eclipse.angus:angus-mail:2.0.3")
implementation("jakarta.mail:jakarta.mail-api:2.1.3")
```

> **Hinweis:** `kotlinx.coroutines` und `kotlinx.serialization` sind in **Tier 0** gelistet (Kotlin-Ökosystem).

---

## Tier 2 — HTTP / HTML / Images (alle freigegeben)

| Library | Wofür | Maven Coordinate |
|---|---|---|
| **OkHttp** | HTTP Client mit Cookies, Cache, Connection Pool | `com.squareup.okhttp3:okhttp:4.12.0` |
| **Jsoup** | HTML-Parser für Scraping (Mensa/Movies/Bib) | `org.jsoup:jsoup:1.17.2` |
| **Coil** | Async Image Loading mit Cache (Movie-Poster) | `io.coil-kt:coil-compose:2.6.0` |

```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.jsoup:jsoup:1.17.2")
implementation("io.coil-kt:coil-compose:2.6.0")
```

→ Spart ca. 45h gegenüber Eigenimplementierung mit `HttpURLConnection` + Regex-Parser + manuelles Bitmap-Loading.

---

## Tier 3 — Quality-of-Life (alle freigegeben)

Nice to have, alle als Helper-Libraries erlaubt.

| Library | Wofür | Empfehlung |
|---|---|---|
| **Timber** | Strukturiertes Logging mit Tags + Crashlytics-Bridge | **Empfohlen** — Standard für Production-Apps |
| **MockK** | Test-Mocking (Kotlin-idiomatisch) | **Empfohlen** — viel besser als Mockito für Kotlin |
| **Turbine** | StateFlow/Flow Testing | **Empfohlen** — macht Flow-Tests lesbar |
| **Robolectric** | JVM-side Android Tests (schneller) | Empfohlen wenn CI-Speed wichtig |
| **LeakCanary** | Memory Leak Detection (Debug-only) | Empfohlen — kostet im Release nichts |

### Maven Coordinates

```kotlin
// Logging
implementation("com.jakewharton.timber:timber:5.0.1")

// Testing
testImplementation("io.mockk:mockk:1.13.10")
testImplementation("app.cash.turbine:turbine:1.0.0")
testImplementation("org.robolectric:robolectric:4.11.1")

// Debug-only
debugImplementation("com.squareup.leakcanary:leakcanary-android:2.13")
```

---

## Tier 4 — Spezial-Features

Nur wenn ihr's wirklich nutzt.

| Library | Wofür | Brauchen wir? |
|---|---|---|
| **Reorderable Compose** | Drag & Drop in Lists | Wenn Settings → Navigation Customization gewollt (war in v1) |
| **Accompanist Permissions** | Compose-Helper für Runtime Permissions | Vieles ist mittlerweile in Compose Core — meist nicht nötig |
| **Lottie Compose** | JSON-Animationen (After Effects exports) | Nur für Polish-Wow — wahrscheinlich Bonus-Feature |

### Maven Coordinates

```kotlin
implementation("sh.calvin.reorderable:reorderable:2.4.0")
implementation("com.google.accompanist:accompanist-permissions:0.32.0")
implementation("com.airbnb.android:lottie-compose:6.4.0")
```

---

## Tier 5 — Backend / Cloud

Firebase ist jetzt in Tier 0 (Google). Hier verbleibt nur:

| Library | Wofür | Empfehlung |
|---|---|---|
| **Retrofit + Moshi/Gson** | Type-safe REST Client | **Skip** — OkHttp + `org.json` reicht für unsere paar Endpoints |
| **Ktor Client** | Async HTTP Client (JetBrains) | Skip — Hat Coroutines-Support, aber OkHttp ist Standard |

→ Für reine REST-Calls reicht **OkHttp + org.json**. Retrofit lohnt sich erst bei >10 Endpoints.

---

## Anfrage-Strategie (erledigt)

Die Regel des Profs ("alles okay was nur Programmieraufwand reduziert, keine ganze-App-Pakete") deckt unseren kompletten Library-Bedarf ab. **Keine weitere Email an den Prof nötig**, außer:

- **Datenschutz-Check** falls Firebase mit Cloud-Daten geplant ist
- **Edge-Case-Frage** falls eine konkrete Library grenzwertig wirkt (z.B. eine fertige "EmailUI-Komponente" mit eingebauter UI)

→ Im Zweifel: kurz die Prof-Regel selbst anwenden — *"Macht diese Library nur Aufwand-Reduktion oder ersetzt sie ein ganzes Feature komplett?"*

---

## Edge-Cases zum Mitdenken

Auch wenn alles freigegeben ist — diese **Grenzfälle** würde ich nochmal anfragen wenn sie auftauchen:

| Szenario | Warum grenzwertig | Empfehlung |
|---|---|---|
| Eine "Calendar-Komponente" mit fertiger UI + Logik | Würde Calendar-Feature komplett ersetzen | Fragen |
| Fertige "Mail-Reader"-Library mit UI | Würde Email-Feature komplett ersetzen | Fragen |
| "BookingSDK" mit Buchungs-UI | Würde Bib-Feature komplett ersetzen | Fragen |
| Game-Engine (Unity, Unreal) | Bei Game-App wäre das "die ganze App" | Nicht relevant für uns |
| Backend-as-a-Service (Supabase, Firebase-Auth+Database) | Ersetzt Backend-Logik komplett | Datenschutz-Check |

**Sicher unproblematisch:**
- Alles was nur eine API/Protokoll abstrahiert (Jakarta Mail, OkHttp)
- Alles was nur Boilerplate spart (Hilt, Timber, Coil)
- Alles was nur einen Algorithmus liefert (Jsoup, Reorderable)

---

## Recommended Build-Stack (alles freigegeben — direkt einsetzbar)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    // === Compose BOM (managt alle Compose-Versionen) ===
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    
    // === Compose UI ===
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // === Lifecycle / ViewModel ===
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // === Core / Splashscreen ===
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    
    // === Room (Database) ===
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // === DataStore + Security + WorkManager ===
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // === Hilt (DI) ===
    implementation("com.google.dagger:hilt-android:2.51")
    ksp("com.google.dagger:hilt-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    
    // === Kotlinx (Coroutines + Serialization) ===
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // === Networking ===
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // === Scraping ===
    implementation("org.jsoup:jsoup:1.17.2")
    
    // === Image Loading ===
    implementation("io.coil-kt:coil-compose:2.6.0")
    
    // === Email (Jakarta Mail / Angus Mail) ===
    implementation("org.eclipse.angus:angus-mail:2.0.3")
    implementation("jakarta.mail:jakarta.mail-api:2.1.3")
    
    // === Logging ===
    implementation("com.jakewharton.timber:timber:5.0.1")
    
    // === Optional: Firebase (Datenschutz prüfen) ===
    // implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    // implementation("com.google.firebase:firebase-messaging-ktx")
    // implementation("com.google.firebase:firebase-crashlytics-ktx")
    
    // === Testing — Unit ===
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.robolectric:robolectric:4.11.1")
    
    // === Testing — Instrumented ===
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    
    // === Debug-only ===
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.13")
}
```

**Version Catalog Empfehlung:** Lagert die Versionen in `gradle/libs.versions.toml` aus. Sieht professional aus und gibt Architecture-Punkte.
