# HiUni — Feature-Katalog

> Das **soll alles rein**. Status zeigt was schon implementiert ist, was geplant ist, und wie wir die heiklen Sachen praktikabel kriegen (lokal vs. echte API). Reihenfolge entspricht der UX, nicht der Implementations-Reihenfolge.

**Status-Legende:**

- ✅ **shipped** — funktioniert
- 🚧 **planned** — fest auf der TODO, mit konkreter Daten-Strategie
- 🌱 **bonus** — wenn Zeit übrig, einer der Bonus-Picks für Phase 4
- ⚠️ **blocked** — technisch nicht so machbar wie im Mock, hier ist der Workaround

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
| Neuigkeiten | 🚧 | Eigenes News-Feed-Modul; Quelle: Uni-Hi RSS / News-Scraper oder manuell pflegbare lokale Liste |
| Avatar oben links → Profil-Screen | ✅ | Click-Wire-Up auf existierende `Destination.Profile`-Route |
| Bell oben rechts → Benachrichtigungen-Center | ✅ | Click-Wire-Up auf neue `Destination.PushCenter`-Route |

### Stundenplan / Kalender (4 Ansichten)

| Detail | Status | Daten-Strategie |
|---|---|---|
| Liste | ✅ | `CalendarListView` |
| Tag (Agenda) | ✅ | `CalendarDayView` |
| Tag (Stundenraster 8–18 Uhr farbkodiert) | 🚧 | Polish-Phase: stündliches Grid mit positionsberechneten Cards. Course-Color über `sourceReference`-Hash deterministisch zugewiesen |
| Woche (5-Spalten Mo–Fr) | ✅ | `CalendarWeekView` |
| Monat (Kalendergitter mit Punkten pro Tag) | 🚧 | Polish-Phase, klickbare Tage springen zur Tag-View |
| Add/Edit/Delete + Date/Time-Picker | ✅ | `AddEditEventSheet` |
| Reminder-Chips | ✅ | Notification-Scheduling über `NotificationScheduler` |
| Wiederkehrende Termine (Kurse jede Woche) | 🚧 | Erweitere `CustomEventEntity` um `recurrenceRule` (RFC 5545 light) |
| „In Kalender packen" Snapshot von Mensa/Movie | 🚧 | Recipe I, `sourceKind = MENSA_PIN/MOVIE_PIN` |

### Mensa

| Detail | Status | Daten-Strategie |
|---|---|---|
| Tagesmenü (Preise, Tags vegan/vegetarisch/fisch) | ✅ | STW-ON-API + Room-Cache |
| Kategoriefilter | ✅ | UI-State + DAO-Query |
| Bewertungen anzeigen | 🌱 | **Lokal**: User-eigene Bewertungen pro Gericht in `meal_ratings` Tabelle, Anzeige als Durchschnitt der eigenen Vergangenheit ("dein Schnitt für Pasta: 4.2") — keine Crowd-Daten |
| Öffnungszeiten | ✅ | Aus STW-ON-API Location-Endpoint |
| „In Kalender packen" | 🚧 | Snapshot-Pin |

### Bibliothek

| Detail | Status | Daten-Strategie |
|---|---|---|
| 6 Räume mit Live-Auslastung | 🚧 | ubwww-Scraper (Phase 3.1) |
| Auslastungsbalken (frei/teil/voll als Hex-Farbe → Enum) | 🚧 | Scraper-Output mappen |
| „Buchen"-Button | ⚠️ | Bib hat kein öffentliches Booking-API. **Workaround**: Button öffnet ubwww-Booking-Formular im Browser via `Intent.ACTION_VIEW` |
| Statistik (verfügbar / Räume / Personen) | 🚧 | Aus Scraper-Daten aggregieren |
| Lieblings-Räume markieren + Push wenn frei | 🌱 | Lokal: Favorites in DataStore, periodischer WorkManager-Sync mit Scraper, lokale Notification |

### Kurse / Module

LSF / Stud.IP hängen hinter CAS-SSO. **Status:** CAS-SSO ist inzwischen implementiert (`core/auth/`, WebView-Login), LSF-Kurse werden via `LsfSyncWorker` + Scraper gezogen. Manuelles lokales Eintragen bleibt als Fallback.

