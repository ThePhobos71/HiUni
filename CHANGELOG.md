# Changelog

Format orientiert an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/).

## [Unreleased]

### Widget-Limit-Fix, Learnweb-Kanal, Semester-Anker & Kurs-Noten (2026-07-17)

- Klausur-Countdown-Widget crashte still bei jedem Render: `MetaChip` emittierte Image + Spacer + Text direkt in die `MetaRow` → im großen Layout mit Datum/Zeit/Raum exakt 11 Kinder, Glance erlaubt max. 10. Das 11. Element (Raum) wurde stumm abgeschnitten und jeder Render warf eine `IllegalArgumentException`. Fix: `MetaChip` in einen eigenen Row-Wrapper gekapselt (zählt jetzt als 1 Kind). Neu: `WidgetLimits` mit dokumentierter Grenze + `capForContainer`-Helper für künftige nicht-lazy Listen (+ Test).
- Learnweb-Reminder (3d/1d/2h-Slots) laufen jetzt über das eigene `LEARNWEB`-Kind statt über `EVENT` — eigener `hiuni_learnweb`-Channel, eigenes Push-Center-Styling, eigener Toggle. Kalender-Reminder bleiben `EVENT`; Bestands-Alarme werden beim nächsten Diff-Resync via `FLAG_UPDATE_CURRENT` neu geplant.
- App-Icon-Unlock ankert das erste Semester am frühesten Semester aus Noten + Kursen statt am Installationszeitpunkt (`Semester.parseLabel` + `earliestOf`, aktualisiert nach jedem Grades-/MyCourses-Sync, neue DAO-Query `findDistinctSemesters`, kein Schema-Bedarf). Der Anker wandert nur nach vorne, nie zurück; Install-Semester bleibt Fallback ohne Daten. Damit entspricht der Icon-Fortschritt dem echten Studienstand.
- Kurse zeigen die echte Note aus dem Notenspiegel statt „Note steht noch aus": `NotenspiegelScraper` liest die Veranstaltungs-Nr aus dem Veranstaltungslink (`GradeEntity.veranstaltungsNr`, Migration 34→35), `CourseGradeMatcher` verknüpft Note ↔ Kurs primär über `veranstaltungsNr == course.lsfCode`, Fallback normalisierter Titel + Semester; bei Wiederholungsversuchen gewinnt die beste Note (PASSED > höchster Versuch > jüngstes Datum). Präzedenz: manuelle `course.grade` > Notenspiegel > keine — `CourseEntity` wird nie überschrieben. `GradeStatusCard` nennt dezent die Quelle („Aus dem Notenspiegel"); „steht noch aus" erscheint nur bei echt fehlender Note (angemeldet zählt nicht).
- Testsuite auf 475 Tests, 0 Failures.

### Sicherheit, Tests, Reminder, Noten, Offline & Push (2026-07-16)

#### Sicherheit & Stabilität
- Security-Härtung + `!!`-Guards entfernt, Emoji-Cleanup in UI-Strings, FEATURES.md-Abgleich (Phase 1).
- Testsuite von 140 auf 289 Tests ausgebaut, dabei Room-Migrations-Crash gefixt (Phase 2).

#### Reminder & Klausuren
- Recurrence-Reminder: wiederkehrende Termine planen ihre nächste fällige Occurrence korrekt ein und reschedulen sich nach Reboot/Force-Stop.
- Manuelle Klausuren: Klausurtermine lassen sich von Hand anlegen/bearbeiten, ergänzend zum LSF-Scrape.
- `core/sync`: gemeinsame `ReminderDiffEngine` für Exam-/Learnweb-Reminder (zentralisiertes ID-Schema, Diff/Cancel/Persist) mit echtem Int-Overflow-Guard (skip + Warnung statt stiller ID-Kollision); `WorkerSyncScheduler`-Basis für LSF-/Sport-Scheduler.

#### Noten & Offline
- Noten/GPA via LSF-Notenspiegel-Scraper inkl. GPA-Berechnung; Offline-Modus mit „Offline – gespeicherte Daten"-Banner über allen Screens (Phase 5). Doppel-Padding des Banners überm Screen-Header behoben.

