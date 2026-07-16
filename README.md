# HiUni v2

> Begleit-App für Studierende der Uni Hildesheim. Modernes Android, Compose-only, Feature-First-Architektur.

**Stand:** Phase 1 Foundation
**Modul:** OOAD/Mobile Apps

## Features (geplant)

- **Home** — Tagesübersicht (nächster Termin, heutige Mensa, ungelesene E-Mails)
- **Kalender** — eigene Events, Mensa-/Movie-Pins als Snapshots
- **Mensa** — STW-ON-API, 14-Tage-Plan
- **Filme** — unifilm.de-Scraper, Trailer, Poster
- **Bibliothek** — Gruppenraum-Verfügbarkeit
- **E-Mail** — IMAP via Jakarta Mail, EncryptedSharedPreferences
- **Settings** — Mensa-Standort, Sync-Intervalle, Nav-Reorder

Phase 1 liefert nur das Foundation-Gerüst (Theme, Navigation, DI, DB, leere Screens).

## Tech-Stack

- **Sprache:** Kotlin 2.0
- **UI:** Jetpack Compose + Material 3, 3 responsive Layouts (Bottom/Rail/Drawer)
- **Architektur:** Single-Activity, MVVM, Feature-First-Packages
- **DI:** Hilt
- **Persistence:** Room (Single AppDatabase, Feature-owned Entities)
- **Settings:** DataStore Preferences
- **Security:** EncryptedSharedPreferences mit Self-Healing-Reset
- **HTTP:** OkHttp + Cookie-Jar + 5MB Cache
- **HTML-Parser:** Jsoup
- **Images:** Coil
- **E-Mail:** Jakarta Mail (Angus Mail)
- **Build:** AGP 8.7.3 + Gradle 8.9, Java 17

Komplette Library-Liste mit Begründungen in `HIUNI_LIBRARIES.md` und `docs/adr/0005-library-strategy.md`.

## Setup

### Prerequisites
- Android Studio (Ladybug oder neuer)
- JDK 17 (z.B. via Homebrew: `brew install --cask temurin@17`)
- Android SDK mit Platform 36

### Lokal bauen

```bash
./gradlew assembleDebug
./gradlew lintDebug
./gradlew installDebug   # auf angeschlossenes Device
```

### Schemas

Room-Schemas werden bei jedem Build nach `app/schemas/de.transio.hiuni.core.database.AppDatabase/` exportiert. Diese Files werden ins Repo committet — sie sind die Basis für Migration-Tests.

### Firebase einrichten

`app/google-services.json` ist **gitignored** (enthält projektspezifische Keys) und muss lokal einmal angelegt werden — sonst schlägt der `google-services`-Gradle-Task fehl:

1. In der [Firebase Console](https://console.firebase.google.com/) ein Projekt anlegen (oder das bestehende HiUni-Projekt öffnen).
2. Dort **zwei** Android-Apps registrieren — Debug-Builds tragen das `.debug`-Suffix:
   - Package `de.transio.hiuni`
   - Package `de.transio.hiuni.debug`
3. Die generierte `google-services.json` herunterladen und nach `app/google-services.json` legen (eine Datei deckt beide App-IDs ab).

Für den optionalen FCM-Tickle-Server (Mail-/Sync-Push) siehe `server/README.md`.

## Weiterentwickeln

Komplettes Kochbuch mit Copy-Paste-Patterns für alle Feature-Typen (Persistenz, Scraper, REST-API, Credentials, Notifications, Background-Sync, Cross-Feature-Reads, Settings, Tests, Design-Tokens, Hilt-Cheatsheet, Build-Errors-Survival-Guide): **[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)**.

Kurz: ein neues Feature ist Package anlegen → ViewModel + Screen + Repository → Route in `Destinations.kt` + `AppNavGraph.kt` → bei Persistenz Entity in `AppDatabase` + Migration → Hilt-Module dazu. Die `docs/DEVELOPMENT.md` zeigt das mit konkretem Code für jedes Pattern.

## Feature-Katalog

Vollständige Produkt-Vision mit Status pro Detail (shipped / planned / stretch / out-of-scope): **[docs/FEATURES.md](docs/FEATURES.md)**.

## Architektur

```
de.transio.hiuni/
├── core/              # Shared Infrastructure
│   ├── design/        # Theme, Color, Typography
│   ├── database/      # AppDatabase, Converters
│   ├── network/       # OkHttp-Provider
│   ├── datastore/     # SettingsDataStore
│   ├── security/      # CredentialsManager
│   ├── notifications/ # AlarmManager + Receiver
│   └── common/        # Result, DateTimeUtils
├── di/                # Hilt App-Modules
├── navigation/        # Destinations, NavGraph
├── ui/responsive/     # AdaptiveScaffold (3 Layouts)
└── feature/<name>/    # alle Feature-Inhalte
    ├── ui/            # Composables
    ├── data/          # Repo, Entity, DAO, Scraper/API
    └── XxxViewModel.kt
```

### Cross-Feature-Regeln

- `feature.home` darf alle Feature-Repos read-only injecten (Aggregator)
- `feature.email` darf `core.security.CredentialsManager` nutzen
- Sonst keine Cross-Feature-Imports

Mehr Details in `docs/adr/`.

## AI-Disclosure

Siehe `AI_USAGE.md`. Wir nutzen Claude Code für Code-Generierung. Jeder Code wird vor dem Merge gereviewt und getestet.

## Lizenz

MIT (oder TBD).
