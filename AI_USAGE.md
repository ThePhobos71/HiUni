# AI Usage Disclosure

> Lebende Datei. Wird pro relevantem Commit aktualisiert.

> Ehrlichkeits-Hinweis: Die Einträge bis „Phase 2.4 Settings + 2.3 Home Wire-Up" (24.05.2026) wurden zeitnah zum jeweiligen Commit geschrieben. Die Sessions ab Ende Mai sind nachträglich rekonstruiert, weil die lebende Datei zwischenzeitlich nicht gepflegt wurde. Quellen der Rekonstruktion: Git-Historie (`git log --date=short`), `CHANGELOG.md`, die Prozess-Logs unter `docs/process/` und `.superpowers/sdd/` sowie die Design-Specs und Pläne unter `docs/superpowers/`. Fakten stammen aus diesen Belegen; wo ein Detail nicht mehr sicher rekonstruierbar war, ist der Eintrag bewusst allgemeiner gehalten.

## Tools

- **Claude Code (Anthropic)** — primärer AI-Assistent für Code-Generierung, Architektur-Review, Doku-Drafts. Modell: Opus 4.x (1M context).
- **Superpowers-Skills** — wiederverwendbare Arbeitsanweisungen, die den Agenten vor dem Coden disziplinieren: `brainstorming` (Konzept-Dialog vor Code), `writing-plans` (Spec → Umsetzungsplan mit TDD-Dreischritt pro Task), `test-driven-development` und `systematic-debugging`. Ab dem Reviews-Experiment (Ende Juni) im Einsatz.
- **Ultracode-Modus** — Multi-Agent-Orchestrierung über getrennte Git-Worktrees, sodass mehrere Agenten parallel an unabhängigen Tasks arbeiten. Gemergt wird immer von Hand.
- **`/code-review`-Skill** — automatisierter Review-Lauf pro Branch vor dem Merge, mit verschiedenen Effort-Levels.

## Workflow-Prinzip

Wir „vibecoden mit Plan": Architektur-Entscheidungen treffen wir manuell (ADRs in `docs/adr/`), AI generiert daraus Code-Skelette. Jeder generierte Code wird gelesen, getestet und vom Team gegengezeichnet bevor er gemergt wird. Bei Pair-Defense kann jeder Teamteil jedes Modul erklären.

Der Workflow ist über das Projekt gereift: Für größere Features tritt vor die Code-Generierung eine Planungsphase mit den Superpowers-Skills (`brainstorming` liefert eine Spec, die WIR entscheiden; `writing-plans` faltet sie mechanisch in einen Task-Plan auf). Bei parallelisierbaren Task-Listen läuft die Umsetzung im Ultracode-Modus über getrennte Worktrees, gefolgt von einem `/code-review`-Gate pro Branch vor dem Merge. Die Trennung bleibt: Spec und Architektur sind menschlich, der Plan und der generierte Code sind die mechanische Ausführung.

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

> Die folgenden Einträge sind nachträglich rekonstruiert (siehe Ehrlichkeits-Hinweis oben).

### 2026-05-26 — Bibliothek: Gruppenraum-Buchung in-App

**Was generiert wurde (mit Claude):**

- `feature/bib/`: Buchungs-Flow direkt in der App statt Weiterleitung in den Browser — `LibraryBookingScreen` mit Slot-Picker (max. 2h pro Slot, 1 Buchung pro Tag), `BibFloorplan` als antippbarer Lageplan der Räume F101–F105, `BibViewModel` + `BibRepository.book(...)`/`cancel(...)` gegen das ubwww-Gruppenraumbuchungs-System, Pull-to-Refresh
- `BibSession.kt`: CAS-Login-Handling für das ubwww-System (Cookie-Extraktion aus den `Set-Cookie`-Headern)
- Scraper- und ViewModel-Tests fürs Bib-Modul

**Was im Review nachjustiert wurde:**

- Slot-Grid und Day-Chips mehrfach vereinheitlicht (feste Breite, uniforme 56/78dp-Höhen), damit die Buchungsansicht nicht springt.

**AI-Incident (wichtig, dokumentiert):**

