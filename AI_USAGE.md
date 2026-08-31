# AI Usage Disclosure

> Lebende Datei. Wird pro relevantem Commit aktualisiert.

## Tools

- **Claude Code (Anthropic)** — primärer AI-Assistent für Code-Generierung, Architektur-Review, Doku-Drafts. Modell: Opus 4.x (1M context).

## Workflow-Prinzip

Wir „vibecoden mit Plan": Architektur-Entscheidungen treffen wir manuell (ADRs in `docs/adr/`), AI generiert daraus Code-Skelette. Jeder generierte Code wird gelesen, getestet und vom Team gegengezeichnet bevor er gemergt wird. Bei Pair-Defense kann jeder Teamteil jedes Modul erklären.

## Was AI macht

- Boilerplate (Room Entities/DAOs, Hilt-Module, ViewModels, Compose-Skelette)
- Refactoring-Vorschläge
- ADR-Drafts (final formuliert vom Team)
- Test-Cases aus Specs

## Was AI NICHT macht

- Architektur-Entscheidungen allein treffen — ADRs werden vom Team unterzeichnet
- Final Commits autonom
- API-Keys oder Credentials handhaben
- Performance-Optimierungen ohne Profiling

## Sessions / Commit-Sektionen

### 2026-05-23 — Phase 1 Foundation (Initial Setup)

**Was generiert wurde (mit Claude):**

- `gradle/libs.versions.toml` — Version Catalog basierend auf `HIUNI_LIBRARIES.md`
- `build.gradle.kts` (root + app) — Plugins, Compose, Hilt, KSP, Java 17
- `app/src/main/AndroidManifest.xml` — Application-Class, Permissions, FileProvider
- `app/src/main/java/de/transio/hiuni/core/**` — Theme, Database-Setup, OkHttp-Provider, SettingsDataStore, CredentialsManager (Self-Healing aus v1-Wissen, nicht Copy-Paste), NotificationScheduler-Stub
- `app/src/main/java/de/transio/hiuni/di/**` — DatabaseModule, NetworkModule, DataStoreModule
- `app/src/main/java/de/transio/hiuni/navigation/**` — Destinations, AppNavGraph
- `app/src/main/java/de/transio/hiuni/ui/responsive/**` — Adaptive Scaffold (3 Layouts)
- `app/src/main/java/de/transio/hiuni/feature/**` — Stub-Screens für Home/Calendar/Mensa/Movies/Bib/Email/Settings/About + ViewModels. Calendar mit echtem `CustomEventEntity`/`CustomEventDao`/`CalendarRepository`
- `docs/adr/0001` bis `0007` — Architecture Decision Records
- `README.md`, `CHANGELOG.md`

**Verifikation:**
- `./gradlew assembleDebug` — grün
- `./gradlew lintDebug` — grün
- Build-Output: `app/build/outputs/apk/debug/app-debug.apk`

**Bekannte AI-spezifische Stolpersteine fürs Team:**

- KSP1 ist absichtlich gewählt (`ksp.useKSP2=false` in `gradle.properties`). Wenn jemand das auf KSP2 setzt, brechen Room-Annotation-Processing — siehe ADR-0007.
- `core/security/CredentialsManager.kt` hat ein Self-Healing-Reset-Pattern. Das funktioniert in der Theorie — vor Phase-2-Email-Feature muss es auf einem echten Gerät mit verschiedenen OEMs getestet werden.
- Stub-Screens haben absichtlich keine Logik. Phase 2 ersetzt die Inhalte mit echten Implementationen.

### 2026-05-23 — Design-Handoff aus Claude Design (Uni Hi.html)

**Was übernommen wurde:**

