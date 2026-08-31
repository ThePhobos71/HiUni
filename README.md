# HiUni v2

> Begleit-App für Studierende der Uni Hildesheim. Modernes Android, Compose-only, Feature-First-Architektur.

**Stand:** Feature-komplett (MSE-Abgabe), aktiv gepflegt
**Modul:** Mobile Software Engineering (Prof. Dr. Marc Hesenius, Uni Hildesheim)

## Features

Alle folgenden Features sind implementiert:

- **Home**: Tagesübersicht mit nächster Vorlesung (Countdown), heutiger Mensa, ungelesenen Mails und fälligen Aufgaben
- **Kalender**: eigene Events, wiederkehrende Termine (RFC 5545 light), Listen-, Tages-, Wochen- und Monatsansicht, Mensa- und Sport-Pins
- **Mensa**: STW-ON-API, 14-Tage-Plan, Nährwerte, Diät-Filter, Live-Öffnungszeiten
- **Kino**: unifilm.de-Scraper mit Trailer und Postern
- **Bibliothek**: Gruppenraum-Auslastung und Buchung direkt in der App (ubwww)
- **E-Mail**: IMAP/SMTP über Jakarta Mail, verschlüsselt, optionaler Fingerabdruck-Schutz
- **Kurse & Noten**: LSF-Kursimport plus Notenspiegel-Scraper mit Schnitt/GPA
- **Klausuren**: Klausurplan mit Countdown
- **Hochschulsport**: Kursplan und Auslastung
- **Learnweb**: CAS-SSO, Kursliste, Abgabefristen mit Kalender-Spiegelung, iCal-Feed
- **Mensa-Karte**: NFC-Guthaben und Transaktionsverlauf
- **Benachrichtigungen**: In-App-Push-Center und konfigurierbare Reminder
- **Globale Suche**: Spotlight über alle Module
- **Home-Widgets**: fünf Glance-Widgets (Stundenplan Tag/Woche, Mensa, Aufgaben, Klausur-Countdown)
- **Settings & Profil**: Mensa-Standort, Sync-Intervalle, Nav-Reorder, Theme, Onboarding

Noch offen: Lerngruppen, Campus-Plan, digitale StudiCard. Der FCM-Tickle-Server (`server/`) ist implementiert und getestet, aber noch nicht produktiv deployed.

## Tech-Stack

- **Sprache:** Kotlin 2.0
- **UI:** Jetpack Compose + Material 3, 3 responsive Layouts (Bottom/Rail/Drawer), Glance-Home-Widgets
- **Architektur:** Single-Activity, MVVM, Feature-First-Packages, offline-first
- **DI:** Hilt
- **Persistence:** Room (Single AppDatabase, SQLCipher-verschlüsselt, Schema 35 mit 34 Migrationen)
- **Settings:** DataStore Preferences
- **Security:** EncryptedSharedPreferences + Android Keystore, BiometricPrompt
- **Background:** WorkManager (Sync-Worker), Firebase Cloud Messaging (Tickle-Push)
- **HTTP:** OkHttp + Cookie-Jar + 5MB Cache + Politeness-Interceptor
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

### local.properties

`local.properties` ist gitignored und muss lokal zwei Einträge enthalten:

```properties
sdk.dir=/pfad/zum/Android/sdk
tmdb.api.key=DEIN_TMDB_API_KEY
```

Der TMDB-Key wird für die Kino-Poster gebraucht und als `BuildConfig.TMDB_API_KEY` injiziert (Fallback: Umgebungsvariable `TMDB_API_KEY`). Ohne Key baut die App weiter, die Poster-Anreicherung bleibt dann leer.

### Lokal bauen

```bash
./gradlew testDebugUnitTest   # Unit-Tests
./gradlew assembleDebug
./gradlew lintDebug
./gradlew installDebug        # auf angeschlossenes Device
```

### Schemas

Room-Schemas werden bei jedem Build nach `app/schemas/de.transio.hiuni.core.database.AppDatabase/` exportiert. Diese Files werden ins Repo committet, sie sind die Basis für Migration-Tests.

### Firebase einrichten

`app/google-services.json` ist **gitignored** (enthält projektspezifische Keys) und muss lokal einmal angelegt werden, sonst schlägt der `google-services`-Gradle-Task fehl:

