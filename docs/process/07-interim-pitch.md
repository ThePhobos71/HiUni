# 07 — Interim Presentation Demo-Skript

> Live-App-Demo statt Slide-Pitch. Stand 28.06.2026, Deadline **17.07.2026**.
> Format: Driver klickt durch die App, Navigator (oder Driver) erklärt parallel. Modular: jeder Modul-Block ist ein Slot, der je nach verfügbarer Zeit ein- oder ausgespart werden kann.

## Zeit-Annahme

Pro-Team-Zeit steht noch nicht fest — wir bauen für **flexibel ~5–8 Minuten**, jeder Modul-Block ist ein 30–45-Sekunden-Slot. Bei 5 Minuten: Hook + Mensa + Learnweb + Calendar + Outro. Bei 8 Minuten: zusätzlich Bib + Sport + Email + Tablet-Layout.

## Rollen

- **Driver:** Kjell — hat das Gerät, klickt, übernimmt die meiste Erklärung.
- **Backup:** Johann — kennt die gleichen Beats, springt ein bei Hänger / Frage / WiFi-Wackler.

## Pre-Flight-Check (max. 5 Min vor Slot)

- [ ] WiFi verbunden, Test-`curl` auf STW-Endpoint + Learnweb-Loginpage
- [ ] App ist im **Live-Modus** (nicht Demo-Modus) — Setting → Profile → eingeloggt
- [ ] Mensa-Standort = "Mensa Uni Hildesheim"
- [ ] Calendar hat mindestens 2–3 Events heute/morgen (sonst Demo-Events anlegen)
- [ ] Letztes Learnweb-Sync ist frisch (Pull-to-Refresh laufen lassen)
- [ ] Email-Inbox hat mindestens eine ungelesene Mail
- [ ] Push-Notification-Permission gewährt (sonst zeigt die Demo am Push-Beat nichts)
- [ ] Bildschirm-Spiegelung getestet (HDMI/Cast/AirPlay je nach Hörsaal)
- [ ] App einmal cold-starten — sichergehen, dass `StartupRefresher` durchlief

---

## Hook (15–30 Sekunden, am Einstieg)

> *"Wir bauen HiUni — eine native Android-App für Studierende der Uni Hildesheim. Die Idee: Mensa, Mail, Abgaben, Sport, Bib, Stundenplan — heute sind das fünf Webseiten und vier Logins. Bei uns ist es ein Screen. Wir zeigen euch das in fünf bis acht Minuten."*

Optional, je nach Stimmung: ein Satz zum v1→v2-Rebuild — *"Wir hatten letztes Jahr v1 abgegeben, dieses Jahr von Grund auf neu gebaut, Feature-First, mit Lessons aus v1 als Eingangsbedingung."*

---

## Demo-Pfad

Reihenfolge ist absichtlich: erst die Aggregator-Story (Home) — dann die technisch beeindruckenden Features (Reviews, Learnweb, Calendar) — dann breite Streuung (Bib, Sport, Email, Tablet).

### 1. Home — Aggregator-Story (~45s)

**Click-Path:**
- App cold-starten (kommt direkt auf Home)
- Hero "Nächste Vorlesung" zeigen → Countdown läuft live
- 2×2 Quick-Access-Grid antippen → Mensa-Count, Mails-Count, Aufgaben-Count
- Down-Scroll: Heutige Lessons → Uni-Kino-Karussell → Offene-Aufgaben

**Erzähl-Beats:**
- *"Tagesübersicht zur Cold-Start-Zeit unter zwei Sekunden — alle Daten aus lokalem Cache, kein Netz nötig."*
- *"`feature.home` ist der einzige Aggregator — darf alle Feature-Repos read-only injizieren. Sonst keine Cross-Feature-Imports, das ist eine Cross-Feature-Regel aus unserem ADR-1."*

### 2. Mensa + P2P-Reviews — technische Story (~60s)

