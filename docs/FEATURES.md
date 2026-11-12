# HiUni — Feature-Katalog

> Das **soll alles rein**. Status zeigt was schon implementiert ist, was geplant ist, und wie wir die heiklen Sachen praktikabel kriegen (lokal vs. echte API). Reihenfolge entspricht der UX, nicht der Implementations-Reihenfolge.

**Status-Legende:**

- ✅ **shipped** — funktioniert
- 🚧 **planned** — fest auf der TODO, mit konkreter Daten-Strategie
- 🌱 **bonus** — wenn Zeit übrig, einer der Bonus-Picks für Phase 4
- ⚠️ **blocked** — technisch nicht so machbar wie im Mock, hier ist der Workaround

**Stand:** 2026-08-25 (gegen den Code abgeglichen)

---

## 📱 Hauptbereiche

### Startseite

| Detail | Status | Daten-Strategie |
|---|---|---|
| Begrüßung mit Namen + Datum | ✅ | Name aus `SettingsDataStore`, Datum lokal |
| Hero-Karte: nächste Vorlesung mit Countdown | ✅ | Aus `CalendarRepository` (custom_events, Kurse als wiederkehrende Custom-Events) |
| 4 Schnellzugriff-Kacheln (Mensa, Bib, Mails, Aufgaben) | ✅ | Counts aus `MensaRepository` / `BibRepository` / `EmailRepository` / `TodoRepository` |
| Heutige Veranstaltungen | ✅ | Aus `CalendarRepository` für `today` |
| Uni Kino Karussell | ✅ | Aus `MoviesRepository` (unifilm.de-Scraper) |
| Offene Aufgaben Vorschau | ✅ | Aus `TodoRepository` |
| Neuigkeiten | 🚧 | Eigenes News-Feed-Modul; Quelle: Uni-Hi RSS / News-Scraper oder manuell pflegbare lokale Liste. `NewsSection`/`NewsItem` existieren in `HomeScreen.kt`, werden aber nirgends aufgerufen — reiner Platzhalter, keine Datenquelle |
| Avatar oben links → Profil-Screen | ✅ | Click-Wire-Up auf existierende `Destination.Profile`-Route |
| Bell oben rechts → Benachrichtigungen-Center | ✅ | Click-Wire-Up auf neue `Destination.PushCenter`-Route |

### Stundenplan / Kalender (4 Ansichten)