#### Mail-Push & Tickle-Server (`server/`)
- FCM-Mail-Push: schlanker FastAPI-Tickle-Server weckt die App periodisch, damit sie selbst neue Mails holt — Server kennt nur Device-Tokens, keine Zugangsdaten/Inhalte (Phase 3).
- Service-Account-JSON als mehrzeilige Env-Variable (Coolify-Deploy ohne Datei-Mount).
- Server-Hygiene: Token-Purge im Tickle-Loop (`TOKEN_MAX_AGE_DAYS`, Default 60) und In-Memory-Rate-Limit je Client-IP auf `/register` + `/unregister` (`RATE_LIMIT_PER_MINUTE`, Default 10, `429` bei Überschreitung).

#### Prefetch & Sync-Tickle
- `PrefetchOrchestrator`: gestaffelter Hintergrund-Warmup der Feature-Daten statt Per-Screen-Nachladen beim Öffnen.
- Sync-Tickle: FCM weckt Mail-Refresh **plus** Feature-Prefetch; Push-Benachrichtigung bei neu erkannten LSF-Kursen.

#### Bib & Sonstiges
- Bib-Gruppenraum-Wechsel direkt über den Floorplan im Buchungs-Screen; Notenspiegel-Fixture synthetisch für Tests.
- UX-Polish quer durch die Screens (Phase 3).

#### Build & Doku
- `androidx.fragment` explizit deklariert (MainActivity ist `FragmentActivity` für BiometricPrompt, war bisher nur transitiv).
- README: Abschnitt „Firebase einrichten" (gitignored `google-services.json`, `.debug`-Suffix registrieren); `server/README.md` + `.env.example` um Purge/Rate-Limit ergänzt.

### Home-Screen-Widgets mit Glance (2026-07-13)

#### Fundament
- Glance 1.1.1 im Version-Catalog + `:app`-Dependencies; Widget-Info-XMLs mit `targetCell` 3x2/4x2, freies Resize und `updatePeriodMillis=0` — Refresh triggert die App (WorkManager/DAO-Änderungen), nicht das Framework-Polling.
- `WidgetHiltEntryPoint`: Repository-Zugriff aus den Glance-Widgets (Non-Hilt-Komponenten) über ein `SingletonComponent`-EntryPoint.
- Widget-eigene DayNight-`ColorProvider`-Palette, weil Glance das Compose-`ColorScheme` nicht direkt konsumieren kann.