| Detail | Status | Daten-Strategie |
|---|---|---|
| Liste aller Module | 🚧 | Lokale `courses`-Tabelle, vom User eingetragen via „Kurs anlegen"-Form |
| Detailansicht (LP, Semester, nächste Prüfung) | 🚧 | Felder in `CourseEntity` |
| Fortschrittsbalken | 🚧 | Berechnet aus aktuellem Datum vs. Semester-Start/-Ende, falls Klausur eingetragen |
| „Note steht noch aus"-Hinweis | 🚧 | Default-Anzeige bis Note manuell eingetragen wird |
| Verknüpfung Kurs ↔ Calendar-Event | 🚧 | `CustomEventEntity.sourceReference = courseId` |

---

## 🔔 Erweiterte Screens

### Profil (Avatar oben links auf Startseite)

| Detail | Status | Daten-Strategie |
|---|---|---|
| Studierendenausweis als Karte | 🌱 | Matrikelnummer + Name in EncryptedSharedPrefs, QR enthält die Matrikel-Nr |
| QR-Code + Barcode | 🌱 | ZXing-Library oder manuelle Composable mit Canvas |
| Semesterticket-Info | 🌱 | Statischer Text aus Settings; oder Foto vom echten Ticket lokal speichern |
| Mensa-Guthaben-Übersicht | ⚠️ | STW-API ohne öffentlichen Guthaben-Endpoint. **Workaround**: lokales Tracking — User trägt Aufladungen manuell ein, App rechnet runter bei "Essen gekauft"-Tap |
| Schnellzugriff zu allen Screens | 🚧 | Grid aus allen `Destination`s |

### Einstellungen

| Detail | Status | Daten-Strategie |
|---|---|---|
| Mensa-Standort | 🚧 | `SettingsDataStore.mensaLocationId` ist da |
| Sync-Intervalle (Email/Mensa) | 🚧 | DataStore, WorkManager-Reconfigure on change |
| Notification-Default-Minuten | 🚧 | `SettingsDataStore.notificationMinutesBefore` ist da |
| Credentials für Email | 🚧 | `CredentialsManager` |
| Theme / Dark-Mode-Override | ✅ | DataStore-Key + Theme-Wrapper (`AppearanceSettingsScreen`) |
| Tab-Leiste anpassen | ✅ | `NavSettingsScreen`, DataStore-Key `navigation_order` |
| Easter-Egg-Themes (Triple-Tap About) | 🌱 | v1-Pattern reaktivieren |

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
| Posteingang mit Ungelesen-Markern | 🚧 | Jakarta Mail IMAP (Phase 3.2) |
| Sterne, Filter | 🚧 | Lokal in Room gecached, Flags-Sync mit IMAP |
| Detail-Ansicht | 🚧 | HTML→Text via Jsoup |
| Antworten | 🌱 | SMTP über Jakarta Mail Send |
| Löschen | 🚧 | IMAP `\Deleted` Flag |
| Anhänge öffnen via FileProvider | 🚧 | Manifest ist Phase-1-ready |
| Avatar-Initialen pro Absender | 🚧 | Berechnet aus „From"-Header |

### Aufgaben (Todos)

Eigenes Feature, parallel zu Calendar. Pattern wie Recipe A.

| Detail | Status | Daten-Strategie |
|---|---|---|
| Neue Aufgaben hinzufügen + Abhaken | 🚧 | `TodoEntity` + DAO + Repository |
| Fälligkeitsdaten farbcodiert (heute=rot, bald=gelb) | 🚧 | UI-Logik aus `dueDate` vs. `now()` |
| Kursverknüpfung | 🚧 | `TodoEntity.courseId` |
| Push bei Fälligkeit | 🚧 | `NotificationScheduler` wie Calendar |

### Uni Kino

| Detail | Status | Daten-Strategie |
|---|---|---|
| Featured-Film Hero + Programmliste | ✅ | unifilm.de-Scraper (Phase 2.6) |
| Genre, Dauer, Saal, Zeit | ✅ | Aus Scraper-Heuristik (v1-Pattern) |
| Reservieren-Button | ⚠️ | unifilm.de hat kein Booking-API. **Workaround**: Intent zur unifilm.de-Seite |
| Trailer-Links | 🚧 | Intent.ACTION_VIEW |
| „In Kalender packen" | 🚧 | Snapshot-Pin |

### Lerngruppen