Beim Bau fiel im echten ubwww-System (`ubwww.uni-hildesheim.de/gruppenraumbuchung/`) ein Session-Fixation-Bug auf: Nach dem CAS-Login wird die `PHPSESSID` rotiert, die alte anonyme Pre-Auth-Session aber serverseitig nicht invalidiert. Buchungen mit der Anon-Session werden akzeptiert, hängen an keiner User-Identity und umgehen das „1 Buchung pro Tag"-Limit (verwaiste, für User nicht stornierbare Reservierungen). Wir hatten den Bug in unserer App unbeabsichtigt repliziert: `BibSession.kt` nahm mit `firstNotNullOfOrNull` die **erste** statt der letzten `PHPSESSID` aus den Headern und landete damit auf der anonymen Session. Gemeinsam mit der KI analysiert, mit curl und DevTools reproduziert und dokumentiert in `docs/UBWWW_BUG_SESSION_FIXATION.md`. Fix: `BibSession.kt` nimmt jetzt die **letzte** `Set-Cookie`-Session (`mapNotNull{...}.lastOrNull()`), also die mit gebundener User-Identity, und verhält sich damit wie ein normaler Browser (RFC 6265 last-wins). Meldung an die UB-IT steht noch aus.

**Verifikation:**
- `./gradlew assembleDebug test` — grün
- Buchung und Stornierung gegen das echte System durchgespielt; nach dem Fix tauchen eigene Buchungen korrekt unter „Meine Buchungen" auf

### 2026-05-31 — Scraper-Welle: Kino, Bib-Auslastung, Mail + LSF/CAS-SSO

**Was generiert wurde (mit Claude):**

- **Movies (Uni Kino):** `MovieScraper` gegen unifilm.de Hildesheim (OkHttp + Jsoup, `data-id`/`data-sid`-Cross-Reference), `MoviesRepository` mit atomarem `replaceAll`, `MoviesScreen` mit Featured-Hero, Pin-to-Calendar; später TMDB-Enrichment für bessere Poster
- **Bib-Auslastung:** ubwww-Scraper mit Auslastungsbalken und dynamischen Öffnungszeiten
- **Mail:** `EmailRepository` über Jakarta Mail (IMAP-Empfang, SMTP-Versand über `mail.uni-hildesheim.de`), HTML-Substrat, Attachments; verschlüsselte lokale Ablage über SQLCipher
- **Mensa-Karte:** Intercard-NFC-Guthaben-Auslesen (STW-NFC ohne öffentliche Spec, daher lokales Guthaben-Tracking)
- **LSF/CAS-SSO:** Hybrid-Login-Framework, Service-Ticket-Acquisition und LSF-Auth End-to-End; User-Profil aus der CAS-Attribute-Page

**Was im Review nachjustiert wurde:**

- IMAP-Ordnernamen unterscheiden sich pro Server (`Sent` vs. `Gesendete Objekte` vs. `INBOX.Sent`): statt Hardcoding jetzt SPECIAL-USE-Discovery.
- Movie-Datumsparser akzeptiert volle („19.05.2026") und partielle Daten („22.06.") mit Jahres-Fallback.

**AI-Incident (nur per Log-Analyse gefunden):**

Das CAS-SSO lief zunächst nicht End-to-End: Der TGC/CAS-Cookie ist an den User-Agent des WebView gebunden, der ihn gesetzt hat. Nutzt man ihn anschließend mit einem abweichenden User-Agent für die Service-Ticket-Acquisition, verwirft CAS ihn stillschweigend. Das steht in keiner öffentlichen Doku und war nur durch Analyse der Redirect-/Cookie-Logs zu finden. Nach Angleichen des User-Agents lief die ST-Acquisition und damit der LSF-Login durch.

**Verifikation:**
- `./gradlew assembleDebug test` — grün, R8-Release-Build zusätzlich geprüft
- Login gegen echtes CAS/LSF, Mail-Roundtrip (Empfang + Versand) auf echtem Account getestet

### 2026-06-28 — Tablet-Layouts, globale Suche, App-Icon-System, Mensa- und Mail-Polish

**Was generiert wurde (mit Claude):**