#### Die fünf Widgets
- **Aufgaben** (`TodoWidget`): offene Todos mit Due-Chip (heute/morgen/in X Tagen, überfällig rot). `SizeMode.Responsive` mit drei Layouts (nur Anzahl / Top-4 mit Checkbox / Top-8). Die Checkbox erledigt die Aufgabe direkt im Widget (`ToggleDoneAction` → `TodosRepository.setDone` + Re-Render), der Plus-Button öffnet die App im Neu-Anlegen-Modus.
- **Stundenplan Heute** (`StundenplanWidget`): heutige Events mit Uhrzeit, Titel und Ort; vergangene werden ausgeblendet, laufende bleiben sichtbar. Farb-Bar pro Event deterministisch aus `sourceReference`/`courseLsfId`/`title` gehasht. Empty-State kennt das Wochenende (Fr–So) und zeigt „Nächster Uni-Tag: <Tag>" aus einem 7-Tage-Vorschaufenster.
- **Stundenplan Woche** (`SchedulaWeekWidget`): 7-Tage-Agenda, nach Datum gruppiert mit Section-Headern („Mo · 30.06."), leere Tage werden übersprungen.
- **Mensa** (`MensaWidget`): heutiger Speiseplan, nach Frühstück/Mittag/Abend gruppiert (Bucket aus dem STW-Kategorie-Prefix, feste Reihenfolge). Pille nur noch bei vegan/vegetarisch — die feste 72dp-Kategorie-Pille zeigte vorher für jedes Gericht ein ellipsiertes, informationsloses „Abend · …" und stauchte den Gerichtsnamen.
- **Klausur-Countdown** (`ExamCountdownWidget`): nächste Klausur als „HEUTE 09:00" / „MORGEN" / „in X Tagen", ab 14 Tagen als Datum, mit Ampel-Farbe (rot ≤ 2 Tage, amber ≤ 7 Tage, sonst primary) und Prüfer/Raum in der Meta-Zeile.

#### Design-Kit & Deep-Links
- `feature/widgets/common/`: gemeinsames `WidgetTheme` + `WidgetPalette`, `WidgetSurface` (Card), `WidgetHeader`, `WidgetEmpty` plus 10 eigene Vector-Icons. Alle fünf Widgets darauf umgestellt — rund 600 Zeilen dupliziertes Layout raus, einheitliche Optik über alle Widgets.
- `WidgetDeepLinkController` (analog `NotificationDeepLinkController`): MainActivity fängt die Widget-Intents (`OPEN_TODOS`, `OPEN_CALENDAR`, `OPEN_CALENDAR_WEEK`, Mensa, Klausuren) ab und emittiert Signale, `AppNavGraph` routet auf den Ziel-Tab. V1 öffnet nur den Tab; Sub-Deeplinks (Todo-Detail nach ID, Auto-Open des Add-Sheets, Event-Detail) sind Follow-ups, die Intent-Extras werden noch nicht ausgewertet.

#### Sonstiges
- Demo-News-Section aus Home entfernt — hartcodierte Platzhalter-Meldungen ohne Backend, aus `HomeSection`-Enum und `HomeScreen` raus.
- `repomix-output.xml` (64k-Zeilen-Repo-Snapshot für einen One-Off-Prompt) aus Git entfernt; `.gitignore` um `presentation/` und `.claude/` ergänzt, MSE-Abgabe-Vorlage `template.docx` wird jetzt getrackt.

### Learnweb-Integration, Mortarboard-Icon, Tablet-Rail & Performance (2026-06-29)

#### Learnweb (Moodle)
- Vier Ausbaustufen an einem Tag: zuerst öffnete die Learnweb-Quick-Access-Kachel nur den Browser (CAS-SSO macht der Browser selbst, kein Token-Sharing nötig) — `Semester.learnwebYear()` berechnet die Instanz-URL aus SS+folgendem WS (SS 2026 + WS 2026/27 → `learnweb2026`). Danach folgte die echte In-App-Integration: `LearnwebClient` holt sich per CAS-Service-Ticket eine `MoodleSession`, `LearnwebScraper` liest die Kurs-Liste primär aus dem `calendar-course-filter`-Select (Navigation-Tree nur zur Titel-Augmentation), `LearnwebRepository` mit 15-Min-Throttle und No-Overwrite-bei-leerem-Scrape, neue Tabelle `learnweb_courses` (Migration 30→31).
- Phase 3: Assignment-Deadlines werden aus dem Moodle-Kalender-Block geparst (`data-event-component=mod_assign`) inkl. Uhrzeit-Regex aus dem Titel-String, ergänzt um einen Deeper-Scrape der Upcoming-View pro Kurs. Neue Tabelle `learnweb_assignments` (Migration 31→32), Spiegelung als `CustomEventEntity` mit `SOURCE_LEARNWEB_ASSIGNMENT` und „📚 "-Prefix — Klick auf so ein Kalender-Event öffnet die Moodle-URL im Browser statt des Edit-Sheets, damit der nächste Sync keine User-Edits überschreibt. Eigener `LearnwebAssignmentReminderScheduler` (3 Tage / 1 Tag / 2h vorher), verschwundene Assignments werden beim Refresh aus `custom_events` gepruned.
- Phase 4: offizieller Moodle-iCal-Export (`/calendar/export_execute.php`) als Zweit-Quelle — `LearnwebICalParser` liefert alle Course-Events (Vorlesungen, Klausuren, Deadlines, Quiz-Termine) strukturiert mit UID/TZID/RRULE statt HTML-Parsing, neue Settings-Preference zum Ein/Ausschalten samt Refresh-Intervall.
- `CustomEventEntity`/Schema v32 um `sourceKind`, `sourceReference`, `url`, `courseName` erweitert, damit gespiegelte Learnweb-Termine beim Re-Sync deterministisch über `sourceReference` (UID-Match) demselben Row-Eintrag zugeordnet werden. Global-Suche bekommt dabei Highlight-Rendering für Token-Matches mitgezogen.

#### App-Icon: Mortarboard & Semester-Unlock
- Das schlichte „Hi"-Letter-Icon wird durch einen Doktorhut (Mortarboard) ersetzt — Diamond-Brett, Trapez-Cap, Knopf mit Quaste, drei Foreground-Varianten (default/inverted/studi).
- Semester-Unlock-System: `core/common/Semester.kt` berechnet SS/WS aus `LocalDate`, `SettingsDataStore.firstSemesterKey` ankert das erste Semester unveränderlich beim allerersten App-Start. Vier Icon-Varianten schalten sich relativ dazu frei (Standard sofort, Dunkel/Klassisch/Studi nach 1/2/3 Semestern), gesperrte Tiles zeigen Schloss + Dimming.
- Fix: H/i wurden vom Circle-Mask im Launcher abgeschnitten (Scale/Translate korrigiert), Standard-Variante jetzt bewusst minimal (reines Weiß, kein Amber-Akzent). Plus Demo-Modus: ohne CAS-Login sind alle Varianten zum Ausprobieren entsperrt, mit Login greift wieder das Semester-Gate.

#### Tablet-Navigation
- Tablet (Expanded) nutzt jetzt dieselbe `NavigationRail` mit User-konfigurierten, umordbaren Tabs (`NavTabsViewModel.primaryTabs`) wie das Medium-Layout, statt eines alphabetischen `PermanentDrawer` mit allen 14 Destinations gleichrangig nebeneinander.
- Zwei Fixes direkt danach: die Rail kollabierte durch einen `verticalScroll`-Modifier auf Content-Höhe (jetzt `fillMaxHeight()`), und `AdaptiveContentBox` cappte den Content weiterhin auf 1100dp und verschenkte auf Tablet-Breite seitlichen Platz (Wrapper entfernt, Screens können den Cap bei Bedarf selbst setzen).
- `core/design/Motion.kt` mit `HiUniMotion`-Tokens (`tabFadeMs`, `contentSwitchMs`, `pushMs`, `pushFadeOutMs`, `reorderSpring()`) bündelt vorher verstreute Animation-Konstanten aus `AppNavGraph`, Calendar-Mode-Switch, Onboarding und den Reorderable-Komponenten.

#### Performance & Netz-Schonung
- Neuer `PolitenessInterceptor` schläft random 200–1200ms vor jedem Request an `lsf.uni-hildesheim.de`/`cas.uni-hildesheim.de`, MyCourses-Detail-Throttle von 400ms auf 600ms erhöht — ein Voll-Sync verteilt sich jetzt über 10+ Sekunden statt eines Bursts, schont die ältere LSF-Infrastruktur.
- Cold-Start-Prewarming um Sport erweitert (Mensa/Movies liefen schon vorher mit), Movie-Poster werden nach dem Refresh direkt über den jetzt Hilt-provided Coil-`ImageLoader` (100MB Disk-Cache) vorgeladen, Onboarding→Main-Crossfade von 600ms auf 1200ms verlängert — gegen sichtbares Nachpoppen von Postern/Namen beim ersten Öffnen.
- ViewModel-`StateFlow`s liefen mit `WhileSubscribed(5000)` und poppten bei jedem Tab-Wechsel kurz auf Initial-State zurück, weil die Upstream-Collection zwischen Tab-Besuchen stoppte; auf 60s angehoben (23 betroffene ViewModels) — ein normaler Tab-Hop hält den State jetzt warm.
- Fix: `LoginSyncOrchestrator` triggerte bei jedem Cold-Start einen vollen LSF-Sync, selbst wenn der letzte erst Minuten zurücklag — jetzt gedrosselt auf 6h seit `lastLsfSyncEpoch` (explizite Pull-to-Refresh-Aufrufe umgehen die Drosselung weiterhin). Der zuvor Hilt-provided Coil-`ImageLoader` kollidierte mit Coils Default-Singleton und machte Movie-Poster unsichtbar — Provider und Poster-Prefetch dafür wieder zurückgebaut, `AsyncImage` nutzt wieder das Default-Singleton.
- `MensaNutritionApi.per100g` auf `Map<String, String?>` umgestellt, weil die STW-API für nicht gemessene Nährwerte (z. B. `roughage`) `null` statt eines fehlenden Feldes liefert — null-Einträge werden vor dem Persistieren gefiltert statt als String `"null"` in der DB zu landen.

#### Doku
- 5 Markdown-Dokumente unter `docs/process/` als Grundgerüst für die MSE-Abgabe (Projektbeschreibung, Architektur-Übersicht, Engineering-Log, AI-Workflow, Build-and-Run); `template.docx` (Uni-Word-Vorlage) ins `.gitignore`.
- Spec-Entwurf für ein mögliches späteres P2P-Mensa-Review-Feature im Gun.js-Spirit (signierte Ed25519-Events, Web-of-Trust, Dual-Transport WebSocket-Relay + LAN-mDNS) — reines Design-Dokument, noch nicht implementiert.

### App-Icon-System, Globale Suche & Mensa-Polish (2026-06-28)

#### App-Icon-Switcher
- Vier Activity-Aliases (default/dark/classic/studi) + `AppIconManager.setVariant()` über `PackageManager.setComponentEnabledSetting`, `AppIconCard` mit vier Vorschauen im Erscheinungsbild-Settings. Teil eines größeren Drops zusammen mit Archive-Folder, Onboarding-Polish (Biometrie-Slide, Sync-Status auf der Login-Slide) und der Settings-Reorganisation zum 6-Kategorien-Hub.
- Direkt danach drei Korrekturen: das ursprüngliche Foreground zeigte das offizielle Uni-Hildesheim-Wahrzeichen (Brand-Aneignung, HiUni ist kein offizielles Uni-Projekt) — komplett neu gezeichnet als eigenständiges „Hi"-Letter-Design; die Adaptive-Icon-XMLs lagen im falschen `mipmap-anydpi/` statt `mipmap-anydpi-v26/`, und alte Android-Bot-webp-Fallbacks in den dpi-Ordnern wurden entfernt (ab minSdk 28 ohnehin ungenutzt).
- Zwei weitere Fixes: `painterResource()` crashte auf den Adaptive-Icon-XMLs (kein `<vector>`-Root) — die Vorschau-Tiles referenzieren jetzt direkt die Foreground-Drawables; die Compose-Preview für „Standard" zeigte noch Android-Sample-Grün statt HiUni-Indigo als Background-Fallback.

#### Globale Suche
- Neues `core/search/`-Modul: `GlobalSearchRepository` sucht parallel über sechs Quellen (Mail, Termine, Kurse, Klausuren, Mensa-Gerichte, Sport-Events) mit AND-Token-Match, pro Kategorie im Flow auf 5 Top-Treffer geclippt. `GlobalSearchViewModel` debounced 200ms, `SearchTile` (Lupe) im `HomeHeader` links neben Avatar/Glocke als Einstiegspunkt, Tap navigiert zum jeweiligen Tab.

#### Mensa
- Öffnungszeiten kommen jetzt live aus der STW-API (`MensaLocationApi.opening_hours`) statt aus hartcodierten Default-Slots — das OPEN/CLOSED-Badge reagiert korrekt beim Wechsel zwischen den Hildesheim-Standorten. Dazu zwei neue Diet-Filter, Geflügel und Klimaessen.
- `MealDetailSheet` (Tap auf eine `MealCard`) zeigt Nährwerte, Zusatzstoffe und Besonderheiten aus der API, die bisher ungenutzt im JSON-Roundtrip lagen; danach Polish-Pass mit Hero-Row (Studi-Preis/Kalorien side-by-side), Allergen/Diet-Split und Section-Dividern. `WeekStrip` zeigt jetzt 4 Wochen statt nur der aktuellen als horizontal scrollbare `LazyRow`.
- Fix: die STW-API hat `special_tags` von einer Top-Level-Liste nach `tags.special` verschoben (das alte Feld ist deprecated und lieferte nur einen Hinweis-String statt Objekten) — der Parser crashte darauf und der komplette Mensa-Refresh schluckte die Response. Echte Daten kommen jetzt aus `tags.special`.

#### Mail
- Neuer Swipe-Action-Wert „Als gelesen markieren" (Gegenstück zu „Als gelesen"), optional Fingerabdruck-Schutz für den gesamten Mail-Tab (`BiometricPrompt` mit PIN-Fallback, `MainActivity` dafür auf `FragmentActivity` umgestellt) — Deaktivieren verlangt seinerseits eine Bio-Auth, damit sich der Schutz nicht unbemerkt abschalten lässt.
- „Mails nur lokal löschen"-Modus: setzt `isHiddenLocally` statt die Mail vom IMAP-Server zu löschen, damit der nächste Sync sie nicht wieder reinpullt.
- Fix: die „Login abgelaufen"-Push kam bisher sofort nach dem ersten LSF-Auth-Fail, obwohl direkt nach frischem CAS-Login der erste Versuch während des Silent-Renewals scheitern kann — jetzt erst nach dem dritten Fehlversuch.

