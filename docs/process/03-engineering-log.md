# 03 — Engineering Log

> Chronologisches Entwickler-Tagebuch. Pro Phase: was gebaut, welche Entscheidungen, welche Stolpersteine, was pair-programmiert. Aufgegliedert nach den Commit-Wellen (siehe `git log --all`).

## Phase 0 — Rebuild aus v1 (vor 23.05.2026)

**Ausgangslage.** HiUni v1 war unsere MSE-Kursabgabe im Vorjahr — Team Kjell + Johann + ein dritter Kommilitone, der dieses Jahr nicht mehr im Modul ist. v1 hatte bereits MVVM und Room, lief aber als monolithische Struktur mit kleinerem Feature-Set (Mensa, Calendar, Mail, Movies, Bib), ohne Tablet-Support und ohne klare Feature-Grenzen — alles direkt verdrahtet, viel geteilter State. Wir wussten konkret, was wackelte: Login-Sessions die in unklaren Zuständen festfraßen (siehe `docs/UBWWW_BUG_SESSION_FIXATION.md`), kaum Wiederverwendung von Logik zwischen Features, schwer testbar.

**Entscheidung Frühjahr 2026.** Hesenius hat im Kurs-Jahrgang 2025/26 alle Teams aufgerufen, ihre Vorjahres-App **von Grund auf neu zu bauen**, um die diesjährigen Lehrinhalte (Compose, sauberer Lifecycle, Hilt, Testing) konsequent anzuwenden. Wir folgen dem.

**Vorbereitung-Artefakte vor erstem Commit:**

- `HIUNI_KONZEPTE.md` — Vision für v2, was rein soll, was raus.
- `HIUNI_LIBRARIES.md` — Library-Liste mit Begründung pro Lib (gegen die "no external libraries"-Default-Linie des Kurses abgewogen).
- Ein initialer Phasenplan steuerte die Phase-1–6-Wellen.
- `HIUNI_REFACTOR_PLAN.md` — was wir aus v1 wiederverwenden (z.B. `CredentialsManager`-Self-Healing-Pattern) und was komplett wegfällt.
- `Uni Hi.html`-Mock — High-Fidelity-Design-Referenz, in Compose-Theme übersetzt (siehe AI_USAGE.md 23.05.2026).
- ADRs 1–7 — Architektur-Entscheidungen, die das Foundation-Setup vorausentschieden haben.

**Was wir mitgenommen haben aus v1:**

- `CredentialsManager`-Self-Healing-Reset-Pattern (Wissen, nicht Code-Copy)
- Bekannte Schmerzen: ubwww-Session-Fixation, STW-API-Schema-Drift, LSF-Shibboleth ohne öffentliches API
- Workaround-Designs: STW-NFC nicht spec'd → lokales Guthaben-Tracking, ubwww-Booking kein API → Intent zur Website.

**Was wir bewusst NICHT mitgenommen haben:**

- v1-Codebase wurde **nicht copy-pasted**. Jede Klasse wurde neu geschrieben, oft mit AI-Hilfe, immer im Pair-Review.
- v1-Modulstruktur (monolithisch, geteilter State) — durch Feature-First-Packages mit Cross-Feature-Regeln ersetzt.

_TODO Kjell/Johann_: Konkretisieren — was war an v1 UI-technisch (XML vs Compose?), DI-technisch (Hilt vs manuell?), Test-mäßig schon vorhanden, was wirklich anders ist in v2?

## Phase 1 — Foundation (23.05.2026)

**Was wurde gebaut:**

- Gradle-Setup mit Version Catalog (`gradle/libs.versions.toml`)
- Theme + Color + Typography aus Design-Mock übertragen
- Hilt-Module (Database, Network, DataStore)
- Adaptive Scaffold (Bottom/Rail/Drawer)
- Stub-Screens für alle 8 Hauptdestinationen
- Calendar-Foundation mit echter CRUD (CustomEventEntity, DAO, Repository)
- ADRs 1–7 verschriftlicht

**Pair-Schwerpunkt:** Architektur-Diskussion (welche Layer, welche DI-Lib, KSP1 vs KSP2). Code-Generierung primär Kjell als Driver, Johann als Navigator. ADRs entstanden im Gespräch, KI hat Drafts vorgelegt.

**Stolpersteine:**

- KSP2 brach Room-Annotation-Processing — Rollback auf KSP1, dokumentiert in ADR-0007.
- `CredentialsManager` Self-Healing-Reset-Pattern aus v1 wiederverwendet — funktioniert in Theorie, Multi-OEM-Test verschoben auf Phase 2.

## Phase 2.x — Feature-Welle Calendar/Mensa/Settings/Home (23.–24.05.2026)

**Was wurde gebaut:**

