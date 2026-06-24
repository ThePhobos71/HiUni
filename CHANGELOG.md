# Changelog

Format orientiert an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/).

## [Unreleased]

### Push-Center: Sync-Hooks für Mail/LSF/Bib (2026-06-26)

- `EmailRepository.refresh(...)`: nach `dao.upsert(toInsert)` wird ein `MAIL`-Eintrag ins Push-Center geschrieben, sobald (a) es nicht der initiale Inbox-Pull ist (`existingByUid` nicht leer) und (b) mindestens eine der neuen Mails ungelesen ist. Titel adaptiert (1 vs. n), Body zeigt Absender + Subject der ersten ungelesenen.
- `LsfSyncWorker`: beide AuthFailure-Branches (MyCourses + Stundenplan) loggen jetzt einen `SYSTEM`-Eintrag „LSF-Login abgelaufen" mit Body-Hinweis auf den Settings-Re-Login. Repository wird über die existierende `@HiltWorker`-Constructor-Injection eingehängt.
- `BibRepository.book(...)` / `cancel(...)`: nach erfolgreichem `"ok"`-Response schreibt jede Operation einen `BIB`-Eintrag mit Raum-Label und einer kompakten „Heute/Morgen/EEE d.M. · HH:mm–HH:mm"-Zeile.
- `SettingsScreen`: neue Sektion „Push-Center" mit Test-Mitteilung-Button (`SYSTEM`-Eintrag, Snackbar-Bestätigung), damit man die Bell-Verkabelung ohne echten Reminder validieren kann.
- `NotificationsScreen` Empty-State: erklärt jetzt explizit, dass Reminder hier auch landen, wenn die System-Benachrichtigung verpasst wurde, plus Tipp auf den Test-Button.

### Push-Center: Notifications-Log + Bell-Wire-Up (2026-06-26)

- `core/notifications/data/`: neue `NotificationLogEntity` + DAO + Repository. Tabelle `notifications` (id, kind, title, body, firedAt, isRead, refKey). `NotificationKind`-Enum mit den Quellen aus FEATURES.md (EVENT/EXAM/GRADE/MAIL/MENSA/MOVIE/SPORT/BIB/SYSTEM), als TEXT via `Converters` gespeichert.
- DB-Migration v20→v21 (`schemas/21.json`): CREATE TABLE + Index auf `(isRead, firedAt)` und `firedAt`.
- `NotificationReceiver` ist jetzt `@AndroidEntryPoint` und schreibt jeden ausgelösten Reminder ins Push-Center-Log — unabhängig davon, ob die System-Notification durchkommt (POST_NOTIFICATIONS verweigert → User sieht es trotzdem im Center).
- `feature/notifications/ui/NotificationsScreen.kt`: gruppiert nach Heute/Gestern/Älter, Typ-Icon mit semantischer Farbe pro Kind, Unread-Dot, Tap → gelesen, X → entfernen, „Alle gelesen"-Action in der Top-Bar. Pull-Pattern matched ProfileScreen.
- Home: Bell oben rechts navigiert jetzt zu `Destination.Notifications` (statt Settings), `state.unreadNotifications` füttert das rote Badge auf der Glocke. `HomeViewModel` injiziert das neue Repository und beobachtet `observeUnreadCount()`.
- `NotificationsViewModel` räumt beim Öffnen Einträge älter als 30 Tage auf (`prune(...)`), damit die DB nicht beliebig wächst.

### LSF-Auto-Sync + Todos × Kurse + Bounce-Back-Animation (2026-06-26)

