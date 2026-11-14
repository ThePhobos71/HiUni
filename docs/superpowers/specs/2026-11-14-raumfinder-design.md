# Freie-Räume-Finder (Design)

Stand: 2026-11-14.

## Absicht

"Ich will heute, jetzt, am Samelson einen Raum haben." Man öffnet den Raumfinder,
wählt einen Campus und sieht sofort, welche Seminarräume und Hörsäle dort jetzt
frei sind, jeweils mit "frei bis". Eine aktive Suche, kein Alarm.

## Ziele

- Campus wählen (der zuletzt gewählte bleibt gemerkt), Uhrzeit "Jetzt" voreingestellt,
  eine andere Uhrzeit am selben Tag wählbar.
- Liste der jetzt freien Räume, am längsten frei oben ("frei bis 16:00",
  "frei, heute nichts mehr"), darunter abgesetzt die belegten
  ("belegt bis 14:00, danach frei").
- "Frei" stimmt für den konkreten heutigen Tag: Einzeltermine, 14-tägliche Termine,
  ausgefallene Termine und vorlesungsfreie Zeit zählen richtig.
- Nach dem ersten Laden eines Campus kommt die Suche sofort und auch offline,
  bis zum Tagesende.

## Nicht-Ziele

- Kein Hintergrund-Abruf, keine Alarme, keine Mitteilungen.
- Keine anderen Tage als heute.
- Keine Labore, Bühnen, Werkstätten, Bistros, gesperrten Räume, keine "Online-Lehre".
- Keine Aussage darüber, ob ein Raum aufgeschlossen ist.

## Rahmenbedingungen (Ist-Zustand, geprüft am 2026-11-14 mit echten Abrufen)

Alles ohne Login erreichbar.

- **Raumliste:** eine Seite mit 163 Links, jeder mit `raum.rgid=<id>` und Text wie
  `SC.A.0.09 (Großer Seminarraum) -`. Der Campus steckt im Präfix (SC, HC, KC, BC,
  SH = Schützenallee, WL = Leesterhaus, dort nur Musik-Übungsräume).
- **Wochenplan eines Raums** (`state=wplan&act=Raum&raum.rgid=<id>&show=plan&P.vx=kurz`,
  etwa 125 KB): Ohne `week` zeigt LSF die "Vorlesungszeitansicht" (`week=-1`), also
  ohne vorlesungsfreie Zeit. Mit `week=<KW>_<Jahr>` (ISO-Kalenderwoche, ohne führende
  Null, zum Beispiel `46_2026`, `1_2027`) zeigt es genau diese Woche. Ein Raum ohne
  Termine in der Woche hat keinen iCal-Link, aber das Plan-Raster (`plan_rahmen`).
- **iCal-Export** (`moduleCall=iCalendarPlan&termine=<Termin-IDs>`): Der Plan verlinkt
  ihn mit den Termin-IDs der angezeigten Woche. Jeder Termin ist ein VEVENT mit
  `DTSTART`/`DTEND` (TZID Europe/Berlin), bei Serien `RRULE` mit `FREQ=WEEKLY`
  (auch `INTERVAL=2` für 14-täglich und `BYDAY=MO,TU,WE,TH,FR` für Blöcke) oder
  `FREQ=DAILY`, `UNTIL` in UTC. Ausfälle stehen als `EXDATE`, auch bei
  Einzelterminen (Beispiel: "reserviert für Einführungswoche", dessen einziges Datum
  per EXDATE gestrichen ist). Eigenheiten: `EXDATE` mit Komma am Ende oder leer,
  `DTEND` mit `T240000`.

## Wege

1. **HTML-Raster parsen:** Das Wochenraster (rowspan-Zellen mit Klassen `plan1`,
   `plan2`, ...) direkt auslesen. Die Lage einer Zelle ergibt sich erst aus allen
   rowspans davor, und Ausfälle sind nicht verlässlich erkennbar. Verworfen.
2. **Semesteransicht plus iCal:** `week=-2` liefert alle Termin-IDs des Semesters
   (bei SC.A.1.19 rund 60), dann iCal. Korrekt, aber der Export wird unnötig groß.
   Verworfen.
3. **Wochenansicht plus iCal (gewählt):** Plan mit `week=<KW>_<Jahr>` der aktuellen
   Woche holen, daraus nur den iCal-Link nehmen, dann den Export laden und die
   Termine für das heutige Datum auswerten. Zwei Abrufe je Raum, kleine Exporte,
   und die Serienregeln samt Ausfällen kommen maschinenlesbar.

## Entwurf