- **Tablet:** `AdaptiveContentBox` (Content-Cap + Zentrierung im Rail/Drawer-Modus), `LocalWindowSizeClass`, opt-out `FullWidthContent`; Multi-Pane-Layouts für Email (40/60), Movies (List/Detail), 2-Spalten-Grids für Mensa/Sport/Kurse/Klausuren/Todos; Tablet-`NavigationRail` mit umordbaren Tabs statt alphabetischem Permanent-Drawer
- **Globale Suche:** neues `core/search/`-Modul, `GlobalSearchRepository` sucht parallel über sechs Quellen (Mail, Termine, Kurse, Klausuren, Mensa-Gerichte, Sport) mit AND-Token-Match, `GlobalSearchViewModel` mit 200ms-Debounce, `SearchTile` im Home-Header
- **App-Icon-System:** vier Activity-Aliases (default/dark/classic/studi) + `AppIconManager.setVariant()` über `PackageManager.setComponentEnabledSetting`, `AppIconCard` mit Vorschauen; Mortarboard-Icon plus Semester-Unlock über `core/common/Semester.kt`
- **Mensa-Polish:** `MealDetailSheet` mit Nährwerten/Zusatzstoffen/Besonderheiten, `WeekStrip` auf 4 Wochen, Öffnungszeiten live aus der STW-API, zusätzliche Diet-Filter
- **Mail:** konfigurierbare Swipe-Gesten, optionaler Fingerabdruck-Schutz für den Mail-Tab (`BiometricPrompt`, `MainActivity` als `FragmentActivity`), „Mails nur lokal löschen"-Modus

**Was im Review nachjustiert wurde:**

- Das erste App-Icon-Foreground zeigte das offizielle Uni-Hildesheim-Wahrzeichen. Das ist Brand-Aneignung (HiUni ist kein offizielles Uni-Projekt) und wurde komplett neu als eigenständiges Design gezeichnet. Dazu mehrere Icon-Iterationen (Verzeichnis `mipmap-anydpi-v26/`, Circle-Mask-Clipping, `painterResource()`-Crash auf Adaptive-XMLs).

**AI-Incident (Schema-Drift, nur am realen Endpoint gefunden):**