- Design-System (Farben, Typography, Spacing, Shapes) aus dem HTML/CSS-Mock in unser Compose-Theme übersetzt
- OKLCH-Farbwerte (Indigo h=265, Amber h=72, Greens/Reds/Purples) als approximierte sRGB-Konstanten in `core/design/Color.kt`
- Semantische Farb-Palette (`HiUniSemanticColors`) als CompositionLocal für Status-Farben (Amber/Green/Red/Purple)
- Typography-Skala (Plus Jakarta Sans-Style mit ExtraBold-Headlines), aktuell mit `FontFamily.SansSerif` bis Plus Jakarta Sans als TTF gebundled wird
- Corner-Radii (`HiUniRadii`): tile=14, card=18, big=24
- `feature/home/ui/HomeScreen.kt` komplett neu: Header mit Greeting + Avatar + Bell, "Nächste Vorlesung"-Banner, 2x2 Quick-Access-Grid (Mensa/Bib/Mails/Aufgaben), "Heute"-Lessons mit Course-Color-Stripes, "Uni Kino" Horizontal-Scroll, Offene-Aufgaben-Preview, Neuigkeiten-Cards
- `AdaptiveScaffold` mit Pillow-Indicator-BottomNav (primaryContainer-Background, primary-Tint)

**Was nicht übernommen wurde (out-of-scope für unsere App):**

- Die 17 Mock-Screens (Klausuren, Sport, Lerngruppen, Noten, Push-Center, Campus-Plan, Mensa-Card-Reader, etc.). Unsere App startete mit 8 Destinations (Home, Calendar, Mensa, Movies, Bib, Email, Settings, About).
- In-App Tweaks-Panel (Akzent-Hue, Begrüßung, Dark Mode). Settings in unserer App laufen über `feature/settings`.
- Mock-Daten im Home (Lineare Algebra, VWL, Uni Kino Filme). Phase 2 ersetzt sie mit echten Repos.

**Build-Status:** `./gradlew assembleDebug lintDebug` grün nach der UI-Übernahme.

### 2026-05-23 — Phase 2.1 Calendar (CRUD + Notifications + Tests)

**Was generiert wurde (mit Claude):**

- `feature/calendar/CalendarUiState.kt` + `CalendarViewModel.kt`: 5-Quell-Combine (view-mode, selected-date, eventsFlow via flatMapLatest, editing, sheet-open). Range-Berechnung pro View-Mode. Notification-Scheduling auf Insert/Update + Cancel bei Delete.
- `feature/calendar/ui/CalendarViews.kt`: ListView (grouped by day), DayView (Tages-Agenda), WeekView (5-Spalten Mo-Fr-Strip mit Day-Selektion), plus gemeinsame EventCard mit Source-Kind-Accent-Farbe (USER=primary, MENSA_PIN=amber, MOVIE_PIN=purple)
- `feature/calendar/ui/AddEditEventSheet.kt`: ModalBottomSheet mit OutlinedTextFields, AssistChips für Date/Time-Picker-Launcher, FilterChip-Reihe für Reminder-Minuten, Delete-Confirmation-Dialog
- `feature/calendar/ui/CalendarScreen.kt`: Scaffold + Extended-FAB + SegmentedButton-Switcher + AnimatedContent
- `core/notifications/NotificationReceiver.kt`: NotificationCompat.Builder mit Permission-Check (Android 13+), PendingIntent zur MainActivity
- `app/src/test/.../CalendarRepositoryImplTest.kt`: 4 Tests (observeRange, insert, update, delete) mit MockK + Turbine als Referenz-Pattern für Phase-2-Features

**Was reviewt + nachjustiert wurde:**

- Initial generierte ich die Day-View als Hour-Grid (8-22 mit Position-Calc). Habe das im Review-Schritt auf eine simple Agenda-Liste reduziert — saubere Implementation kommt in Phase 4 Polish, jetzt zählt funktional > visuell.
- Range-Fenster der List-View: 6 Monate statt 12, weil Repo-`observeAll()` mit zu vielen Events auf Cold Devices stuttert. Pragmatisches Limit.

**Verifikation:**
- `./gradlew assembleDebug test` — grün
- 4/4 Unit-Tests in `CalendarRepositoryImplTest` passen
- `Icons.Outlined.Article` Deprecation behoben (→ `Icons.AutoMirrored.Outlined.Article`)

### 2026-05-23 — Phase 2.2 Mensa (STW-ON API + Pin-to-Calendar)