- **Calendar (2.1):** List/Day/Week-View, AddEdit-Sheet, Reminder-Chips, Notification-Scheduling
- **Mensa (2.2):** STW-ON-API, MealEntity + DAO, Pull-to-Refresh, Pin-to-Calendar
- **Settings (2.4):** sectioned Cards, Credentials-Card, Mensa-Standort-Picker
- **Home Wire-Up (2.3):** Mock-Daten durch Live-Bindings (Next-Event-Banner, Mensa-Subtitle mit Open-Status)

**Pair-Schwerpunkt:**

- **Johann lead bei Mensa:** STW-ON-API-Live-Inspektion, Erkennen dass `price` (Singular) statt `prices` (Plural), `tags.{categories,allergens,additives,special}` statt `notes`-Array, Location-ID 150 für Mensa Uni Hildesheim verifiziert.
- **Kjell lead bei Calendar:** Reduktion von Hour-Grid auf Agenda-List im Review-Schritt (pragmatisch: funktional vor visuell), Range-Fenster 6 Monate statt 12 wegen Stuttering auf Cold Devices.

**Stolpersteine:**

- AI-Erstgeneration des Mensa-DTO war falsch (siehe oben). Fix erforderte Live-API-Call.
- `FlowRow` ist `@ExperimentalLayoutApi` — Opt-in pro Composable, nicht global.

## Phase 2.5–2.8 — Movies / Bib / Email / Sport (Ende Mai 2026)

**Was wurde gebaut:**

- **Movies:** unifilm.de-Scraper, Trailer-Links, Featured-Film-Hero
- **Bib:** ubwww-Scraper mit Auslastungsbalken, Lieblings-Räume mit Push
- **Email (Phase 1):** Jakarta Mail IMAP, Ordner-Discovery, Posteingang
- **Sport:** HSP-Scraper (Bonus)

**Pair-Schwerpunkt:**

- **Johann lead bei Bib + Sport** (sein Modul-Schwerpunkt laut Team-Sektion).
- **Kjell lead bei Email** wegen Credentials-Komplexität (`CredentialsManager` aus Phase 1, Session-Fixation-Bug aus v1 dokumentiert in `docs/UBWWW_BUG_SESSION_FIXATION.md`).

**Stolpersteine:**

- Session-Fixation in ubwww-Scraper — siehe `docs/UBWWW_BUG_SESSION_FIXATION.md` für Reproducer + Workaround.
- Email-Foldername-Discovery (`Sent` vs `Gesendete Objekte` vs `INBOX.Sent`) — SPECIAL-USE-Discovery statt Hardcoding.

## Phase 3 — Email-Polish / Learnweb / Performance (Ende Mai bis Juni 2026)

**Was wurde gebaut:**

- Email: Reply/Forward, Volltextsuche, Delete/Archive, Swipe-Gesten konfigurierbar, Autocomplete, Fingerabdruck-Schutz, "Mails nur lokal löschen"-Modus
- Learnweb: CAS-SSO-Login, Kurs-Liste, Assignment-Deadlines mit Calendar-Spiegelung + Push
- Recurrence (RFC 5545 light) für Calendar
- Tablet-Optimierung (Rail-Layout, FullWidth-Opt-in, 840dp-Content-Cap)
- Performance: ViewModel-StateFlows von 5s auf 60s WhileSubscribed, Random-Delay vor LSF/CAS-Hits, Sport- und Movie-Poster-Vorwärmen beim Cold-Start

**Pair-Schwerpunkt:**

- Tablet-Optimierung war Pair-Heavy: jeder Bildschirm zu zweit durchgegangen.
- Learnweb-CAS-SSO Kjell lead — komplexes Multi-Redirect mit Cookie-Carrying.

**Stolpersteine:**

- "Kein LSF-Login bei jedem App-Open" — initialer Sync triggerte unnötige Logins, Fix: Login-Lazy + Cache-Gate.
- "Keine voreilige Login-abgelaufen-Notification" — Bio-Toggle als Gate hinzugefügt.
- App-Icon: mehrere Iterationen ("Mortarboard statt schlichtem Hi-Letter", "Hi-Mark + Semester-Unlock-System") — Design-Diskussionen ohne KI-Drift.

## Phase 4 — Reviews & Federation (Ende Juni 2026, current)

**Was wurde gebaut:**

- `:shared-events`-Modul mit Event-Datenklassen + Canonical-Form, Tink-Ed25519
- recipeHash mit Nährwert-Fingerprint (Tuple-Key Name+Location+Fingerprint)
- ReviewRepository mit Aggregation + Submit + Retract
- MyKeyManager mit Android-Keystore-Wrapping
- EventValidator (Signatur/Trust/Spam)
- ReviewBottomSheet + dedizierte Bewerten-Page (entgegen erster Sheet-Stacking-Lösung)
- ReviewBadge in Meal-Detail
- **hiuni-relay** als separates Ktor-Modul: SQLite-EventStore, WebSocket `/sync` mit Hello/Event/Events-Protokoll, Dockerfile + Caddy-Sidecar
- Relay-Federation-Spec (Master-WoT, RelayAnnounceEvent) als Design-Doc