**Click-Path:**
- Bottom-Nav → Mensa
- Heute → ein Gericht antippen → MealDetailSheet öffnet
- Bewertung-Button → Bewerten-Page öffnet
- Sterne setzen + "Würde wieder bestellen" antippen → Submit
- Zurück zu MealDetailSheet → `ReviewBadge` zeigt aggregierte Bewertung
- (Optional) Filter Diet → Vegan/Klima-Chip

**Erzähl-Beats:**
- *"Daten kommen live aus der STW-ON-API, gecacht in Room. 14-Tage-Window."*
- *"Bewertungen sind P2P-signiert mit Ed25519. Jede Bewertung trägt eine Signatur, die unser eigener Mini-Server `hiuni-relay` validiert bevor er sie an andere Devices weiterreicht. Trust-Modell ist Master-Web-of-Trust — kein anonymes Schreibrecht."*
- *"Wenn der Relay aus ist, bleibt die Bewertung nur auf diesem Device — Fail-Soft."*

### 3. Calendar — Snapshot + Recurrence (~30s)

**Click-Path:**
- Bottom-Nav → Calendar
- View-Switch List → Day → Week
- Mensa-Eintrag von vorhin sichtbar als `MENSA_PIN`-Source (andere Farbe)
- Optional: Add-Event-FAB → Sheet mit Date/Time-Picker + Reminder-Chips

**Erzähl-Beats:**
- *"Custom-Events plus Snapshots aus anderen Features — die Mensa-Bewertung von eben hat sich als Pin in den Kalender gelegt. Auch Learnweb-Abgaben spiegeln sich hier automatisch."*
- *"Recurrence ist als RFC-5545-light implementiert, Reminders schedulen einen `NotificationReceiver` über den AlarmManager."*

### 4. Learnweb — CAS-SSO + Abgaben (~45s)

**Click-Path:**
- Bottom-Nav / Drawer → Learnweb (oder über Settings → Learnweb-Login)
- Falls noch nicht eingeloggt: Login-Button → WebLoginActivity öffnet CAS-Seite → Login → kommt zurück
- Kurs-Liste → ein Kurs antippen → Assignment-Liste mit Deadline-Countdown

**Erzähl-Beats:**
- *"Uni-Hi Learnweb hat kein öffentliches API. Wir gehen über CAS-SSO mit Multi-Redirect und Cookie-Carrying — `WebLoginActivity` + `CasSession` + `CasCookieStore` machen das. Danach scrapen wir Kurs-Liste und Assignment-Liste."*
- *"Wir spiegeln Assignment-Deadlines in den Kalender und schedulen Push-Reminders."*

### 5. Bibliothek — Live-Auslastung (~30s, optional bei Zeit)

**Click-Path:**
- Drawer/Tab → Bib
- 6 Räume mit Verfügbarkeits-Balken
- Lieblings-Raum markieren → Push-Toggle erklären

**Erzähl-Beats:**
- *"Daten aus dem ubwww-Scraper — wir cachen Live-Auslastung und können bei Lieblings-Raum-Freiwerden pushen. Buchung selbst öffnet die ubwww-Website im Browser, weil's kein öffentliches Booking-API gibt."*

### 6. Sport — HSP-Programm (~30s, optional)

**Click-Path:**
- Drawer/Tab → Sport
- Kategorie-Filter → Yoga / Bouldern / Cardio
- Eintrag antippen → Detail mit "Buchen"-Button (öffnet HSP-Website)

**Erzähl-Beats:**
- *"Programm-Liste vom Hochschulsport-Scraper. Buchung selbst hinter Uni-Login, das öffnen wir extern."*

### 7. Email — IMAP (~30s, optional)

**Click-Path:**
- Drawer/Tab → Email
- (Optional) Fingerabdruck-Gate zeigen
- Inbox, Swipe-Gesture demonstrieren (Archive / Mark-Unread)
- Detail öffnen
- Reply-Button (nicht abschicken)

**Erzähl-Beats:**
- *"Jakarta-Mail-IMAP gegen den Uni-Server, Credentials in EncryptedSharedPrefs mit Self-Healing-Reset falls Android das Keystore mal verliert."*
- *"Swipe-Aktionen sind in den Settings konfigurierbar, Sent-Folder via SPECIAL-USE-Discovery."*