| Detail | Status | Daten-Strategie |
|---|---|---|
| Liste | ✅ | `CalendarListView` |
| Tag (Agenda) | ✅ | `CalendarDayView` |
| Tag (Stundenraster 8–18 Uhr farbkodiert) | ✅ | `HourGrid` in `CalendarViews.kt` — stündliches Grid mit positionsberechneten Cards, Course-Color über `CourseColor.kt` |
| Woche (5-Spalten Mo–Fr) | ✅ | `CalendarWeekView` |
| Monat (Kalendergitter mit Punkten pro Tag) | ✅ | `CalendarMonthView`, klickbare Tage springen zur Tag-View |
| Add/Edit/Delete + Date/Time-Picker | ✅ | `AddEditEventSheet` |
| Reminder-Chips | ✅ | Notification-Scheduling über `NotificationScheduler` |
| Laufende Vorlesung als fortlaufende Mitteilung | ✅ | `core/notifications/lecture`: während einer LSF-Vorlesung „Kurs", darunter „Raum · bis 11:45", im Kopf „läuft noch" mit System-Countdown (Chronometer bis Ende, `setTimeoutAfter` räumt ab, kein Polling). Reine Planung in `LiveLecturePlanner` (48-h-Horizont, Serien via `RecurrenceExpander`, nur `SOURCE_LSF_STUNDENPLAN`), ein einziger Weck-Alarm zum nächsten Beginn (`LiveLectureScheduler`/`LiveLectureReceiver`). Neu geplant bei App-Start, Stundenplan-/Kalender-Änderung (Room-Flow), Schalter, Kurs-Einstellungen und im `ReminderRestorer`. Eigener leiser Channel + Kategorie „Laufende Vorlesung" (Default an), Tap öffnet den Kalender, Wegwischen wird gemerkt. Android 16: fragt Live-Update-Hervorhebung an (`android.requestPromotedOngoing`), sonst normale Mitteilung. **Je Kurs abschaltbar** (`CourseNotificationPrefs`, DataStore je LSF-ID, Default an): Vorlesungen abgeschalteter Kurse überspringt der Planer, eine sichtbare Mitteilung verschwindet sofort; Termine ohne Kurs-Zuordnung folgen nur dem globalen Schalter |
| Vorlesungs-Erinnerung je Kurs | ✅ | Pro Kurs „Erinnerung: Aus / 5 / 10 / 15 / 30 Min" (Default aus, liegt im `CourseNotificationPrefs`-Store, nicht in den neu gesyncten LSF-Terminen). `LectureReminderPlanner` plant im rollierenden 72-h-Fenster je Vorkommen eine Termin-Erinnerung („Analysis, Beginnt um 10:15 · F101", Kind `EVENT`, daher mit „Später"), eigener ID-Bereich −799.998.999..−770.000.000; `LectureReminderAlarms` räumt überholte Alarme ab. Der Weck-Alarm der Laufenden Vorlesung hält das Fenster am Rollen, `ReminderRestorer` plant nach Reboot/Update/Zeitwechsel neu. UI: Abschnitt „Mitteilungen" im Kurs-Detail und „Erinnerungen & Push → Vorlesungen" (Übersicht „Live-Anzeige für X von Y Kursen, Erinnerung für Z", Liste aller Kurse des aktuellen Semesters, „Alle an"/„Alle aus") |
| Wiederkehrende Termine (Kurse jede Woche) | ✅ | `CustomEventEntity.recurrenceRule` (Migration 26→27) + `RecurrenceRule.kt`/`RecurrenceExpander.kt` (RFC 5545 light: FREQ/INTERVAL/BYDAY/UNTIL, Cap 2 Jahre). Master in DB, Occurrences in-memory expandiert |
| „In Kalender packen" Snapshot-Pins | ✅ | Mechanik: `sourceKind`-Pin + `findBySourceReference`-Upsert. Live für **Hochschulsport** (`SOURCE_SPORT_PIN`, `SportDetailViewModel.pinToCalendar`) und **Kino** (`SOURCE_MOVIE_PIN`, `MovieDetailViewModel.pinToCalendar`). Der Home-Hero beschriftet den nächsten Termin jetzt nach Quelle (`nextEventLabel`: Vorlesung, Film, Sportkurs, Raumbuchung, Abgabe, Termin), damit Pins nicht mehr als „nächste Vorlesung" erscheinen. **Mensa-Pin** bleibt entfernt (Migration 27→28), ließe sich mit dem Quell-Label aber wieder einbauen |
| Export als .ics (teilen/speichern) | ✅ | Teilen-Icon im Kalender-Header → `IcsExportSheet`: Vorlesungen, Eigene Termine, Klausuren (aus `exams`), Pins, Abgaben; Zeitraum „Dieses Semester" oder „Nächste 4 Wochen". `IcsExportPlanner` + `IcsExporter` (RFC 5545, `TZID=Europe/Berlin` mit VTIMEZONE, Serien als eine RRULE, VALARM aus dem Reminder). Teilen per FileProvider-Chooser, Speichern per `ACTION_CREATE_DOCUMENT`. Reiner Export ohne Sync (ADR-0006) |

### Mensa

