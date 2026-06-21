# Changelog

Format orientiert an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/).

## [Unreleased]

### Home Clickability + Reusable Components (2026-05-24)

- HomeScreen vollständig klickbar: Avatar → Settings, Bell → Settings (Push-Center kommt später), Next-Lesson-Banner → Calendar, Quick-Tiles → Mensa/Bib/Email/Calendar, Section-„Alle anzeigen" → jeweilige Feature-Screen, Uni-Kino-Karten → Movies
- `core/design/components/QuickTile.kt` + `SectionLabel.kt` extrahiert als wiederverwendbare Composables — Profil/Notenübersicht/sonstige Screens können sie 1:1 verwenden
- BottomNav umgestellt: Primary = Home/Calendar/Mensa/Movies/Settings (Settings jetzt erreichbar auf Phones); Bib + Email weiterhin via Home-Tiles und im Permanent-Drawer
- AppNavGraph: zentraler `navigate: (Destination) -> Unit` Callback mit saveState/launchSingleTop/restoreState, an HomeScreen weitergegeben
- `docs/DEVELOPMENT.md`: neue Sektion „Wiederverwendbare UI-Bausteine" mit Erweiterung-Patterns für Sections + Quick-Access-Kacheln + Cross-Feature-Navigation

### Phase 2.4 Settings + 2.3 Home Wire-Up (2026-05-24)

- `feature/settings`: voll funktionale Settings-Seite mit Sections für Mensa-Standort (3 Hildesheim-Locations als Cards mit Radio-Indikator), Termin-Erinnerungs-Default (Pill-Chips 0/5/10/15/30/60/120), Email-Sync-Intervall (15/30/60/120 Min), Uni-Hildesheim-E-Mail-Credentials mit OutlinedTextFields + Save/Update/Löschen via CredentialsManager (EncryptedSharedPreferences)
- `feature/home`: Mock-Daten durch echte Repos ersetzt — `HomeViewModel` injectet `CalendarRepository` + `MensaRepository` + `SettingsDataStore`, combine über nextEvent (nächste 14 Tage) + heutige Mensa-Meals + Mensa-Open-Status; QuickTiles zeigen Live-Counts und Mensa-Status
- `feature/settings/data/MensaLocation.kt` mit verifizierten STW-Standorten (150/152/153 für Mensa/Cafeteria/Bistro)

### Phase 2.2 Mensa (2026-05-23)

- STW-ON API integriert: `MensaApiService` mit OkHttp + kotlinx.serialization, Base-URL `https://sls.api.stw-on.de/v1`, Endpoint `/locations/{id}/menu/{from}/{to}` für 14-Tage-Fenster
- DTOs an reale API angepasst: Preise als String mit `,`/`.` Konvertierung zu Cents, `tags.categories/allergens/additives/special` als strukturierte Objekte, Kategorie aus `lane.name` + `time` abgeleitet (Mittag/Abend-Prefix)
- `MealEntity` mit Composite-Key (`sourceId` + `locationId`), Index auf (`date`, `locationId`)
- Migration 1→2 in `core/database/Migrations.kt`, AppDatabase v2
- `MensaRepository`: observeForDate, observeAvailableDates (für Day-Strip), refresh mit `replaceWindow` (atomic delete + upsert), prune älterer Daten
- `MensaViewModel`: DayPicker-State, Kategorien-Filter, Pull-to-Refresh, `pinToCalendar` als CustomEventEntity-Snapshot mit Mittag/Abend-Logik
- `MensaScreen`: Header mit Datum + Refresh-Action, horizontaler DayStrip 14 Tage, Filter-Chips pro Kategorie, Meal-Cards mit Preis (Studi-Tarif amber) + Tag-Pills (vegan=grün, fisch=primary, schwein=amber, rind=rot), Pin-Icon zum Kalender-Snapshot, PullToRefreshBox + Snackbar-Errors
- Tests: `MensaDtosTest` mit echtem STW-ON-Sample, evening-Time-Prefix, Date-fehlt-Skip, Preis-Cents-Mapping (4 Tests)

### Phase 2.1 Calendar (2026-05-23)

- `CalendarUiState` + `CalendarViewModel`: StateFlow-combine über Repository + View-Mode + Selected-Date, Add/Edit/Delete-Aktionen mit automatischem Notification-Scheduling/Cancel
- 3 Views: `CalendarListView` (gruppiert nach Tag, 6-Monats-Fenster), `CalendarDayView` (Tages-Agenda), `CalendarWeekView` (Mo-Fr-Strip + Events des Tages)
- `AddEditEventSheet`: ModalBottomSheet mit Title/Beschreibung/Ort, DatePicker + TimePicker für Start+End, Reminder-Chips (0/5/10/15/30/60/120 Min)
- Event-Detail über dasselbe Sheet (Edit-Mode) mit Delete-Bestätigung
- `CalendarScreen` mit Segmented-View-Switcher (Liste/Tag/Woche), Extended-FAB für Add, AnimatedContent zwischen Views
- `NotificationReceiver` postet echte Notifications via `NotificationCompat` (Channel + Auto-Cancel + Launch-Intent zur MainActivity); POST_NOTIFICATIONS Permission-Check auf Android 13+
- Unit-Test-Pattern: `CalendarRepositoryImplTest` mit MockK + Turbine — observeRange/insert/update/delete

### Phase 1 Foundation (2026-05-23)

- Initial-Setup: AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, Java 17
- Compose + Material 3 + Hilt + Room + DataStore + WorkManager als Stack
- Package umbenannt: `de.transio.uni_hi` → `de.transio.hiuni`, App-Name auf "HiUni"
- Feature-First Package-Struktur in `:app` (siehe `docs/adr/0001-feature-first-packages.md`)
- Stub-Screens für Home, Kalender, Mensa, Filme, Bib, E-Mail, Settings, About
- `feature.calendar` mit echtem `CustomEventEntity` + DAO + Repository (Phase-2-ready)
- `core/security/CredentialsManager` mit Self-Healing-Reset-Pattern aus v1 (re-implementiert)
- 3-Layout Adaptive Scaffold (Bottom Nav / Rail / Permanent Drawer) via WindowSizeClass
- 7 ADRs geschrieben (`docs/adr/0001` bis `0007`)
- `AI_USAGE.md`, `README.md`, `CHANGELOG.md` initial
- GitHub Actions CI: assembleDebug + lintDebug auf jeden PR

### Entwickler-Guide

- `docs/DEVELOPMENT.md` — Kochbuch mit Copy-Paste-Patterns für: Room-Feature anlegen, Web-Scraper, REST-API, Credentials, Notifications, Background-Sync (WorkManager), Cross-Feature-Reads (Home-Aggregator), Settings-Toggles, "In Kalender packen"-Snapshot, Tests, Design-Tokens, Hilt-Cheatsheet, Build-Errors-Survival-Guide

### Bekannte Stolpersteine

- KSP1 statt KSP2 (`ksp.useKSP2=false`) bis Room ≥ 2.7 im Stack ist
- `compileSdk = 36` über AGP-8.7-getestetem Range, suppressed
