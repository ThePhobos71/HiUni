# 02 — Architecture Overview

> Hochlevel-Architektur, Modulgrenzen, Cross-Feature-Regeln. Detail-Entscheidungen siehe ADRs.

## Modul-Diagramm

```
┌────────────────────────────────────────────────────────────────────┐
│                       de.transio.hiuni (:app)                      │
│                                                                    │
│  ┌─────────────┐   ┌─────────────┐   ┌──────────────────────┐     │
│  │ navigation/ │   │ ui/         │   │ di/                  │     │
│  │ Destinations│   │ responsive/ │   │ DatabaseModule       │     │
│  │ AppNavGraph │   │ Adaptive-   │   │ NetworkModule        │     │
│  │             │   │ Scaffold    │   │ DataStoreModule      │     │
│  └─────────────┘   └─────────────┘   └──────────────────────┘     │
│                                                                    │
│  ┌──────────────────────  core/  ──────────────────────────────┐  │
│  │ auth/      common/    database/    datastore/   design/     │  │
│  │ icon/      network/   nfc/         notifications/           │  │
│  │ search/    security/  startup/     sync/                    │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                    │
│  ┌──────────────────────  feature/ ────────────────────────────┐  │
│  │ home/      calendar/   mensa/      mensacard/   movies/     │  │
│  │ bib/       email/      learnweb/   lsf/         courses/    │  │
│  │ exams/     todos/      sport/      profile/     search/     │  │
│  │ settings/  notifications/  onboarding/  about/              │  │
│  └─────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────┘
        │                                                │
        │ konsumiert                                     │ konsumiert
        ▼                                                ▼
┌─────────────────────────┐                ┌──────────────────────────┐
│ :shared-events          │                │ hiuni-relay              │
│ Ed25519 Sign/Verify,    │                │ Ktor + SQLite WebSocket  │
│ Canonical Event-Form,   │                │ /sync — P2P-Relay für    │
│ Tink                    │                │ Reviews (Bonus-Feature)  │
└─────────────────────────┘                └──────────────────────────┘
```

## Schichten-Verantwortung

| Schicht | Verantwortung | Wer darf was |
|---|---|---|
| `feature/<x>/ui/` | Composables, Screens, Sheets | Nur ViewModel des eigenen Features |
| `feature/<x>/<X>ViewModel.kt` | UiState, Intent-Verarbeitung | Nur eigenes Repository, evtl. cross-feature read-only |
| `feature/<x>/data/` | Repository, Entity, DAO, Scraper/API | Nur eigene Datenquellen + `core/` |
| `core/` | Geteilte Infrastruktur (DB, HTTP, Security, Settings) | Wird von allen Features konsumiert, importiert kein Feature |
| `navigation/`, `di/`, `ui/responsive/` | App-Wiring | Importiert alle Features minimal (nur Routes / Module-Bindings) |

## Cross-Feature-Regeln

Aus `README.md` (Repo-Root) und durchgehalten:

1. **`feature.home` ist Aggregator** — darf alle Feature-Repos read-only injecten (Tagesübersicht braucht Daten aus Calendar + Mensa + Mail).
2. **`feature.email` ist die einzige Ausnahme** im Credential-Bereich — darf `core.security.CredentialsManager` nutzen.
3. **Sonst keine Cross-Feature-Imports.** Features kommunizieren über `core/` oder gar nicht.
4. **`feature.notifications` schreibt nicht aus eigenem Code** — alle Features triggern Notifications via `core.notifications.NotificationScheduler` + zentralem `NotificationLogRepository`.

Verstöße sind im PR-Review zu fangen. Bisher keine Verstöße im Master-Branch.

## ADR-Index

Alle Architecture Decision Records liegen unter [`docs/adr/`](../adr/):

| ADR | Titel | Kernentscheidung | Status |
|---|---|---|---|
| [0001](../adr/0001-feature-first-packages.md) | Feature-First Packages | Pakete nach Feature, nicht nach Schicht | Accepted |
| [0002](../adr/0002-compose-single-activity.md) | Compose Single Activity | Eine `MainActivity`, alles Compose, kein Fragment | Accepted |
| [0003](../adr/0003-hilt-for-di.md) | Hilt für DI | Hilt statt Koin oder manuelles DI | Accepted |
| [0004](../adr/0004-room-single-appdatabase.md) | Room mit Single AppDatabase | Eine zentrale `AppDatabase`, Feature-owned Entities | Accepted |
| [0005](../adr/0005-library-strategy.md) | Library-Strategie | Bewusste Library-Wahl, Begründung pro Lib | Accepted |
| [0006](../adr/0006-no-unified-calendar.md) | Kein Unified-Calendar | Kein Cross-Provider-Sync, eigene Custom-Events + Snapshots | Accepted |
| [0007](../adr/0007-agp-8-7-stable.md) | AGP 8.7 statt 8.8/9 | KSP1 + AGP-8.7-Stack, KSP2 absichtlich nicht | Accepted |