### 8. Tablet-Layout (Polish-Beat, ~15s)

**Click-Path:**
- Falls Tablet zur Hand: Tablet anschmeißen, App öffnen → Rail statt Bottom-Nav
- Falls kein Tablet: Emulator in Tablet-Konfiguration zeigen, oder kurz Screenshots
- Optional: Reorder-Demonstrieren im Settings → Tab-Layout

**Erzähl-Beats:**
- *"`AdaptiveScaffold` entscheidet zur Laufzeit zwischen Bottom-Nav, Rail und Drawer basierend auf der WindowSizeClass. Kein Code-Duplikat — gleiche Composables, andere Schale."*

---

## Outro (30 Sekunden)

> *"Status: ~14 Features shipped, plus das P2P-Reviews-Feature als technischer Bonus. Bis zum 31.08. kommt Polish — Multi-Device-Testing, Performance-Pass, finale Process-Doku und optional ein Video."*
> *"Quellcode im Repo, ADRs in `docs/adr`, AI-Workflow-Disclosure in `AI_USAGE.md`. Fragen?"*

---

## Notfall-Sätze (wenn was hängt)

| Situation | Was sagen |
|---|---|
| WiFi weg, Mensa lädt nicht | *"Cache funktioniert — letzter erfolgreicher Sync war heute Morgen, also zeigen wir die gecachten Daten. Im Live-Fall würde Pull-to-Refresh sofort aktualisieren."* |
| CAS-Login zickt | *"Login-Sessions laufen aus, das ist erwartet — `LoginSyncOrchestrator` würde das in der Produktion abfangen und neu anstoßen. Für den Vortrag überspringen wir das."* |
| App crasht | *"Cold-Restart, dauert <2 Sekunden — wir hatten in v1 dasselbe und haben den Lifecycle für v2 sauberer aufgesetzt."* (Dann ehrlich weiterklicken, nicht dramatisieren.) |
| Backup-Driver-Übernahme | Driver sagt "Johann übernimmt kurz" — Johann nimmt das Gerät, klickt sein Schwerpunkt-Modul (Mensa / Bib / Sport). |

## Was wir bewusst NICHT zeigen

- **Annotated Prompt Log** und **AI-Workflow-Details** — sind in der Process Doc, nicht in der Demo. Erwähnen genügt.
- **Federation-Setup mit echtem Relay** — zu fragil für Live, wird im Video bei Bedarf gezeigt.
- **`hiuni-relay/`-Docker-Setup** — keine Backend-Demo, ist Bonus-Material für die Process Doc.
- **Alle Settings-Sub-Screens** — wir zeigen Settings nur falls Frage kommt.

## Was wir vorbereiten falls Hesenius fragt

- *"Welche Architektur?"* — ADR-1 (Feature-First), ADR-2 (Single-Activity Compose), ADR-3 (Hilt), ADR-4 (Single Room DB), siehe [02-architecture-overview.md](02-architecture-overview.md).
- *"Wie testet ihr?"* — Repository-Tests als Pattern: `CalendarRepositoryImplTest`, `MensaDtosTest`, `LearnwebICalParserTest`.
- *"Was kommt noch?"* — Phase 6, siehe [Engineering Log](03-engineering-log.md#phase-6--final-polish--abgabe-august-2026--geplant).
- *"Wie viel AI?"* — siehe [AI-Workflow](04-ai-workflow.md). Ehrliche Antwort: ~80% Roh-Code AI-generiert, Architektur + Scope + Reviews menschlich, mit konkreten AI-Versagen-Cases dokumentiert.

## Probelauf-Plan

Vor dem 17.07 mindestens **zwei volle Durchläufe**:

1. **Test-Lauf zu zweit** (eine Woche vor Termin, ~10.07) — auf Kjells Gerät, in ruhigem Raum, mit Stoppuhr. Anpassungen am Skript.
2. **General-Probe am Tag davor** — am echten Setup (oder Spiegelung simulieren), Backup-Driver-Übernahme einmal trainieren.

_TODO_: Termine fixieren sobald Pro-Team-Zeit bekannt ist.