**Pair-Schwerpunkt:**

- **Kjell lead Reviews + Federation** (laut Team-Sektion sein Schwerpunkt).
- Johann reviewed P2P-Trust-Modell und manuelle Tests auf seinem Device.

**Stolpersteine:**

- BottomSheet auf BottomSheet stack'te nicht sauber → eigene Bewerten-Page.
- Emojis im Review-Picker entfernt (siehe Memory-Regel "no emojis in UI"), durch Material-Icons + ★/☆ ersetzt.
- `wouldOrderAgainPct` truncatete statt zu runden — Review-Catch von Kjell.
- BuildKit-only `Dockerfile.dockerignore` → top-level `.dockerignore` als Fallback.

## Phase 5 — Polish, Doku, Interim Pitch (Juli 2026 — laufend)

**Was geplant ist vor dem Interim am 17.07.2026:**

- **Process Documentation Skelett** — dieses Dokument, mit gefüllten Sektionen für Architecture / Engineering Log / AI-Workflow (aus Code+History abgeleitet) und offenen TODOs für Sicht-Teile (Motivation/Reflexion).
- **Interim-Pitch-Outline** — 5-Minuten-Format: Problem (1min), Lösung (1min), Status (1min), Live-Demo-Plan (1min), nächste Schritte (1min). Aufbauend auf One-Pager + Elevator-Pitch.
- **Pair-Defense-Probe** — wir gehen pro Modul gegenseitig durch, dass jeder Modul-Owner sein Modul (siehe [06-team-and-contributions.md](06-team-and-contributions.md)) ohne Spickzettel auf Architektur-Ebene erklären kann.
- **Co-Authored-By-Trailer** — ab erstem Pair-Commit nach Doku-Skelett wird Johann als Co-Author getrailt, damit Git-History und Pair-Realität konvergieren.
- **Polish-Pass auf bestehenden Features** — Tablet-Layout finalisieren, Edge-to-Edge-Issues fixen wo noch sichtbar, Dark-Mode-Token-Konsistenz prüfen, App-Icon-Vorschau-Crash-Regressionen testen.
- **Demo-Pfad festlegen** — welche User-Journey präsentieren wir live? Vorschlag: Cold-Start → Home → Mensa-Pin-to-Calendar → Bewertung abgeben → Learnweb-Sync mit CAS-Login → Push-Notification.

## Phase 6 — Final Polish + Abgabe (August 2026 — geplant)

**Was geplant ist bis zum 31.08.2026:**

- **Bug-Bash auf Multi-Devices** — Pixel 7a (Phone), Tablet von Johann, Emulator-Pool (API 26 / 30 / 34). Hesenius will die App auf mehreren Devices laufen sehen.
- **Multi-OEM-Test für `CredentialsManager`** — Self-Healing-Reset-Pattern auf Samsung + Pixel + Xiaomi testen (Phase 1 hatte das als TODO offen gelassen).
- **Performance-Pass** — Cold-Start-Profiling (Macrobenchmark wenn Zeit reicht), Memory-Leak-Check (LeakCanary läuft im Debug), ANR-Audit.
- **Doku-Final** — alle TODO-Stellen in dieser Process Doc geschlossen, Cross-Refs gegengeprüft, Engineering Log auf finalen Stand gebracht.
- **AI Reflection Report final** — siehe [04-ai-workflow.md](04-ai-workflow.md), mit konkreten Annotated-Prompt-Beispielen aus Phase 4–6.
- **Optional Video (Bonus-Punkte)** — bis 5 Minuten, Aufbau gemäß Kursvorgabe: Problem+Lösung (1min), Feature-Walkthrough (2min), AI-Use (2min). Format-Entscheidung: Screen-Recording + Voiceover oder On-Device-Kamera mit Sprecher? Sollte vor dem 17.08 stehen.
- **Final-App-Build + Signed-APK** — Debug-APK reicht laut Vorgabe; falls Signed-APK gewünscht, neues Keystore generieren und Build-Anleitung in [05-build-and-run.md](05-build-and-run.md) erweitern.
- **Abgabe-Checkliste:** Repo struktur-final, README aktualisiert, AI_USAGE.md letzter Stand, MIT-Lizenz finalisieren (oder andere), `docs/process/` vollständig, Build-Anleitung getestet auf fremdem Rechner.

## Fortlaufendes "Lebende-Diary"-Format

Ab Phase 5 schreiben wir nach jeder Pair-Session einen Mini-Eintrag direkt unten anhängen — Datum, was gemacht, wer driver, was gelernt. Verhindert dass das Diary am 30.08 als rückwirkende Konstruktion entsteht.

### YYYY-MM-DD — Session-Titel (Driver: X, Navigator: Y)

_Template — Beispiel-Eintrag löschen wenn echte Sessions kommen._

- Was wir gemacht haben: ...
- Was wir gelernt haben: ...
- Was offen blieb: ...