## Wichtige Klassen pro Schicht

Pro Modul 2–3 Schlüsselklassen mit Ein-Satz-Beschreibung. Wer das Repo neu öffnet, findet hier den Einstieg.

### `core/` — Geteilte Infrastruktur

- `core.database.AppDatabase` — Single Room-DB, alle Feature-Entities, versionierte Migrations (`Migrations.kt`), Schema-Export nach `app/schemas/`.
- `core.security.CredentialsManager` — EncryptedSharedPrefs-Wrapper mit Self-Healing-Reset für Mail-Credentials.
- `core.security.DatabaseKeyProvider` — sqlcipher-Key-Generierung + Persistenz im Android-Keystore.
- `core.security.MailLockGate` — Fingerabdruck-Gate für den Mail-Tab.
- `core.network.OkHttpClientProvider` — geteilter HTTP-Client mit Cookie-Jar + 5MB-Cache, von allen Scrapern konsumiert.
- `core.network.PolitenessInterceptor` — Rate-Limiting + Random-Delay vor LSF/CAS-Hits (gegen Pattern-Detection durch Uni-IT).
- `core.auth.WebLoginActivity` + `core.auth.CasSession` + `core.auth.CasCookieStore` — CAS-SSO-Flow für Learnweb/LSF, Multi-Redirect mit Cookie-Carrying.
- `core.sync.LoginSyncOrchestrator` — koordiniert Sync-Trigger nach erfolgreichem Login.
- `core.sync.LsfSyncWorker` / `SportSyncWorker` / `LearnwebAssignmentReminderScheduler` — WorkManager-Periodic-Jobs.
- `core.notifications.NotificationScheduler` + `NotificationPresenter` + `NotificationReceiver` + `NotificationDeepLinkController` — vier-Klassen-Pipeline: schedulen → empfangen → anzeigen → in App tiefen-verlinken.
- `core.startup.StartupRefresher` — Cold-Start-Vorwärmen (Movie-Poster, Sport-Programm).
- `core.search.GlobalSearchRepository` — modul-übergreifende Spotlight-Suche.
- `core.icon.AppIconManager` — App-Icon-Switcher mit Semester-Unlock-System.
- `core.nfc.NfcScanController` — NFC-Lese-Helper (für Mensa-Karte, deaktiviert in Production weil STW-NFC nicht spec'd ist).

### `ui/responsive/` — Adaptive Layouts

- `ui.responsive.AdaptiveScaffold` — wählt zur Laufzeit zwischen Bottom-Nav (Phone), Rail (Tablet portrait) und Drawer (Tablet landscape) basierend auf `LocalWindowSizeClass`.

### `feature/home/` — Aggregator

- `HomeViewModel` + `HomeSectionsViewModel` + `QuickAccessViewModel` — drei Layer: HomeUiState-Combine, Section-Reorder, QuickAccess-Tile-Counts. Einziges Feature, das alle anderen Repos read-only konsumiert.

### `feature/calendar/` — eigene Events + Snapshots

- `CalendarViewModel` — 5-Quell-Combine (view-mode, selected-date, eventsFlow, editing, sheet-open).
- `feature.calendar.data.CustomEventEntity` — Event mit `sourceKind` (USER / MENSA_PIN / MOVIE_PIN / LEARNWEB / LSF), `recurrenceRule` (RFC 5545 light).
- `feature.calendar.data.CalendarRepository` — observeRange + insert/update/delete + Notification-Scheduling-Integration.

### `feature/mensa/` — STW-ON-API + Bewertungen

- `MensaViewModel` — DayStrip + Diet-Filter + Pin-to-Calendar.
- `feature.mensa.data.MensaApiService` + `MensaRepository` — STW-ON-Endpoint mit `flatMapLatest` über Settings (Location-Wechsel triggert Reload).
- `feature.mensa.data.MealEntity` — Composite-Key (date + location + lane + meal), strukturierte `tags.{categories,allergens,additives,special}`.
- `feature.mensa.review.ReviewRepository` — lokale Aggregation + Submit/Retract mit Ed25519-Signatur via `:shared-events`.
- `feature.mensa.review.MyKeyManager` — Ed25519-Keypair mit Android-Keystore-Wrapping.

### `feature/learnweb/` — CAS-SSO + Abgaben

- `LearnwebClient` — CAS-SSO-Login + Kurs-Liste-Scraper.
- `LearnwebScraper` + `LearnwebICalParser` — Assignment-Deadlines (HTML + iCal-Spiegelung).
- `LearnwebRepository` + `LearnwebCalendarSync` — Cache + Calendar-Spiegelung als CustomEvent.

### `feature/lsf/` — Notenübersicht (Scraper)

- `feature.lsf.data.*` — Scraper für LSF-Noten (Shibboleth-Workaround via Login-Session).

### `feature/email/` — IMAP

- `EmailViewModel` + `EmailComposeViewModel` + `EmailDetailActionsViewModel` — drei ViewModels für die drei Mail-Sub-Screens (Liste, Compose, Detail-Actions).
- `feature.email.data.*` — Jakarta-Mail-IMAP-Client, Folder-SPECIAL-USE-Discovery, Autocomplete-Index.
- `MailSwipeAction` — konfigurierbare Swipe-Gesten (Archive / Delete / Mark-Unread).

### `feature/bib/` — ubwww-Scraper

- `BibViewModel` — Raum-Verfügbarkeit, Lieblings-Räume mit Push.
- `feature.bib.data.*` — ubwww-Scraper, Session-Fixation-Workaround (siehe `docs/UBWWW_BUG_SESSION_FIXATION.md`).

### `feature/movies/` — unifilm.de

- `MoviesViewModel` + `MovieDetailViewModel` — Programmliste + Featured-Hero + Detail-Sheet.
- `feature.movies.data.*` — unifilm.de-Scraper mit Heuristik für Genre/Dauer/Saal.

### `feature/sport/` — HSP-Scraper

- `SportViewModel` + `SportDetailViewModel` — Kategoriefilter + Detail.
- `feature.sport.data.*` — HSP-Scraper über öffentliche Programm-Seite.

### `feature/courses/` + `feature/exams/` + `feature/todos/` — lokal verwaltet

Workaround für LSF-Shibboleth-ohne-API: User trägt Kurse, Klausuren und Aufgaben lokal ein. Felder verlinken zur Calendar-Spiegelung (siehe ADR-0006).

### `feature/profile/` — Studierendenausweis-Karte

- `ProfileViewModel` — Anzeige eingetragener Matrikel-Daten + Quick-Access-Grid.

### `feature/settings/` — DataStore-zentral

- `SettingsViewModel` (+ Sub-VMs `CasLoginViewModel`, `DisplayNameViewModel`, `LsfMyCoursesViewModel`, `LsfStundenplanViewModel`, `NavTabsViewModel`) — sektionierte Settings inkl. Login-Status, Reorderable-Nav.

### `feature/onboarding/` — Erstbenutzung

- `OnboardingViewModel` — geführter Erst-Setup (Mensa-Standort, optional Login).

### `feature/notifications/` — Push-Center

- `NotificationsViewModel` — gruppierte Anzeige (Heute / Gestern / Älter), Auto-Prune nach 30 Tagen.

### `feature/mensacard/` — lokales Guthaben-Tracking

Workaround: Da STW-NFC nicht spec'd ist, User trägt Aufladungen/Abbuchungen manuell ein.

### `feature/search/` — Spotlight

- `GlobalSearchViewModel` — konsumiert `core.search.GlobalSearchRepository` für Modul-übergreifende Suche.

### Standalone Module

- `:shared-events` — Event-Datenklassen + Canonical-Form + Tink-Ed25519, von App und Relay konsumiert.
- `hiuni-relay/` — Ktor-Server mit SQLite-EventStore, WebSocket `/sync` (Hello/Event/Events-Protokoll), Dockerfile + Caddy-Sidecar für TLS.

## Datenfluss-Beispiel (Mensa)

```
STW-ON-API ──► MensaApiService ──► MensaRepository ──► Room (meals)
                                          │
                                          ▼
                                   MensaViewModel ── Flow ──► MensaScreen
                                          │
                                          ▼
                                   "In Kalender packen" ──► CalendarRepository
                                                                    │
                                                                    ▼
                                                          NotificationScheduler
```

### Datenfluss-Beispiel — Reviews mit Signatur und Federation

```
User tippt Bewertung
        ▼
ReviewBottomSheet / Bewerten-Page
        ▼
ReviewViewModel.submit()
        ▼
ReviewRepository.submit(meal, rating, wouldOrderAgain)
        ▼
:shared-events.canonicalize(event)  ─── recipeHash mit Nährwert-Fingerprint
        ▼
MyKeyManager.sign(canonical)  ──────── Ed25519 via Tink, Key in Android-Keystore
        ▼
ReviewRepository persistiert ────► Room (review_events) + lokale Aggregation
        ▼
        ▼ (optional, opt-in)
hiuni-relay /sync WebSocket  ─────► EventValidator (Signatur, Trust-WoT, Spam)
                                  │
                                  ▼
                          SQLite-EventStore (Relay)
                                  │
                                  ▼ (Push an andere verbundene Devices)
                            Andere App-Instanzen
                                  │
                                  ▼
                          ReviewRepository.ingest(remoteEvent)
                                  │
                                  ▼
                          MealDetailSheet zeigt aggregiertes Rating + WoT-Trust-Indikator
```

Wenn kein Relay konfiguriert ist (Default), bleiben Reviews ausschließlich auf dem eigenen Device — Fail-Soft-Verhalten.