- **Paket** `feature/rooms`.
- **Parser** (Jsoup und eigene Zeilenlogik, Android-frei, JVM-getestet mit gekürzten
  echten Abrufen unter `app/src/test/resources/raumfinder/`):
  - `LsfRoomListParser`: Raumliste zu Räumen mit Code, Raumart und Campus; Filter auf
    Seminarräume, Hörsäle und Arbeitsräume.
  - `LsfRoomPlanParser`: iCal-Link aus dem Wochenplan, oder "leer" bei vorhandenem
    Raster ohne Link, sonst Fehler.
  - `RoomIcsParser`: eigene, kleine VEVENT-Auswertung statt biweekly, weil biweekly an
    `T240000` und an den Komma-Listen scheitert. Liefert die belegten Zeitspannen
    eines Datums.
- **Frei/Belegt-Logik** `FreeRoomCalculator`: reine Funktion aus belegten Spannen je
  Raum und Uhrzeit zu "frei bis" oder "belegt bis, danach frei bis", samt Sortierung.
- **Repository** `FreeRoomsRepository`: lädt Raumliste, dann je Raum Plan und iCal mit
  höchstens 4 gleichzeitigen Abrufen, meldet Fortschritt, toleriert Teilfehler
  (geladene Räume werden gezeigt, fehlende benannt und einzeln nachladbar).
  Abrufe ohne Cookies, damit eine LSF-Sitzung des Nutzers nichts verändert. Der
  `PolitenessInterceptor` bremst ohnehin jeden LSF-Abruf.
- **Zwischenspeicher** im App-weiten Preferences-DataStore: je Campus ein JSON-Stand
  (Datum, Abrufzeit, Räume mit belegten Spannen, fehlende Räume), dazu der zuletzt
  gewählte Campus. Gültig nur für das gespeicherte Datum.
- **Einstieg:** eigenes Ziel "Freie Räume" im Schnellzugriff (Profil) und als
  wählbarer Tab, eine Kachel für den Home-Schnellzugriff, und ein Hinweis-Link am Ende
  des Bibliothek-Screens ("Kein Gruppenraum frei?").
- **Screen:** Kopf wie Bibliothek (Titel, Untertitel mit Datum und "Stand 13:42"),
  Campus- und Uhrzeit-Pills (handgestylte Surface und Text), Hinweis "Frei laut LSF
  heißt nicht aufgeschlossen", Liste frei, abgesetzt Liste belegt.

## Dem Bau überlassen (meine Festlegungen, Kjell hat nicht widersprochen)

- "Jetzt" voreingestellt, andere Uhrzeit am selben Tag wählbar (volle Stunden bis 20 Uhr).
- Zuletzt gewählter Campus gemerkt. Beim allerersten Öffnen wird nichts geladen,
  bis ein Campus gewählt ist.
- Nur Seminarräume, Hörsäle, Arbeitsräume. Ausgeschlossen auch Räume mit "Labor"
  (zum Beispiel Fachseminarraum mit Labortischen), "gesperrt" und "Freiluft".
- Reihenfolge: frei zuerst, am längsten frei oben; dann belegt, am frühesten wieder
  frei oben.
- Zwischenspeicher bis Tagesende mit sichtbarem Stand und manuellem Neuladen.
- Höchstens 4 Abrufe gleichzeitig, Fortschritt sichtbar, Teilfehler tolerieren.
- DataStore statt Room-Tabelle: Der Stand ist klein, kurzlebig und wird immer als
  Ganzes gelesen und ersetzt; eine Tabelle bräuchte Migration und Schema ohne Nutzen.
- Unbekannte Serienregeln (zum Beispiel `FREQ=MONTHLY`) werden vorsichtig als
  "belegt" gewertet, sobald ein Termin der Serie an diesem Tag liegen könnte.

## Tests

- Parser mit gekürzten echten Abrufen: Raumliste, Wochenplan mit und ohne Termine,
  iCal von SC.A.1.19 (KW 46) und Sonderfälle aus Hörsaal 1 und Hörsaal 4.
- Frei/Belegt-Logik als reine Funktion.
- Repository mit Fake-Quelle: Zwischenspeicher, Teilfehler, Nachladen fehlender Räume.
- ViewModel: gemerkter Campus, Uhrzeitwahl, Sortierung.

## Offen

- Semesterwechsel (Ende März, Ende September): Ob LSF in der Übergangswoche schon das
  neue Semester zeigt, ist nicht geprüft.
- Ob es Ausfälle gibt, die LSF nur im HTML und nicht als EXDATE führt, ist nicht geprüft.