| Detail | Status | Daten-Strategie |
|---|---|---|
| Tagesmenü (Preise, Tags vegan/vegetarisch/fisch) | ✅ | STW-ON-API + Room-Cache. Preise je **Preisgruppe** (Studierende/Bedienstete/Gäste, alle drei schon in `meals`): Automatik aus dem Uni-Login (`PriceGroupRules.detect`: eduPerson-Affiliation falls vorhanden, sonst Matrikelnummer bzw. LSF-Notenspiegel = Studierende, Login ohne beides = Bedienstete, ohne Login = Studierende) oder fester Override in Einstellungen → Mensa (`SettingsDataStore.mensaPriceGroup`). Gilt für Mensa-Tab, Detail-Sheet, Suche und Widget; fehlt der Gruppenpreis, wird der Studi-Preis gezeigt |
| Kategoriefilter | ✅ | UI-State + DAO-Query |
| Bewertungen anzeigen | 🌱 | **Lokal**: User-eigene Bewertungen pro Gericht in `meal_ratings` Tabelle, Anzeige als Durchschnitt der eigenen Vergangenheit ("dein Schnitt für Pasta: 4.2") — keine Crowd-Daten |
| Öffnungszeiten | ✅ | Aus STW-ON-API Location-Endpoint (`MensaHours`, angezeigt im `MensaHeader`) |
| „In Kalender packen" | ⚠️ | War implementiert, **wieder entfernt**: gepinnte Menüs erschienen im Home-Hero als „nächste Vorlesung". Migration 27→28 löscht die `MENSA_PIN`-Bestandsdaten, `SOURCE_MENSA_PIN` bleibt als Konstante für einen späteren Wiedereinbau |

### Bibliothek

| Detail | Status | Daten-Strategie |
|---|---|---|
| 6 Räume mit Live-Auslastung | ✅ | `BibScraper` + `BibRepository`, Räume/Slots in `BibEntities.kt` |
| Auslastungsbalken (frei/teil/voll als Hex-Farbe → Enum) | ✅ | `BibScraper` mappt die Hex-Hintergründe (`#DF2E3B` → `BOOKED` usw.) auf `SlotStatus`, Einfärbung + Legende in `LibraryBookingScreen` |
| „Buchen"-Button | ✅ | **Besser als geplant** — kein Browser-Intent, sondern echter In-App-Flow: `BibRepository.book(...)`/`cancel(...)` über `BibClient`/`BibSession`, UI `LibraryBookingScreen`, eigene Buchungen als `SOURCE_BIB_BOOKING`-Kalendereintrag. Regeln: max. 2h-Slot, 1 Buchung/Tag |
| Statistik (verfügbar / Räume / Personen) | ✅ | Stats-Sektion in `BibScreen`, aggregiert aus `RoomAvailability.freeCount` + Raum-Kapazitäten |
| Lieblings-Räume markieren + Push wenn frei | ✅ | Stern ★/☆ in der Raumkarte (`BibScreen`), Favoriten + Belegt-Baseline im Preferences-DataStore (`BibFavoritesStore`). Raum-Alarm-Karte unter den Räumen (`BibRoomAlarmStore`): Modus Aus / Nur heute (gilt bis 23:59, danach lazy Aus) / Immer, Mindestdauer 1 oder 2 Std (Standard 2 Std), Zeitfenster von/bis in vollen Stunden (Standard 08 bis 20 Uhr). Bestandsnutzer mit Favoriten starten mit „Immer“, alle anderen mit „Aus“. Jeder frische Snapshot läuft durch `BibFavoriteWatcher` → reiner Diff `BibFreeSlotNotifier`: gemeldet wird nur eine Stornierung (vorher fremd belegt, jetzt frei) heute nach „jetzt“, als zusammenhängender freier Block im Zeitfenster mit mindestens der Wunschdauer, max. eine Meldung pro Raum; bei Alarm aus läuft die Baseline still weiter. Mit Favorit und aktivem Alarm holt `PrefetchOrchestrator` den Snapshot im Zeitfenster mit 25 min TTL (per 15-min-Tickle effektiv alle 30 min, je ein GET), auch ohne Login. Eigene Kategorie/Kanal „Bibliothek“, Tap öffnet den Bib-Screen. Eingeloggt und ohne eigene Buchung am Tag hat die Meldung einen „Buchen“-Button (nur entsperrt): bucht ab Blockbeginn min(Wunschdauer, Block, 2 h) im 30-min-Raster, prüft vorher gegen einen frischen Snapshot und ersetzt die Meldung durch „F102 gebucht 14:00–16:00“ bzw. eine konkrete Fehlermeldung |

### Kurse / Module

LSF / Stud.IP hängen hinter CAS-SSO. **Status:** CAS-SSO ist inzwischen implementiert (`core/auth/`, WebView-Login), LSF-Kurse werden via `LsfSyncWorker` + Scraper gezogen. Manuelles lokales Eintragen bleibt als Fallback.