Die STW-API hatte `special_tags` von einem Top-Level-Feld nach `tags.special` verschoben. Kein Crash, aber die Diet-Filter-Chips waren für alle Gerichte plötzlich leer. Die KI konnte das nicht von selbst finden, das alte Schema wirkte für sie konsistent. Gefangen im Pair-QA-Pass auf der Mensa-Seite („die Filter zeigen heute komisch wenig") plus kurzem `curl`. Fix: Parser liest aus `tags.special`.

**Verifikation:**
- `./gradlew assembleDebug test` — grün
- Tablet-Layouts auf Galaxy Tab und Emulator-Pool durchgeklickt; Icon-Wechsel auf echtem Gerät geprüft

### 2026-06-29 — P2P-Mensa-Reviews (Experiment, NICHT gemergt) + Learnweb

**Ehrlichkeits-Vermerk:** Dieses Feature war ein Experiment und ist **nie in `main` gemergt** worden. Der Branch `feature/mensa-reviews` ist inzwischen gelöscht. Es bleibt hier dokumentiert, weil hier erstmals der volle Superpowers-Workflow zum Einsatz kam und die Prozess-Logs (`docs/superpowers/`, `.superpowers/sdd/`) den Lauf belegen.

**Workflow (erstmals voll durchgezogen):**

- `brainstorming` → 654-Zeilen-Design-Spec (`docs/superpowers/specs/2026-06-28-mensa-p2p-reviews-design.md`), über einen Nachmittag in fünf Commits iteriert, inklusive Abschnitt „Resolved points (decided in brainstorming)" und dem Streichen einer ganzen Phase (LAN-Sync via mDNS), nachdem die Recherche zeigte, dass Eduroam Multicast blockt
- `writing-plans` → 3344-Zeilen-Umsetzungsplan mit 7 Phasen / 34 Tasks, jeder Task als TDD-Dreischritt (failing test, Implementation, Commit)
- `subagent-driven-development` → Ausführungs-Log in `.superpowers/sdd/progress.md`, 34 Tasks Task-für-Task mit Opus-Review-Verdicts pro Task und Whole-Branch-Reviews nach jeder Phase

**Was generiert wurde (mit Claude):**

- `:shared-events`-Modul: Event-Datenklassen + Canonical-Form, Ed25519-Signaturen über Tink
- `ReviewRepository` (Aggregation, Submit, Retract), `MyKeyManager` mit Android-Keystore-Wrapping, `EventValidator` (Signatur/Trust/Spam), recipeHash mit Nährwert-Fingerprint
- `hiuni-relay` als separates Ktor-Modul: SQLite-EventStore, WebSocket `/sync` mit Hello/Event/Events-Protokoll, Distroless-Dockerfile
- Web-of-Trust: QR- und Mail-Intro-Flow (Ed25519), Master-WoT-/Federation-Spec als Design-Dokument

**AI-Incident (durch TDD gefangen):**

Für `wouldOrderAgainPct` generierte die KI `(positive * 100) / total`, also Integer-Division, die abschneidet statt zu runden (`0.99` → `0`, `1.50` → `1`). Erst beim Schreiben der Tests aufgefallen. Fix: `roundToInt` statt `.toInt()`-Truncation, plus Rounding-Boundary-Test. Seitdem galt für dieses Feature konsequent Test-First.

**Was parallel lief (und in `main` blieb):**

- **Learnweb (Moodle):** CAS-SSO-Login, Kurs-Liste (`LearnwebClient`/`LearnwebScraper`), Assignment-Deadlines aus dem Moodle-Kalender mit Spiegelung als `CustomEventEntity` und eigenem Reminder-Scheduler (3 Tage / 1 Tag / 2h), offizieller iCal-Export (`LearnwebICalParser`) als Zweitquelle. Neue Tabellen `learnweb_courses`/`learnweb_assignments`.

**Verifikation:**
- Reviews-Branch: 15 Tests nach Phase 1, alle Phasen mit grünen Tests und Opus-Whole-Branch-Reviews abgeschlossen
- Learnweb (in `main`): `LearnwebICalParserTest` + `./gradlew assembleDebug test` grün

### 2026-07-13 — Fünf Glance-Widgets an einem Tag (Ultracode)

**Was generiert wurde (mit Claude):**

- Glance-Foundation (1.1.1) + `WidgetHiltEntryPoint` für Repository-Zugriff aus Non-Hilt-Komponenten, widget-eigene DayNight-`ColorProvider`-Palette
- Fünf Widgets: `TodoWidget` (offene Todos mit Toggle-Done-Action), `StundenplanWidget` (heutiger Tagesplan), `SchedulaWeekWidget` (7-Tage-Agenda), `MensaWidget` (heutiger Speiseplan, nach Frühstück/Mittag/Abend gruppiert), `ExamCountdownWidget` (nächste Klausur mit Ampel-Farbe)
- Design-Kit `feature/widgets/common/`: `WidgetTheme` + `WidgetPalette`, `WidgetSurface`, `WidgetHeader`, `WidgetEmpty` plus zehn Vector-Icons. Rund 600 Zeilen dupliziertes Layout raus
- `WidgetDeepLinkController`: MainActivity fängt Widget-Intents ab, `AppNavGraph` routet auf den Ziel-Tab

**Ultracode-Modus:** Mehrere Agenten arbeiteten parallel in getrennten Git-Worktrees. Nach Git-Timestamps liegen zwischen Glance-Foundation und letztem Merge rund 63 Minuten mit 19 Commits und ca. 2900 eingefügten Zeilen: Foundation sequenziell, drei der fünf Widgets in eigenen Worktrees (die Hauptsession baute zwei direkt), danach das gemeinsame Design-Kit plus drei weitere Worktrees, die die fertigen Widgets darauf umstellten (deren Merges haben negative Netto-Diffs). Gemergt wurde durchgehend von Hand, ein Manifest-Konflikt ist in den Merge-Commits dokumentiert. Dazu `/code-review` pro Branch vor dem Merge.

**AI-Incident (hartes Framework-Limit):**

Das `ExamCountdownWidget` sprengte im großen Layout still das Glance-10-Kinder-Limit: `MetaChip` emittierte Image + Spacer + Text als drei Geschwister direkt in die `MetaRow`, im großen Layout mit Datum/Zeit/Raum genau 11 Kinder. Das 11. Element (Raum) wurde stumm abgeschnitten, jeder Render warf eine stille `IllegalArgumentException`. Wegen des fehlenden Crashs blieb das mehrere Tage unentdeckt. Fix (nachgezogen am 17.07.): `MetaChip` in einen eigenen Row-Wrapper gekapselt (zählt als 1 Kind), neu `WidgetLimits` mit dokumentierter Grenze + `capForContainer`-Helper und Test. Lehre: Solche harten Framework-Limits kennt die KI nicht, und ein Design-Kit gehört VOR die Parallelität, nicht danach (die Worktrees lieferten je eigene Farben/Paddings, es folgte ein Vereinheitlichungs-Pass).

**Verifikation:**
- `./gradlew assembleDebug` grün, alle fünf Widgets auf dem Homescreen platziert und resized

### 2026-07-16 — Sicherheits-Härtung, Testsuite, Noten/GPA, Tickle-Server

**Was generiert wurde (mit Claude):**

- **Sicherheit & Tests (Phase 1+2):** Security-Härtung, `!!`-Guards entfernt, Emoji-Cleanup in UI-Strings, FEATURES.md-Abgleich; Testsuite von 140 auf 289 Tests ausgebaut, dabei einen Room-Migrations-Crash gefixt
- **Reminder & Klausuren (Phase 3):** Recurrence-Reminder (planen ihre nächste Occurrence, reschedulen nach Reboot), manuell anlegbare Klausuren, gemeinsame `ReminderDiffEngine` in `core/sync` mit Int-Overflow-Guard
- **Noten/GPA (Phase 5):** `NotenspiegelScraper` gegen den LSF-Notenspiegel inkl. GPA-Berechnung, Offline-Modus mit „Offline – gespeicherte Daten"-Banner
- **Prefetch & Push:** `PrefetchOrchestrator` (gestaffelter Hintergrund-Warmup), Sync-Tickle (FCM weckt Mail-Refresh + Feature-Prefetch), Push bei neu erkannten LSF-Kursen
- **Tickle-Server (`server/`):** schlanker FastAPI-Server, der die App periodisch weckt, damit sie selbst neue Mails holt; kennt nur Device-Tokens, keine Zugangsdaten oder Inhalte. Token-Purge im Loop (`TOKEN_MAX_AGE_DAYS`), In-Memory-Rate-Limit je Client-IP auf `/register`/`/unregister`

**Ehrlichkeits-Vermerk:** Der Tickle-Server ist implementiert und getestet (`/health`-Smoke-Test grün), aber noch nicht produktiv deployed. Der Coolify-Deploy ist vorbereitet (Service-Account-JSON als mehrzeilige Env-Variable statt Datei-Mount), Rate-Limit und Token-Alter wurden erst nachträglich ergänzt.

**AI-Incident (Datenschutz im Test-Fixture):**

Die erste Noten-Test-Fixture enthielt echte Klausurdaten. Umgestellt auf eine synthetische Notenspiegel-Fixture, damit keine realen personenbezogenen Daten im Repo landen.

**Verifikation:**
- `./gradlew assembleDebug test` — grün, 289 Tests
- Server: `/health`-Smoke-Test lokal grün

### 2026-07-17 — Notenspiegel↔Kurs-Matching, Semester-Anker, Learnweb-Kanal

**Was generiert wurde (mit Claude):**

- **Kurs-Noten:** Kurse zeigen die echte Note aus dem Notenspiegel statt „Note steht noch aus". `NotenspiegelScraper` liest die Veranstaltungs-Nr aus dem Veranstaltungslink (`GradeEntity.veranstaltungsNr`, Migration 34→35), `CourseGradeMatcher` verknüpft Note ↔ Kurs primär über `veranstaltungsNr == course.lsfCode`, Fallback normalisierter Titel + Semester. Präzedenz: manuelle `course.grade` > Notenspiegel > keine, `CourseEntity` wird nie überschrieben
- **Semester-Anker fürs Icon:** Das App-Icon-Unlock ankert das erste Semester am frühesten Semester aus Noten + Kursen statt am Installationszeitpunkt (`Semester.parseLabel` + `earliestOf`, neue DAO-Query `findDistinctSemesters`, kein Schema-Bedarf). Der Anker wandert nur nach vorne, nie zurück
- **Learnweb-Kanal:** Learnweb-Reminder laufen jetzt über ein eigenes `LEARNWEB`-Kind (eigener `hiuni_learnweb`-Channel, eigenes Push-Center-Styling, eigener Toggle) statt über das generische `EVENT`-Kind
- **Widget-Fix:** der am 13.07. entdeckte Glance-10-Kinder-Bug im `ExamCountdownWidget` wird hier behoben (`WidgetLimits` + `capForContainer` + Test, siehe Session 13.07.)

**Was im Review nachjustiert wurde:**

- Bei Wiederholungsversuchen im Notenspiegel gewinnt die beste Note (PASSED > höchster Versuch > jüngstes Datum); „steht noch aus" erscheint nur bei echt fehlender Note (angemeldet zählt nicht).

**Verifikation:**
- `./gradlew assembleDebug test` — grün
- Testsuite auf 475 Tests, 0 Failures