**Was generiert wurde (mit Claude):**

- `feature/mensa/data/MealEntity.kt` mit Composite-Key + Indices
- `MealDao.kt` mit `observeForDate`/`observeRange`/`observeAvailableDates`/`replaceWindow`(transactional)
- `MensaDtos.kt` + `MensaApiService.kt` für STW-ON API
- `MensaRepository.kt` mit Settings-DataStore-Verkettung über `flatMapLatest`
- `MensaViewModel.kt` mit 5-Quell-Combine + Pin-to-Calendar
- `MensaScreen.kt` mit DayStrip + FilterChips + Meal-Cards + Pull-to-Refresh
- Migration 1→2 + AppDatabase v2 + DatabaseModule erweitert
- `MensaDtosTest.kt` (4 Tests, davon einer mit echtem STW-ON-Sample)

**Was im Review nachjustiert wurde (wichtig fürs Team):**

- **Erste Iteration nahm `prices` (Plural) als Number**. Realer STW-ON-Endpoint liefert `price` (Singular) mit String-Werten ("2.50" mit Punkt-Separator). Refactored nach Live-API-Sample-Fetch.
- **Erste Iteration baute Tags aus `notes`-Array**. Real ist es `tags.{categories,allergens,additives,special}` als Liste strukturierter Objekte. Refactored.
- **Kategorie kommt nicht direkt aus dem JSON** — abgeleitet aus `lane.name` (z.B. "Essen 1") + `time` (noon/evening → Mittag/Abend-Prefix). Bei Mittag steht nur die Lane, bei Abend "Abend · Lane".
- **Location-ID 150** ist die richtige für „Mensa Uni Hildesheim" (Universitätsplatz 1) — verifiziert via Live-Call auf `/v1/location`.

**Verifikation:**
- `./gradlew assembleDebug test` — grün
- Tests parsen das echte STW-ON-Sample korrekt
- App startet, MensaScreen erreichbar via Bottom-Nav

### 2026-05-24 — Phase 2.4 Settings + 2.3 Home Wire-Up

**Was generiert wurde (mit Claude):**

- `feature/settings/data/MensaLocation.kt` — hardcoded Hildesheim-Locations (verifiziert via `/v1/location` Live-Call)
- `feature/settings/SettingsUiState.kt` mit `CredentialsDraft` Substate
- `feature/settings/SettingsViewModel.kt` — combine über alle DataStore-Flows + Credentials-Status + Draft + Snackbar-Message, Bump-Counter um nach Credentials-Save den `hasCredentials`-Wert neu zu beziehen
- `feature/settings/ui/SettingsScreen.kt` — sectioned Cards mit Icon-Badge, LocationRow als Radio-style, ChipRow für Pillow-Picker (FlowRow), CredentialsCard mit OutlinedTextField + Password-Toggle
- `feature/home/HomeUiState.kt` + `HomeViewModel.kt` — combine über CalendarRepo + MensaRepo + Settings, isMensaOpenNow lokal berechnet
- `feature/home/ui/HomeScreen.kt` Mock-Daten durch State-Bindings ersetzt: nextEvent-Banner mit Live-Countdown, Mensa-Subtitle zeigt Meal-Count + Open-Status

**Was im Review nachjustiert wurde:**

- `import androidx.compose.runtime.getValue` war im Foundation-HomeScreen nicht da (Mock-Daten brauchten kein State-Delegate). Beim Wire-Up hinzugefügt — sonst kaskadieren Type-Inference-Errors.
- `FlowRow` ist `@ExperimentalLayoutApi`. Opt-in pro Composable statt global.
- Credentials-Diagnose-Text aus v1 wiederverwendet (`CredentialsManager.diagnose()`) für Error-Snackbars wenn AES-256-Keychain crasht.

**Verifikation:**
- `./gradlew assembleDebug test` — grün, alle 15 Tests passen
- Settings-Screen erreichbar via Bottom-Nav, Mensa-Standort-Wechsel triggert MensaScreen-Reload
- HomeScreen zeigt jetzt echte Daten aus Calendar + Mensa