| Detail | Status | Daten-Strategie |
|---|---|---|
| Liste aller Module | ✅ | `courses`-Tabelle (`CourseEntity`/`CourseDao`/`CourseRepository`), gefüllt per LSF-Sync oder `AddEditCourseSheet`, Semester-Filter in `CoursesScreen` |
| Detailansicht (LP, Semester, nächste Prüfung) | ✅ | Felder in `CourseEntity`, Rendering in `CoursesScreen` |
| Fortschrittsbalken | ✅ | `SemesterProgress.kt` (`semesterProgress`) → `ProgressBar`/`SemesterProgressBar` |
| „Note steht noch aus"-Hinweis | ✅ | `CoursesUiState.effectiveGrade` — echte Notenspiegel-Note (`CourseGradeMatcher` gegen `GradesRepository`) hat Vorrang, manuelle `CourseEntity.grade` überschreibt, „steht noch aus" nur als Fallback |
| Verknüpfung Kurs ↔ Calendar-Event | ✅ | `SOURCE_LSF_STUNDENPLAN` + `sourceReference` in `LsfStundenplanRepository` (Upsert per Ref, Prune verwaister Events) |

---

## 🔔 Erweiterte Screens

### Profil (Avatar oben links auf Startseite)

| Detail | Status | Daten-Strategie |
|---|---|---|
| Studierendenausweis als Karte | 🌱 | Matrikelnummer + Name in EncryptedSharedPrefs, QR enthält die Matrikel-Nr |
| QR-Code + Barcode | 🌱 | ZXing-Library oder manuelle Composable mit Canvas |
| Semesterticket-Info | 🌱 | Statischer Text aus Settings; oder Foto vom echten Ticket lokal speichern |
| Mensa-Guthaben-Übersicht | ⚠️ | STW-API ohne öffentlichen Guthaben-Endpoint. **Workaround**: lokales Tracking — User trägt Aufladungen manuell ein, App rechnet runter bei "Essen gekauft"-Tap |
| Schnellzugriff zu allen Screens | ✅ | Quick-Grid in `ProfileScreen.kt` aus `Destination.all` (ohne Home/Profil/Settings, Settings als eigene Hero-Tile) + Matrikelnummer-Copy |

### Einstellungen

| Detail | Status | Daten-Strategie |
|---|---|---|
| Mensa-Standort | ✅ | `SettingsDataStore.mensaLocationId`, UI in den Settings-Kategorien |
| Sync-Intervalle (Email/LSF) | ✅ | `emailSyncIntervalMinutes` (15/30/60/120) + `lsfSyncIntervalHours` (6/12/24/aus), WorkManager-Reconfigure on change |
| Notification-Default-Minuten | ✅ | `SettingsDataStore.notificationMinutesBefore`, UI `RemindersSettingsScreen` |
| Credentials für Email | ✅ | `CredentialsManager` + `CredentialsCard` in `AccountSettingsScreen` |
| Theme / Dark-Mode-Override | ✅ | DataStore-Key + Theme-Wrapper (`AppearanceSettingsScreen`) |
| Tab-Leiste anpassen | ✅ | `NavSettingsScreen`, DataStore-Key `navigation_order` |
| App-Updates (Self-Update aus GitHub Releases) | ✅ | `core/update/`: `releases/latest` von `ThePhobos71/HiUni` (ETag-Cache), Tag `vX.Y.Z` → versionCode `X*10000+Y*100+Z`. Nur Release-Builds; Hintergrund-Check per WorkManager (12 h, Netz) und beim Start, gedrosselt auf 6 h, abschaltbar, Version überspringbar. Mitteilung im leisen Kanal „App-Updates“, Tap öffnet `AppUpdateSettingsScreen`. Download nach `cacheDir/updates/` mit Größen-, Paket-, Signatur- (SHA-256) und Versionsprüfung, Installation per `PackageInstaller`-Session, vorher ggf. Freigabe „Unbekannte Apps installieren“ |
| Easter-Egg-Themes (Triple-Tap About) | 🌱 | v1-Pattern reaktivieren. Voraussetzung fehlt noch: `AboutScreen.kt` ist ein 31-Zeilen-Stub (nur Name/Version/Untertitel, kein Tap-Handling) |

