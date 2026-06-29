# 06 — Team and Contributions

> Wer macht was, wie wir zusammenarbeiten, wie der gemeinsame Git-Account zustande kam. Wichtig für die Pair-Defense.

## Team

- **Kjell Karstens** — Mat-Nr. _TODO_, Mail [khk@duck.com](mailto:khk@duck.com) (privat)
- **Johann Brosthaus** — Mat-Nr. _TODO_, Mail _TODO_

### Konstellations-Wechsel v1 → v2

HiUni v1 (Vorjahres-MSE-Abgabe) hatten wir zu dritt gebaut: Kjell + Johann + ein dritter Kommilitone, der dieses Jahr nicht mehr im Modul ist. Für v2 sind wir zu zweit — Kjell und Johann. Das passt zur Kurs-Regel "Groups of two" und ist mit Hesenius abgestimmt (siehe One-Pager-Abgabe vom 31.05.2026).

## Arbeitsmodell — Pair-Programming an einem Laptop

Wir haben durchgehend in Pair-Programming-Sessions an **einem** Laptop (Kjells MacBook) gearbeitet. Die Rolle hat zwischen **Driver** (tippt) und **Navigator** (denkt, reviewt, schlägt Edge-Cases vor) je nach Feature und Energie gewechselt. Beide haben bei jedem Modul mitgehört, mitdiskutiert und mit-entschieden.

**Warum ein Laptop?** Pragmatisch — Kjell hatte das Android-Studio-Setup bereits konfiguriert (Java 17, SDK Platform 36, Emulator, signed Debug-Keys), Johann hat keinen separaten Mac mit identischem Setup. Statt das nochmal aufzusetzen, haben wir Pair-Sessions im selben Raum / am selben Tisch gemacht.

**Warum ein Git-Account?** Konsequenz aus dem One-Laptop-Setup. Wir hatten zu Beginn keinen zweiten SSH-Key/GitHub-Account auf dem Mac eingerichtet. **Ab Mitte Juli 2026** wird das nachgezogen: Jeder Commit aus einer gemeinsamen Pair-Session bekommt einen `Co-Authored-By: Johann Brosthaus <…>`-Trailer, damit Git-History und Realität konvergieren.

## Modul-Schwerpunkte

| Modul | Lead-Navigator | Co-Driver/Reviewer |
|---|---|---|
| Foundation (Theme, DI, DB, Nav) | Kjell | Johann |
| Calendar (CRUD, Notifications, Recurrence) | Kjell | Johann |
| **Mensa** (STW-API, Meals, Filter, Detail-Sheet) | **Johann** | Kjell |
| Movies (unifilm-Scraper) | Kjell | Johann |
| **Bib** (ubwww-Scraper, Räume, Push) | **Johann** | Kjell |
| Email (Jakarta Mail, IMAP, Swipe, Autocomplete) | Kjell | Johann |
| Learnweb (CAS-SSO, Assignments) | Kjell | Johann |
| **Sport** (HSP-Scraper) | **Johann** | Kjell |
| Reviews + Federation (P2P, Ed25519, Relay) | **Kjell** | Johann (Trust-Modell-Review) |
| Settings + Profile + Design-System | gemeinsam | gemeinsam |
| Tablet-Optimierung | gemeinsam | gemeinsam |

## Defense-Topics — wer erklärt live was

Für die Pair-Defense (Hesenius-Regel: jeder muss jedes Modul erklären können). Hier markieren wir, wer welches Modul **mindestens** auf Architektur-Level erklären kann.

### Johann — Defense-Ready für:

- **Mensa** — STW-ON-API, Cache-Strategie, Meal-Aggregation, Diet-Filter, Pin-to-Calendar-Flow
- **Bibliothek** — ubwww-Scraper-Logik, Session-Fixation-Bug-Workaround, Auslastungs-Color-Mapping
- **Sport** — HSP-Scraper, Kategoriefilter

### Kjell — Defense-Ready für:

- **Reviews + Federation** — recipeHash-Konstruktion, Ed25519-Signaturen, ReviewRepository-Aggregation, Relay-Sync-Protokoll
- **Learnweb** — CAS-SSO-Flow, Assignment-iCal-Parsing, Calendar-Spiegelung
- **Email** — Folder-Discovery, Swipe-Gesten-Modell, Credentials-Self-Healing
- **Calendar** — Single-Recurrence, Reminder-Notification-Scheduling, Source-Kind-System (USER/MENSA_PIN/MOVIE_PIN)

### Beide gemeinsam Defense-Ready für:

- **Architektur-Big-Picture** — ADRs 1–7, Cross-Feature-Regeln, Feature-First-Begründung
- **AI-Workflow** — Architect-First-Modell, AI-Versagen-Fälle (siehe [04-ai-workflow.md](04-ai-workflow.md))
- **Build-und-Run** — siehe [05-build-and-run.md](05-build-and-run.md)

## Pair-Defense-Probe (vor 17.07)

Vor der Interim Presentation machen wir 1–2 Sessions:

1. Kjell stellt Fragen zu Johanns Modulen — Johann erklärt ohne Spickzettel.
2. Johann stellt Fragen zu Kjells Modulen — Kjell erklärt ohne Spickzettel.
3. Beide testen Architektur-Big-Picture wechselseitig (warum Hilt, warum Single-DB, warum Compose-only).

Wenn jemand bei einer Frage hängt → Modul nachholen, dann erneut Probe.

## Aufteilung non-code (Pitch, Doku, Video)

- **Elevator Pitch (abgegeben 22.05):** _TODO wer_
- **One-Pager (abgegeben 31.05):** _TODO wer_
- **Interim Presentation (17.07):** _TODO Aufteilung_
- **Process Documentation:** Skelett von Kjell mit AI-Hilfe, Inhalt-Sektionen wo "_TODO_" steht: Kjell+Johann gemeinsam.
- **Final App:** Code wie bisher (Pair).
- **Optional Video:** _TODO wer schneidet_
