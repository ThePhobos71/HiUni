# ADR-0005: Library-Strategie

**Datum:** 2026-05-23
**Status:** Accepted

## Kontext

Der Prof erlaubt alle Libraries, **die nur Programmieraufwand reduzieren** (Helper, Tools, Frameworks). Nicht erlaubt sind Libraries, die im Prinzip *die ganze App* abdecken (z.B. eine fertige "Uni-Companion-SDK", komplette UI-Komponenten mit Logik).

Die komplette Liste mit Maven-Koordinaten, Tier-Einordnung und Edge-Case-Diskussion liegt in `HIUNI_LIBRARIES.md`.

## Entscheidung

Wir nutzen folgenden Stack ohne weitere Rückfragen beim Prof:

**Tier 0 — Google/AndroidX:**
- Compose + Material 3 + Material Icons Extended
- Navigation Compose, Lifecycle, Activity Compose
- Room, DataStore Preferences, Security Crypto, WorkManager
- Splashscreen, Window Manager
- Hilt (Dagger)

**Tier 1 — Email + Networking:**
- Jakarta Mail (Angus Mail) für IMAP
- OkHttp + Logging Interceptor
- kotlinx.serialization JSON

**Tier 2 — Scraping + Images + Logging:**
- Jsoup für HTML-Parsing
- Coil für Image-Loading
- Timber für strukturiertes Logging

**Tier 3 — Test-Stack:**
- JUnit 4, MockK, Turbine, Robolectric
- Coroutines Test, Room Testing
- Compose UI Test, Espresso

**Tier 4 — Debug-only:**
- LeakCanary

Alle Versionen sind im Version Catalog `gradle/libs.versions.toml` zentral verwaltet.

## Begründung

- **Catalog statt verteilter Versions:** Single Source of Truth, Updates an einer Stelle
- **OkHttp statt HttpURLConnection:** Cookies, Connection-Pool, Cache eingebaut
- **Jsoup statt Regex:** Robustes HTML-Parsing
- **Coil statt manuelles Bitmap-Loading:** Async, Cache, Compose-Integration
- **Hilt statt manuelles DI:** Compile-Time-Sicherheit (siehe ADR-0003)

## Self-Check für neue Libraries

> "Macht diese Library nur Aufwand-Reduktion, oder ersetzt sie ein ganzes Feature komplett?"

Wenn die Antwort *„ersetzt komplett"* ist (z.B. fertige Mail-Client-Library mit UI), wird nachgefragt oder eine andere Lösung gewählt.