### Mensa-Karte (Guthaben)

⚠️ Kein offizielles STW-API für NFC oder Guthaben-Stand. **Workaround:**

| Detail | Status | Daten-Strategie |
|---|---|---|
| Guthaben-Hero | ✅ | Guthaben aus NFC-Read der STW-Karte (`MensaCardReader`) |
| Aufladebetrag wählen (5/10/20/50€) | 🌱 | Manuell eingetragene Aufladung → addiert auf lokale Balance |
| NFC-Reader mit Animation | ✅ | NFC-Read der STW-Karte via `IsoDep` (`MensaCardReader`) |
| Buchungshistorie | ✅ | Lokale `mensa_transactions` Tabelle |

### Campus-Plan

| Detail | Status | Daten-Strategie |
|---|---|---|
| SVG-Karte (Gebäude, Wege, Bäume) | 🌱 | Eigene SVG-Composition aus Uni-Hildesheim-Karte (manuelle Vektor-Erstellung in Figma → Compose-Path-Strings) |
| „Du bist hier"-Pin | 🌱 | `LocationManager` + Geofence auf Campus |
| Filter (Hörsäle / Bib / Mensa / Sport) | 🌱 | Filter dimmt nicht-relevante Gebäude |
| Gebäudeliste | 🌱 | Hardcoded für Uni Hildesheim |

### Mail

| Detail | Status | Daten-Strategie |
|---|---|---|
| Posteingang mit Ungelesen-Markern | ✅ | Jakarta Mail IMAP (`ImapClient`), Room-Cache (`EmailEntity`/`EmailDao`) |
| Sterne, Filter | ✅ | `toggleStar` + Swipe-Actions (`MailSwipeAction`), Ordner-Umschalter Posteingang/Gesendet/Markiert |
| Detail-Ansicht | ✅ | HTML→Text via Jsoup, Detail-Pane in `EmailScreen` (List-Detail auf Tablet) |
| Verfassen / Antworten / Weiterleiten | ✅ | `SmtpClient` + `EmailComposeScreen`/`EmailComposeViewModel`, Prefill via `EmailComposePrefillHolder` (Re:-Prefix, In-Reply-To/References), Drafts |
| Löschen | ✅ | `EmailViewModel.deleteEmail` → IMAP-Delete + lokaler Prune |
| Anhänge öffnen via FileProvider | ✅ | `EmailAttachment` + `openAttachment`; zusätzlich `IcsParser` für Kalender-Anhänge |
| Avatar-Initialen pro Absender | ✅ | `EmailEntity.initials`, gerendert in der Listen-Row |

### Aufgaben (Todos)

Eigenes Feature, parallel zu Calendar. Pattern wie Recipe A.