- `core/sync/LsfSyncWorker.kt` + `LsfSyncScheduler.kt`: `@HiltWorker` synct MyCourses → 400ms Throttle → Stundenplan. Auth-Fehler → `Result.failure()` (kein Retry-Hammer), Netzfehler → `Result.retry()` (exponentielle Backoff ab 30s). `last_lsf_sync_epoch` wird nach Erfolg persistiert.
- `HiUniApplication` implementiert `Configuration.Provider` + `HiltWorkerFactory`. On-Start liest es das gespeicherte Intervall via `runBlocking` und schedult Periodic-Work (`NetworkType.CONNECTED`, `ExistingPeriodicWorkPolicy.UPDATE`).
- `CasLoginViewModel` beobachtet `CasSession.state` und triggert `scheduler.triggerNow()` beim Übergang von „nicht authentifiziert" → „authentifiziert" — d.h. nach erstem Login UND nach Re-Auth, ohne Spurious-Fire beim App-Start.
- Settings: neue Sektion „LSF-Auto-Sync" mit ChipRow `6h / 12h / 24h / Aus`, „Zuletzt: vor X Std"-Label und „Jetzt synchronisieren"-Button. Default 12h.
- Auth-Fail-Erkennung im Worker matched aktuell auf die deutschen Error-Messages der Repos („CAS-Login abgelaufen…", „Login erforderlich"). Folgepass: typisierte `AuthRequiredException` in `core.common`.

### Todos × Kurse + Bounce-Back-Animation (2026-06-26)

- `TodoEntity.courseId: String?` + Migration v19→v20 (`ALTER TABLE todos ADD COLUMN courseId`, neuer Index). Kein FK-Constraint, damit gelöschte Kurse die Aufgabe nicht löschen — beim Render zeigt eine Pille „Kurs entfernt".
- `TodosViewModel` injiziert `CourseRepository`, exposed `courses` + `coursesById` im UiState; `save(...)` nimmt jetzt `courseId: String?`.
- `AddEditTodoSheet`: neue „Kurs"-AssistChip öffnet eigenes `CoursePickerSheet` (Kein-Kurs-Option + Kurse nach Semester gruppiert, mit Farb-Dot vorm Namen). Modulkürzel bevorzugt vor Modulnamen.
- `courseColorFor(course: CourseEntity)`-Helper in `feature/calendar/ui/CourseColor.kt` — Kurs- und Kalender-Events teilen sich die gleiche Farbe über `lsfId`/`name`-Hash.
- Kurs-Pille auf `TodosScreen.TodoCard` (Modulkürzel mit Kurs-Farbe als bg/fg) und Home-`TodoPreviewRow`. HomeViewModel injiziert `CourseRepository` und exposed `openTodosCoursesById`.
- `ReorderableColumn` bekommt Bounce-Back beim Loslassen zurück: separates `Animatable<Float>` für die visuelle Translation federt mit `Spring.DampingRatioMediumBouncy` + `StiffnessMediumLow` auf 0 zurück; die Swap-Mathematik läuft synchron auf einem Float-State.

### Home-DnD + Long-Press-Add + Bib-Lageplan-Tap + Todos-Feature (2026-06-26)

- `feature/home/ui/ReorderableColumn.kt`: Long-Press + Drag-Gesture-Helper für vertikale Reorder im Home — Item wird visuell hervorgehoben (Scale 1.02, Alpha 0.95, zIndex 1), Nachbar-Swap bei halber Item-Höhe + Spacing, Haptic-Feedback bei Start und jedem Swap, Commit erst beim Loslassen via DataStore. Tile-Reorder bleibt vorerst Settings-only (2D-Grid komplizierter).
- `HomeSectionsViewModel.setOrder(ids)` + `QuickAccessViewModel.setOrder(ids)`: atomic order replacement statt N × move-Call beim DnD-Commit.
- Kalender Long-Press auf Tag öffnet `AddEditEventSheet` mit vorausgewähltem Datum: `CalendarViewModel.openAddOnDate(date)`, `CalendarUiState.initialDateForAdd`, `AddEditEventSheet(initialDate = ...)`. Default-Zeit: 09:00 für andere Tage, jetzt+1h für heute. `DayPickerCell`, `WeekDayColumn`-Header und `MonthCell` nutzen `combinedClickable`.
- `BibFloorplan(onRoomClick = ...)`: Räume F101–F105 sind direkt am Lageplan antippbar und springen zur Buchungs-Sicht. Hint-Text adaptiert ("Tippe einen Raum an, um ihn zu buchen").
- `feature/todos`: vollständiges Aufgaben-Feature — `TodoEntity` (Room, sortIndex + dueDate + isDone), DAO, Repository, ViewModel, `TodosScreen` mit Add/Edit-Sheet + Swipe-to-Delete + Checkbox-Toggle, Empty-State. DB-Migration auf v19 + schemas/19.json. `OpenTodosSection` auf Home zeigt jetzt die ersten 3 echten offenen Todos statt Mock-Daten; Schnellzugriff-Kachel „Aufgaben" zeigt offene Anzahl und navigiert zu `Destination.Todos` statt zum Kalender.