#### Sonstiges
- Edge-to-Edge bereinigt: Header-Surfaces ziehen jetzt bis y=0 durch statt eines grauen Streifens zwischen Statusbar und Header; Plus Jakarta Sans als Brand-Schrift über Downloadable Google Fonts.
- Profile/Settings/Notifications/Search stehen im Drawer jetzt oberhalb der Feature-Tabs statt alphabetisch verstreut; Settings bekommt zusätzlich eine volle-Breite Hero-Tile oben im Profil-Schnellzugriff.
- Die Home-Settings-Todos-Section trug noch den Text „Demo-Liste — noch ohne Backend", obwohl sie längst auf `TodoRepository` läuft — korrigiert auf „Nächste fällige Todos".

### Tablet-Grundlagen, Dark Mode, wiederkehrende Termine & Mail-Swipes (2026-06-27)

#### Tablet-Optimierung
- `AdaptiveContentBox` cappt den Content im Rail/Drawer-Modus auf 840dp und zentriert ihn horizontal (Phone bleibt No-Op); kurz danach ergänzt um `LocalWindowSizeClass` (Composition Local für breitenabhängige Layout-Verzweigungen) und einen opt-out `FullWidthContent`-Wrapper, Cap dabei auf 1100dp angehoben, damit bildreiche Listen (Mensa, Movies) mehr Luft haben.
- Darauf aufbauend Multi-Pane-Layouts für alle Feature-Screens: Email (40/60 Liste/Detail), Movies (40/60 List-Detail mit per-Film-VM-Cache), 2-Spalten-Grids für Mensa/Sport/Kurse/Klausuren/Todos, zentriertes Dialog statt BottomSheet für `AddEditEventSheet` auf Expanded. Notifications/Settings bewusst nicht angefasst (text-heavy, kein klarer Nutzen).
- Drei kleinere Nav-Polish-Fixes: graue Hintergrund-Pille bei unselected Drawer-Items entfernt (M3-Default war nicht transparent), einheitliche 56dp-Item-Höhe über `NavigationDrawerItemDefaults.ItemPadding` statt eigenem Padding, `PermanentDrawer`/`NavigationRail` scrollbar gemacht (14 Destinations passten nicht immer in voller Höhe auf schmale Tablets).