1. In der [Firebase Console](https://console.firebase.google.com/) ein Projekt anlegen (oder das bestehende HiUni-Projekt öffnen).
2. Dort **zwei** Android-Apps registrieren, Debug-Builds tragen das `.debug`-Suffix:
   - Package `de.transio.hiuni`
   - Package `de.transio.hiuni.debug`
3. Die generierte `google-services.json` herunterladen und nach `app/google-services.json` legen (eine Datei deckt beide App-IDs ab).

Ohne `google-services.json` baut die App trotzdem (das Firebase-Plugin wird nur bei vorhandener Datei angewandt), dann ist FCM/Push inaktiv. Für den optionalen FCM-Tickle-Server (Mail-/Sync-Push) siehe `server/README.md`.

## Weiterentwickeln

Komplettes Kochbuch mit Copy-Paste-Patterns für alle Feature-Typen (Persistenz, Scraper, REST-API, Credentials, Notifications, Background-Sync, Cross-Feature-Reads, Settings, Glance-Widgets, Tests, Design-Tokens, Hilt-Cheatsheet, Build-Errors-Survival-Guide): **[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)**.

Kurz: ein neues Feature ist Package anlegen, dann ViewModel + Screen + Repository, Route in `Destinations.kt` und `AppNavGraph.kt`, bei Persistenz Entity in `AppDatabase` plus Migration, Hilt-Module dazu. Die `docs/DEVELOPMENT.md` zeigt das mit konkretem Code für jedes Pattern.

## Feature-Katalog

Vollständige Produkt-Vision mit Status pro Detail (shipped / planned / stretch / out-of-scope): **[docs/FEATURES.md](docs/FEATURES.md)**.

## Architektur

```
de.transio.hiuni/
├── core/              # Shared Infrastructure
│   ├── auth/          # CAS-SSO, WebLogin, Cookie-Store
│   ├── database/      # AppDatabase, Converters, Migrations
│   ├── datastore/     # SettingsDataStore
│   ├── design/        # Theme, Color, Typography, UI-Bausteine
│   ├── icon/          # App-Icon-Switcher, Semester-Unlock
│   ├── network/       # OkHttp-Provider, Politeness, Connectivity
│   ├── nfc/           # IsoDep-Scan, MensaCardReader
│   ├── notifications/ # Scheduler, Presenter, Push-Center
│   ├── push/          # FCM-Service, Tickle-Handler, Registration
│   ├── search/        # GlobalSearchRepository
│   ├── security/      # CredentialsManager, DatabaseKeyProvider
│   ├── startup/       # Cold-Start-Prewarming
│   ├── sync/          # WorkManager-Worker, Prefetch-Orchestrator
│   └── common/        # Result, DateTimeUtils
├── di/                # Hilt App-Modules
├── navigation/        # Destinations, NavGraph
├── ui/responsive/     # AdaptiveScaffold (3 Layouts)
└── feature/<name>/    # alle Feature-Inhalte
    ├── ui/            # Composables
    ├── data/          # Repo, Entity, DAO, Scraper/API
    └── XxxViewModel.kt
```

Feature-Packages: `home`, `calendar`, `mensa`, `movies`, `bib`, `email`, `courses`, `grades`, `exams`, `sport`, `learnweb`, `lsf`, `mensacard`, `notifications`, `search`, `profile`, `settings`, `onboarding`, `todos`, `widgets`, `about`.

### Cross-Feature-Regeln

- `feature.home` darf alle Feature-Repos read-only injecten (Aggregator)
- `feature.widgets` liest Repository-Flows direkt (Glance kennt keine ViewModels)
- `feature.email` darf `core.security.CredentialsManager` nutzen
- Sonst keine Cross-Feature-Imports

Mehr Details in `docs/adr/`.

## Abgabe

Der MSE-Projektbericht (LaTeX, englisch) samt PDF, Architekturdiagramm und Mockups liegt in **[abgabe/](abgabe/)**. Die deutsche Fassung ist als `abgabe/main-de.tex` archiviert.

## AI-Disclosure

Siehe `AI_USAGE.md`. Wir nutzen Claude Code für Code-Generierung. Jeder Code wird vor dem Merge gereviewt und getestet.

## Lizenz

MIT?