### Startseite konfigurierbar + Bib-Copy + Display-Name (2026-06-26)

- `feature/home/HomeSection.kt` + `HomeSectionsViewModel.kt` + `core/datastore` Key `home_sections_order`: 5 Sektionen (Schnellzugriff, Heute, Uni-Kino, Aufgaben, Neuigkeiten) per Hand ein-/ausblendbar und sortierbar, Default = nur die zwei realen (Schnellzugriff + Filme), Mock-Sektionen (Aufgaben/Neuigkeiten) per Default aus
- `feature/home/QuickAccessTile.kt` + `QuickAccessViewModel.kt` + Key `home_quick_access_order`: Schnellzugriff-Kacheln dynamisch — 7 Kandidaten (Mensa, Bib, Mails, Aufgaben, Kurse, Filme, Mensa-Karte), Default = die jetzigen 4, beliebige Reihenfolge, `chunked(2)` Layout mit Spacer für ungerade Anzahl
- `feature/settings/ui/HomeSettingsScreen.kt` + `QuickAccessSettingsScreen.kt`: Up/Down/Remove/Add pro Eintrag, Reset, Description als Untertitel — Pattern aus `NavSettingsScreen` adaptiert, ohne Min/Max-Constraint
- `HomeScreen.NextLessonBanner` Tonung: voll-`primary`-gefüllter Banner → `primaryContainer` + `onSurface`-Titel, kleinere Padding/Typo (`titleLarge` → `titleMedium`) — weniger laut, gleicher Tap-Target
- `DisplayNameCard` + `DisplayNameViewModel.hasMultipleFirstNames`: „Alle Vornamen"-Option nur sichtbar wenn `vorname` ≥ 2 Whitespace-Tokens hat — kein toter Eintrag für User mit einem Vornamen
- Bib-Copy gestrafft (`BibScreen.kt` + `LibraryBookingScreen.kt` + `BibViewModel.kt`): Slot-Label `"zu"` → `"geschl."`, Legende `"Deine"` → `"Deine Buchung"`, Raum-Subtitle zeigt Equipment („Bildschirm" / „Whiteboard"), Hint nennt 2h-Limit, Confirmation erwähnt Kalender-Eintrag, Empty-State actionable mit Pull-to-Refresh-Hinweis, Snackbar `"Buchung gelöscht"` → `"Buchung storniert"`

### Phase 2.6 Movies (2026-05-24)

- `feature/movies/data/MovieEntity.kt` + `MovieDao` mit Filtern für upcoming / range / by-id
- `MovieScraper`: unifilm.de Hildesheim scraping mit OkHttp+Jsoup, `data-id`/`data-sid` Cross-Reference zwischen `li.film` und `div.film-showcase` (v1-Pattern), Filmdaten-Heuristik aus `ul.film-info-filmdaten li` (R:/FSK/Land/Min./Genre)
- Date-Parser akzeptiert volle Daten ("19.05.2026") + partielle ("22.06.") mit Fallback aufs aktuelle Jahr
- `MoviesRepository` mit refresh + replaceAll (atomic delete + insert), Pin-to-Calendar als `CustomEventEntity` mit `SOURCE_MOVIE_PIN` + 30-Min-Reminder + Duration aus Scraper-Filmdaten
- `MoviesScreen`: Featured-Film-Hero mit Coil-AsyncImage Poster, Genre-Badge, Pin-Button; weiteres Programm als kompakte Rows mit Poster-Thumbnail
- Migration 2→3 für `movies`-Tabelle, AppDatabase v3, schemas/3.json
- 4 Tests in `MovieScraperTest` (data-id/sid Cross-Ref, showcase fehlt, isPast-Markierung, partial-date-Fallback)

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