#### Kalender & Settings
- Wiederkehrende Termine (RFC 5545 light): `recurrenceRule` als JSON-Spalte in `custom_events` (Migration 26→27) mit Frequenz DAILY/WEEKLY/MONTHLY, Interval, optionalen `byDays` und Until-Datum; Expansion über `RecurrenceExpander` mit Hard-Cap 365 Occurrences bzw. 2 Jahre bei offenem Ende. `AddEditEventSheet` bekommt eine Wiederholungs-Sektion mit Frequenz-Chips und Mo–So-Pillen.
- Dark-Mode-Toggle in den Settings (System/Hell/Dunkel) über `ThemeMode`-Enum, beide Activities reagieren live ohne App-Restart.

#### Mail
- Swipe-Gesten für Archivieren (rechts) und Löschen (links, mit Confirmation-Dialog wegen unwiderruflichem IMAP-EXPUNGE); direkt danach konfigurierbar gemacht (`MailSwipeAction`: Archivieren/Löschen/Sternen/Als gelesen/Aus, pro Richtung einzeln in den Settings wählbar).

#### Mensa & Design-Infrastruktur
- Pin-to-Calendar-Button von der `MealCard` entfernt (verschmutzte die `custom_events`-Tabelle und ließ Home schon mal ein Gericht statt einer echten Vorlesung als „nächster Termin" zeigen) und durch einen Diet-Filter (Vegan/Vegetarisch/Fisch/Schwein/Rind) ersetzt, der nur Filter mit tatsächlichen Treffern anzeigt.
- `HiUniTopBar`/`HiUniSearchBar` als geteilte Composables (ersetzen individuelle Header-Implementierungen in Exams/Profile/Notifications/Settings-Screens) plus zentrales `core/common/DateTimeFormats`-Modul über 11 Dateien hinweg — rund 400 Zeilen Boilerplate raus. `LocationRow` im Mensa-Standort-Setting nutzt jetzt das echte M3 `RadioButton` statt eines selbstgebauten Dots.

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