Ohne Backend funktional als „eigene Gruppen lokal verwalten".

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
| Liste aller Klausuren | 🚧 | `exams`-Tabelle, manuell oder aus Kurs-Detail |
| Countdown-Hero zur nächsten | 🚧 | UI-Berechnung |
| Tage-Chip rot (≤7) / gelb (≤21) | 🚧 | Pattern aus Recipe E |
| Vertikale Timeline | 🚧 | LazyColumn |

### Notenübersicht

LSF-Anbindung ist out-of-scope (Shibboleth). **Workaround:** Noten lokal pflegen.

| Detail | Status | Daten-Strategie |
|---|---|---|
| GPA-Hero in Notenstufen-Farbe | 🚧 | Berechnet aus lokal eingetragenen Noten in `grades`-Tabelle, gewichtet nach LP |
| ECTS-Fortschritt (X/180 LP) | 🚧 | Summe der LP aller bestandenen Module |
| Push-Toggle bei Noteneintragung | 🚧 | DataStore-Toggle, manuelle Note-Eintragung postet sofort eine lokale Notification |
| Aktuelles Semester (Ausstehend) | 🚧 | Filter über `CourseEntity.semester` |
| Vergangene Semester aufklappbar mit Schnitt | 🚧 | UI mit `expandable` |
| Notenskala-Legende | 🚧 | Statisches Composable |

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
| Kategoriefilter (Yoga / Bouldern / Ballsport / Cardio / Kraft) | 🌱 | HSP-Scraper über öffentliche Programm-Seite |
| Auslastungsbalken | 🌱 | Aus Scraper, falls Seite Spots-Info liefert |
| „Buchen"-Button | ⚠️ | Booking hinter Login. **Workaround**: Intent zur HSP-Buchungs-Seite |

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
| HSP-Programm-Scraper | ✅ | `feature/sport/data/SportScraper.kt` (Buchung/Auslastung weiterhin offen) |
| Global Search | ✅ | `core/search/GlobalSearchRepository` kombiniert Calendar/Kurse/Mail/Learnweb/LSF/Mensa/Sport; UI `feature/search/ui/GlobalSearchScreen` |
| Glance-Home-Widgets | ✅ | `feature/widgets/` — Stundenplan (Tag + Woche), Mensa, Todos, Klausur-Countdown; Receiver in AndroidManifest registriert |

---

## Scope-Erweiterung — was zur Phase-Roadmap hinzukommt

Die ursprüngliche `HIUNI_REBUILD_PLAN.md` deckte 6 Features (Home/Calendar/Mensa/Movies/Bib/Email). Mit dieser Liste kommen dazu:

| Neu | Phase | Aufwand-Schätzung |
|---|---|---|
| Kurse-Feature (lokal verwaltet) | 2.7 | 8h |
| Aufgaben-Feature | 2.8 | 6h |
| Profil-Screen (ohne StudiCard) | 3.3 | 4h |
| Tab-Leiste-Konfiguration | 3.4 | 6h |
| Notenübersicht (lokal) | 3.5 | 8h |
| Lerngruppen (lokal) | 3.6 | 6h |
| Klausurplan (aus Kurse + manuell) | 3.7 | 4h |
| ~~Push-Center (Notifications-Tabelle)~~ | 3.8 ✅ | shipped |
| ~~Mensa-Karte (NFC-Read + Buchungshistorie)~~ | 3.9 ✅ | shipped |
| Campus-Plan | 4 Bonus | 10h |
| Hochschulsport-Scraper | 4 Bonus | 8h |
| StudiCard mit QR | 4 Bonus | 4h |
| Easter-Egg-Themes | 4 Bonus | 2h |

**Realistischer Bilanz:** Original-Plan war ~145h Core. Erweiterung bringt ~78h dazu → **223h total**. Bei 6.5h/Person/Woche × 2 Personen × 16 Wochen = 208h verfügbar. Wir sind **leicht über Plan**, kompensierbar durch:

- Bonus-Features als Phase-4-Optional (zuerst die hochwertigen ✅+🚧 saubermachen)
- Lerngruppen & Klausurplan ggf. zusammenlegen
- Mensa-Karte als Bonus statt Phase-2

**Empfehlung:** Erst die ursprüngliche Phase-2-Welle abschließen (Mensa-Daten, Settings, Home-Wire-Up, Movies), dann entscheiden welche der neuen Features eingeschoben werden vs. in Phase 3+ rutschen.