| Detail | Status | Daten-Strategie |
|---|---|---|
| Neue Aufgaben hinzufügen + Abhaken | ✅ | `TodoEntity` + `TodoDao` + `TodosRepository`, UI `TodosScreen`/`AddEditTodoSheet` |
| Fälligkeitsdaten farbcodiert (heute=rot, bald=gelb) | ✅ | `TodoFormatting.kt` aus `dueDate` vs. `now()` |
| Kursverknüpfung | ✅ | `TodoEntity.courseId` (indiziert), Kurs-Chip in der Row |
| Push bei Fälligkeit | ✅ | `TodoReminderScheduler` + `TodoReminderPlanner`: Erinnerung am Fälligkeitstag um 09:00 (Kind `TODO`, eigene Kategorie/Channel „Aufgaben"), gekoppelt im `TodosRepositoryImpl` (Anlegen/Bearbeiten/Wieder-Öffnen plant, Abhaken/Löschen cancelt), Re-Scheduling beim App-Start und nach Reboot/App-Update/Zonenwechsel (`ReminderRestorer`); Tap öffnet den Aufgaben-Screen. Buttons in der Notification (`NotificationActionReceiver`): „Erledigt“ hakt über `TodosRepository.setDone` ab, „Später“ zeigt die Erinnerung nach 1 h erneut (eigener Snooze-Alarm, geht bei Reboot verloren) |

### Uni Kino

| Detail | Status | Daten-Strategie |
|---|---|---|
| Featured-Film Hero + Programmliste | ✅ | unifilm.de-Scraper (Phase 2.6) |
| Genre, Dauer, Saal, Zeit | ✅ | Aus Scraper-Heuristik (v1-Pattern) |
| Reservieren-Button | ⚠️ | unifilm.de hat kein Booking-API. **Workaround**: Intent zur unifilm.de-Seite |
| Trailer-Links | ✅ | Button "Trailer ansehen" im Film-Detail (nur bei gefüllter `trailerUrl`, nicht bei Überraschungsfilmen), öffnet per `Intent.ACTION_VIEW`, `ActivityNotFoundException` wird abgefangen |
| „In Kalender packen" | ✅ | Button "In Kalender eintragen" / "Im Kalender" im Film-Detail (`MovieDetailViewModel.pinToCalendar`/`unpin`). Einzeltermin mit `SOURCE_MOVIE_PIN`, Referenz `movie:<filmId>:<sessionId>` (Upsert per `findBySourceReference`), Ende = Start + Laufzeit (sonst 2 h), Überraschungsfilme als "Überraschungsfilm im Uni-Kino". Reminder = Standard-Vorlaufzeit aus den Einstellungen, `ReminderRestorer` plant ihn nach Reboot neu. Ausgeblendet ohne Datum/Uhrzeit oder für vergangene Vorstellungen. Im Home-Hero erscheint der Pin wie ein Sport-Pin als nächster Termin |

### Lerngruppen

Ohne Backend funktional als „eigene Gruppen lokal verwalten". **Noch komplett offen** — es gibt weder Entity, DAO, Repository noch Screen im Code.

| Detail | Status | Daten-Strategie |
|---|---|---|
| Liste, Mitglieder-Namen | 🚧 | Lokale `study_groups`-Tabelle, manuell gepflegt |
| Beitreten/Verlassen-Toggle | 🚧 | Lokaler Status |
| Nächstes Treffen mit Ort | 🚧 | Felder in Entity, optional als Calendar-Event spiegeln |
| Filter Alle / Meine / Offen | 🚧 | UI-Filter |
| Mitglieder-Avatars gestapelt | 🚧 | Generierte Initialien-Avatars wie in Mail |
| Push bei Treffen | 🚧 | Calendar-Spiegelung |

### Klausurplan

| Detail | Status | Daten-Strategie |
|---|---|---|
| Liste aller Klausuren | ✅ | `exams`-Tabelle, gefüllt per LSF-Klausur-Scraper oder `AddEditExamSheet` |
| Countdown-Hero zur nächsten | ✅ | `CountdownHero` in `ExamsScreen` (inkl. „Heute!" / „Termin steht aus") |
| Tage-Chip rot (≤7) / gelb (≤21) | ✅ | Tage-Chip in `TimelineRow`, semantische Farben |
| Vertikale Timeline | ✅ | `TimelineRow`-Liste, auf Tablet-Landscape als 2-Spalten-Grid |
| Lernplan-Generator | ✅ | Lokal und deterministisch (`feature/exams/studyplan/StudyPlanner`): verteilt Lernblöcke nach LP und Restzeit in freie Kalenderlücken (Puffer, Tagesmaximum, freie Tage), schreibt sie als `STUDY_PLAN`-Termine; Einstellungen im DataStore, Sheet mit Vorschau auf dem Klausuren-Screen |

### Notenübersicht

**Update:** Nicht mehr „lokal pflegen" — die Noten kommen echt aus dem LSF-Notenspiegel (`NotenspiegelScraper` hinter CAS-SSO). Manuelles Eintragen bleibt als Override.

| Detail | Status | Daten-Strategie |
|---|---|---|
| GPA-Hero in Notenstufen-Farbe | ✅ | `GradesRepository` aus `NotenspiegelScraper`-Daten (`GradeEntity`/`GradesSummaryEntity`), LP-gewichtet, Hero in `GradesScreen` |
| ECTS-Fortschritt (X/180 LP) | ✅ | Balken gegen `GradesUiState.TARGET_LP` |
| Push bei Noteneintragung | ✅ | `GradeDiffNotifier` (pure, JVM-getestet) entscheidet im `GradesRepository`-Sync: pro neuer/neu-benoteter Note eine lokale Notification (`NotificationKind.GRADE`), ab 4 auf einmal eine Sammel-Meldung, Erst-Sync (leere Baseline) ohne Push, **datenschutzbewusst ohne Note im Text**. Dank FCM-Tickle + `TTL_GRADES_MS` (50 min) kommt der Push binnen etwa einer Stunde. Opt-out über die Kategorie „Noten" in den Mitteilungs-Einstellungen; Tap öffnet das Push-Center |
| Aktuelles Semester (Ausstehend) | ✅ | `SemesterSection`-Gruppierung |
| Vergangene Semester aufklappbar mit Schnitt | ✅ | `SemesterHeader` mit Expand/Collapse (neuestes offen) + LP/Schnitt pro Semester |
| Notenskala-Legende | ✅ | Aufklappbare Legenden-Fußsektion in `GradesScreen` |

### Benachrichtigungen-Center

| Detail | Status | Daten-Strategie |
|---|---|---|
| Gruppiert Heute / Gestern / Älter | ✅ | `notifications`-Tabelle (v21), `NotificationReceiver` (`@AndroidEntryPoint`) loggt jeden Trigger via `NotificationLogRepository.log(...)` |
| Ungelesen-Dot + „Alle gelesen"-Button | ✅ | `isRead`-Spalte + `observeUnreadCount()`; Home-Bell zeigt Badge, „Alle gelesen" als Top-Bar-Action |
| Typ-Icons farbig (Klausur, Note, Mail, Mensa, Kino, Sport, Bib, System) | ✅ | `NotificationKind`-Enum + `kindStyling(...)` mappt auf `HiUniSemanticColors` (red/green/amber/purple/primary/muted) und Outlined-Icon |
| Auto-Prune älter als 30 Tage | ✅ | `NotificationsViewModel.init` ruft `repository.prune(...)` beim Öffnen |

### Hochschulsport

HSP-Website hat eine öffentliche Programm-Liste. Buchung erfordert Login.

| Detail | Status | Daten-Strategie |
|---|---|---|
| Kursfilter | ✅ | `SportScraper` über die öffentliche Programm-Seite, Filter-Chips in `SportScreen` (nach Kurstitel, nicht nach fixen Kategorien) |
| Auslastung (freie Plätze) | ✅ | `SportEventEntity.capacity` + freie Plätze im `SportDetailScreen` |
| „In Kalender" | ✅ | `SportDetailViewModel.pinToCalendar` → `SOURCE_SPORT_PIN` inkl. Reminder-Scheduling und Unpin |
| „Buchen"-Button | ⚠️ | Booking (SuperSaaS) hinter Login. **Workaround**: Intent zur HSP-Buchungs-Seite |

---

## ⚙️ Konfiguration

| Detail | Status | Daten-Strategie |
|---|---|---|
| Tab-Leiste anpassen (5 von N Tabs auswählen) | ✅ | `NavSettingsScreen`, DataStore-Key `navigation_order`, `MAX_NAV_TABS` |
| Drag-Order per ↑/↓ Buttons | ✅ | ↑/↓ Buttons in `NavSettingsScreen` |
| Live-Vorschau der unteren Leiste | ✅ | `PreviewBar`-Composable das den NavigationBar 1:1 darstellt |

---

## 🔌 Infrastruktur & Querschnitt (nachträglich geshipped)

Features, die im Original-Katalog keine eigene Zeile hatten, inzwischen aber im Code fertig sind.

| Detail | Status | Daten-Strategie |
|---|---|---|
| CAS-SSO (Apereo, Uni-Hi) | ✅ | `core/auth/` — WebView-Login gegen `.../sso/login`, Ticket-Handling, `CasSession`/`CasCookieStore` |
| LSF-Scraper (Kurse / Stundenplan / Klausuren) | ✅ | `feature/lsf/data/Lsf*Scraper.kt`, orchestriert via `core/sync/LsfSyncWorker` (`@HiltWorker`) |
| Learnweb-Scraper | ✅ | `feature/learnweb/data/LearnwebScraper.kt` |
| STW-ON-Mensa-Datenquelle | ✅ | `feature/mensa/data/MensaApiService.kt` (`sls.api.stw-on.de/v1`) |
| unifilm.de-Scraper | ✅ | `feature/movies/data/MovieScraper.kt` |
| HSP-Programm-Scraper | ✅ | `feature/sport/data/SportScraper.kt` inkl. freie Plätze und Kalender-Pin (nur die Buchung bleibt extern) |
| Global Search | ✅ | `core/search/GlobalSearchRepository` kombiniert Calendar/Kurse/Mail/Learnweb/LSF/Mensa/Sport; UI `feature/search/ui/GlobalSearchScreen`. Tap öffnet den konkreten Treffer (Mapping in `feature/search/SearchNavigation`, One-Shot-Übergabe per `core/search/SearchSelectionHolder`): Mail-Detail (auch für Mails jenseits der 200 neuesten), Kurs-Detail, Mensa-Detail-Sheet, Kalender-Tag des Termins, Klausur scrollt in die Liste und wird kurz hervorgehoben, Sport per `SportDetail`-Route, Learnweb-Kurse/-Abgaben im Browser |
| Notenspiegel-Scraper (Noten / GPA) | ✅ | `feature/grades/data/NotenspiegelScraper.kt` + `GradesRepository` (LP-gewichteter Schnitt, Neue-Note-Push) |
| About-Screen | 🚧 | `feature/about/ui/AboutScreen.kt` ist ein 31-Zeilen-Stub (Name, Version, ein Untertitel). Offen: Lizenzen, AI-Usage-Hinweis, Build-Info, Easter-Egg-Trigger |
| Glance-Home-Widgets | ✅ | `feature/widgets/` — Stundenplan (Tag + Woche), Mensa, Todos, Klausur-Countdown; Receiver in AndroidManifest registriert |

---

## Scope-Erweiterung — was zur Phase-Roadmap hinzukommt

Der ursprüngliche Rebuild-Plan deckte 6 Features (Home/Calendar/Mensa/Movies/Bib/Email). Mit dieser Liste kommen dazu:

| Neu | Phase | Aufwand-Schätzung |
|---|---|---|
| ~~Kurse-Feature (jetzt via LSF-Sync)~~ | 2.7 ✅ | shipped |
| ~~Aufgaben-Feature~~ | 2.8 ✅ | shipped (nur Fälligkeits-Push offen) |
| ~~Profil-Screen (ohne StudiCard)~~ | 3.3 ✅ | shipped |
| ~~Tab-Leiste-Konfiguration~~ | 3.4 ✅ | shipped |
| ~~Notenübersicht (jetzt via Notenspiegel-Scraper)~~ | 3.5 ✅ | shipped |
| Lerngruppen (lokal) | 3.6 | 6h |
| ~~Klausurplan (aus LSF + manuell)~~ | 3.7 ✅ | shipped |
| ~~Push-Center (Notifications-Tabelle)~~ | 3.8 ✅ | shipped |
| ~~Mensa-Karte (NFC-Read + Buchungshistorie)~~ | 3.9 ✅ | shipped |
| Campus-Plan | 4 Bonus | 10h |
| ~~Hochschulsport-Scraper~~ | 4 Bonus ✅ | shipped (Buchung bleibt extern) |
| StudiCard mit QR | 4 Bonus | 4h |
| Easter-Egg-Themes | 4 Bonus | 2h |

**Realistischer Bilanz:** Original-Plan war ~145h Core. Erweiterung bringt ~78h dazu → **223h total**. Bei 6.5h/Person/Woche × 2 Personen × 16 Wochen = 208h verfügbar. Wir sind **leicht über Plan**, kompensierbar durch:

- Bonus-Features als Phase-4-Optional (zuerst die hochwertigen ✅+🚧 saubermachen)
- Lerngruppen & Klausurplan ggf. zusammenlegen
- Mensa-Karte als Bonus statt Phase-2

**Empfehlung:** Erst die ursprüngliche Phase-2-Welle abschließen (Mensa-Daten, Settings, Home-Wire-Up, Movies), dann entscheiden welche der neuen Features eingeschoben werden vs. in Phase 3+ rutschen.
