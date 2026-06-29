# 04 — AI Workflow + Reflection Report

> Wie wir mit AI gearbeitet haben, was AI gemacht hat, wo AI versagt hat, was wir gelernt haben. Vollständige Disclosure nach dem AI-Policy-Prinzip des Kurses.

## TL;DR

- **Eingesetztes Werkzeug:** Claude Code (Anthropic), Modell Opus 4.x (1M context).
- **Arbeitsmodell:** "Architect-First, AI-Generate, Human-Review". Wir definieren Architektur (ADRs), Scope (FEATURES.md) und Acceptance-Kriterien manuell. AI generiert daraus Code-Skelette und Boilerplate. Jeder Code wird im Pair-Programming gelesen, getestet, in den meisten Fällen nachjustiert, bevor commit.
- **Anteilsschätzung:** ~80% des Roh-Codes ist AI-generiert. ~100% der Architektur-Entscheidungen, Library-Wahl, Feature-Scope-Entscheidungen und Edge-Case-Fixes sind menschlich. Code-Reviews fingen mehrere AI-Fehler ab, dokumentiert unten.
- **Vergleich v1 ↔ v2:** HiUni v1 (Vorjahres-MSE-Abgabe) wurde **ohne AI** geschrieben. v2 ist mit AI als Generator/Reviewer gebaut. Der Vergleich ist eine eigene Reflexion wert — siehe [Sektion v1↔v2](#vergleich-v1--v2).
- **Lebende AI-Disclosure pro Session:** [`AI_USAGE.md`](../../AI_USAGE.md) (Repo-Root) — wird pro Commit-Welle aktualisiert. Dieses Dokument ist die zusammenfassende Reflexion.

## Was AI gemacht hat

- Boilerplate (Room-Entities/DAOs, Hilt-Module, Compose-Skelette, ViewModel-Patterns)
- Erste Drafts von ADRs (Wortlaut, nicht Entscheidung)
- Refactoring-Vorschläge nach manuellem Review-Hinweis ("dieser Code ist mir zu lang")
- Test-Cases aus Specs (z.B. `CalendarRepositoryImplTest`, `MensaDtosTest`, `LearnwebICalParserTest`)
- Code-Reviews und Bug-Catches durch automatisierte Review-Aufrufe (`/code-review` Skill)
- Erste Drafts dieser Process-Doku-Sektionen

## Was AI NICHT gemacht hat

- **Library-Wahl:** `HIUNI_LIBRARIES.md` mit jeder Library begründet — Wahl von Kjell/Johann, AI hat nur Argumente strukturiert.
- **Architektur-Entscheidungen:** Feature-First-Packages (ADR-0001), Single-Activity (ADR-0002), Hilt (ADR-0003), Single-AppDatabase (ADR-0004), No-Unified-Calendar (ADR-0006), AGP-8.7-Stable (ADR-0007) — alles im Team-Gespräch entschieden.
- **Feature-Scope-Definition:** `docs/FEATURES.md` mit Status pro Detail (shipped/planned/bonus/blocked) — Scope-Entscheidungen sind menschlich, AI hat tabelliert.
- **Workaround-Designs für fehlende APIs:** STW-NFC nicht spec'd → lokales Guthaben-Tracking. ubwww-Booking kein API → Intent-zur-Website. LSF kein API → manuelle Kurs-Eingabe. Diese Workarounds sind Produkt-Entscheidungen.
- **P2P-Reviews-Konzept:** "Gun.js-Spirit", Master-WoT, Ed25519-Signaturen, recipeHash mit Nährwert-Fingerprint — Konzept von Kjell, AI hat es in Code übersetzt.
- **Manuelle QA auf realen Geräten:** Tablet-Layout-Bugs, Edge-to-Edge-Issues, App-Icon-Crashes, Mensa-API-Schema-Drift (`tags.special` migration) — alles findet keine AI ohne Gerät.
- **API-Keys oder Credentials handhaben** (siehe `AI_USAGE.md`).

## Annotated Prompt Log

> 3-5 repräsentative Beispiele aus den Sessions. Format: Prompt → AI-Output (Auszug) → Review-Verdict.

### Beispiel 1 — Mensa-DTO erste Iteration (23.05.2026)

**Prompt (sinngemäß):** *"Baue ein Kotlin-Data-Class für die STW-ON-Mensa-API. Hier ist ein Sample-JSON: [...]."*

**AI-Output (Auszug):** `data class MealDto(val prices: List<Double>, val notes: List<String>, ...)`

**Review-Verdict: FALSCH.** Live-API-Call zeigte:

- Feld heißt `price` (Singular), nicht `prices` — und ist ein String mit Punkt-Separator.
- `notes` existiert nicht — strukturierte `tags.{categories,allergens,additives,special}` stattdessen.

**Konsequenz:** Refactor nach Live-API-Inspektion. **Lesson Learned:** Bei externen APIs immer Live-Call validieren, nie nur dem JSON-Sample-Inferenz vertrauen.

_Hinweis_: Der exakte Prompt-Wortlaut ist aus der Claude-Code-Session vom 23.05.2026 nicht mehr verlässlich rekonstruierbar — der oben angegebene Wortlaut ist sinngemäße Wiedergabe aus dem `AI_USAGE.md`-Eintrag dieses Tages.

### Beispiel 2 — `wouldOrderAgainPct` Aggregation (Juni 2026)

**Prompt (sinngemäß):** *"Aggregiere alle Review-Events für ein Gericht und berechne `wouldOrderAgainPct` als Prozent der positiven Stimmen."*

**AI-Output (Auszug):** `val pct = (positive * 100) / total`

**Review-Verdict: TRUNCATE-Bug.** Integer-Division → `0.99` wurde zu `0`, `1.50` zu `1`. Erst beim Test-Schreiben aufgefallen.

**Fix-Commit:** `c5a7589 fix(reviews): wouldOrderAgainPct rounds (not truncates) + tighten sig assertion`

**Lesson:** AI generiert "korrekte" Berechnungen, die Edge-Cases übersehen. Test-First (oder Test-Sofort) fängt das.

### Beispiel 3 — Calendar-Day-View Initial-Design (23.05.2026)

**Prompt (sinngemäß):** *"Baue die Day-View für den Kalender. Vorhandene Events, 8–22 Uhr, sollen positioniert sein."*

**AI-Output:** Hour-Grid mit Position-Calculation pro Event (komplexer Composable mit Y-Offset-Berechnung).

**Review-Verdict: OVER-ENGINEERED.** Zu komplex für Phase 2, Visuell-vor-Funktional. Im Pair-Review reduzierte Kjell das auf eine simple Agenda-List.

**Lesson:** AI tendiert zu vollständigen Implementierungen, die das Endprodukt vorwegnehmen. Pragmatisch zurückschrauben gehört zur Review-Pflicht.

### Beispiel 4 — STW-API-Schema-Drift `tags.special` (Juni 2026)

**Hintergrund:** Im Mai hatte AI den Mensa-Parser auf das damals aktuelle STW-ON-Schema gebaut: `special_tags: List<String>` als Top-Level-Feld pro Meal. Funktionierte. Im Juni änderte das STW das Schema — `special_tags` wurde gemovt nach `tags.special` (eingebettet in das strukturierte `tags`-Objekt).

**Auswirkung:** Mensa-Feature crasht nicht, aber die "Bio / Klima / Geflügel"-Filter-Chips waren plötzlich leer für alle Gerichte. AI hätte das nicht von selbst gefunden — das Schema wirkte für sie ja noch konsistent zum Trainings-Stand.

**Wer hat's gefangen:** Johann beim Pair-QA-Pass auf der Mensa-Seite. "Die Filter zeigen heute komisch wenig" → kurzes `curl` auf STW-Endpoint → Schema-Diff sichtbar.

**Fix-Commit:** `f60b5ed fix(mensa): STW-API hat special_tags moved nach tags.special`

**Lesson:** Externe APIs sind kein stabiler Vertrag, auch wenn AI sich das so vorstellt. Pair-QA mit dem realen Endpoint ist Pflicht. Wir hätten einen Smoke-Test schreiben können, der periodisch das Schema gegen ein gespeichertes Sample diff't — als Idee fürs Final-Polish.

### Beispiel 5 — Cold-Start Pop-Ins, die AI nicht sehen kann (Juni 2026)

**Hintergrund:** Erste Implementation des Home-Screens lud Movie-Poster lazy beim Scrollen ins Karussell. AI-Code war "richtig" — Coil + Crossfade — und sah auf dem Emulator-Standbild fein aus.

**Auswirkung:** Auf realem Pixel 7a beim Cold-Start war das Karussell sichtbar leer für ~400ms, dann poppten die Poster mit zu kurzer Crossfade-Dauer ein. Wirkte stutterig.

**Wer hat's gefangen:** Kjell beim Live-Test. AI bekam das Symptom beschrieben ("Pop-In beim Cold-Start, sichtbar auf Pixel 7a, ca. 400ms"), nicht den Code.

**Fix-Strategie (gemeinsam entschieden):**

1. `StartupRefresher` zieht Movie-Liste + Poster-Cache schon **vor** dem ersten Home-Frame an (siehe `core.startup.StartupRefresher`).
2. Crossfade-Dauer verlängert für weicheren Übergang.

**Fix-Commits:**
- `64390d7 feat(startup): Movie-Poster vorladen + Crossfade länger gegen Pop-Ins`
- `f7d5e23 feat(startup): Sport zusätzlich beim Cold-Start vorwärmen`

**Lesson:** "AI kann nicht sehen" ist nicht-trivial — visuelle UX-Stutters, Layout-Sprünge bei dynamischen Inhalten, Edge-to-Edge-Surfaces, das alles findet nur Augen am Gerät. Pair-QA mit echtem Phone + echtem Tablet ist die zweite Hälfte des Workflows.

### Beispiel 6 — _Optional, falls Kjell/Johann noch was Konkretes einfällt_

Slot für ein freies Beispiel aus eurer Erinnerung — z.B. ein besonders gelungener Prompt, ein peinliches Versagen, eine Workflow-Verbesserung, die ihr im Lauf entdeckt habt.

## AI Reflection Report

### Was hat funktioniert

- **Boilerplate-Beschleunigung:** Room-Entities, Hilt-Module, Compose-Skelette — wir hätten ohne AI das Foundation-Phase-1-Setup nicht in einem Tag durch.
- **Refactor-Vorschläge nach klarem Mandat:** "Mach das kürzer" / "Extrahiere das in ein Composable" — AI gut.
- **Test-Boilerplate:** MockK + Turbine-Skelette für Repository-Tests waren schnell.
- **Doku-Drafts:** Diese Doku-Sektionen entstehen schneller mit AI-Drafts, die wir dann eigenhändig kuratieren.

### Was hat nicht funktioniert

- **Externe APIs:** AI rät Schema-Felder falsch, wenn sie nicht im Trainings-Set sind (STW-ON, ubwww). Immer Live-Call.
- **Edge-Cases bei Berechnungen:** Integer-Division, Off-by-One, Null-Pointer in Optional-Feldern (`fix(mensa): null-Nährwerte ... brechen STW-Parser nicht mehr`). Tests erforderlich.
- **Visuelle Polish-Iteration:** AI kann nicht sehen, wie ein Composable wirklich aussieht. App-Icon brauchte 8 Commits, Bewerten-Page-Restructuring brauchte einen separaten Commit nach Sheet-Stack-Fail.
- **Multi-OEM-Verhalten:** `CredentialsManager`-Self-Healing musste auf realen Geräten getestet werden, nicht nur via Unit-Test.
- **API-Schema-Drift über Zeit:** `tags.special` Migration durch STW — AI weiß nicht, dass das passiert ist; nur Re-Calls finden das.

### Was wir geändert haben am Workflow

1. **Live-API-Inspektion vor Code:** Bei jedem Scraper / API erst `curl` oder Browser-Inspect, JSON-Sample committen, dann erst Code generieren lassen.
2. **Tests früher schreiben:** Repository-Tests sind nun Standard, nicht Optional. `LearnwebICalParserTest`, `MensaDtosTest`, `CalendarRepositoryImplTest` als Referenz.
3. **Code-Reviews automatisiert:** `/code-review`-Skill mit verschiedenen Effort-Levels läuft pro Branch vor Merge.
4. **Konzepte separat:** Bevor AI Code für ein neues Feature generiert, schreiben wir das Konzept manuell (siehe `docs(reviews): Design für P2P-Mensa-Reviews`).
5. **Manuelle QA-Devices:** Pixel 7a (Kjell), Galaxy Tab S6 (Johann) als feste Testgeräte.

### Was wir gelernt haben — Meta

- AI ist ein Werkzeug, das schnell falschen Code produziert, der plausibel aussieht. Disziplinierte Reviews sind notwendig.
- "AI macht alles" ist eine Selbstirreführung. Architektur, Scope, Library-Wahl, Workarounds, manuelle QA — das ist menschlich.
- Pair-Programming mit AI bedeutet: AI ist der dritte Stuhl am Tisch, nicht der Driver.
- Die Versuchung, AI-Code unreflektiert zu mergen, ist real. Wir haben dagegen den festen Schritt "Read + Review vor Commit".

## Vergleich v1 ↔ v2

HiUni v1 (Vorjahres-Abgabe, gleiches Team) wurde komplett ohne AI-Assistenten geschrieben. v2 nutzt Claude Code durchgehend. Eine ehrliche Gegenüberstellung — was AI verändert hat im Ergebnis und im Prozess:

| Aspekt | v1 (ohne AI) | v2 (mit AI) |
|---|---|---|
| Zeit bis Foundation lauffähig | mehrere Wochen | ein Tag (23.05.2026) |
| Feature-Anzahl im Final | ~5 Hauptfeatures | ~14 Features inkl. Bonus (Reviews, Federation, Sport, Tablet) |
| Architektur | monolithisch, organisch gewachsen | ADR-getragen, bewusst geplant vor Code |
| Tests | praktisch keine | Repository-Test-Pattern etabliert (mehrere shipped) |
| Bugs durch Copy-Paste | mehrere | weniger, weil AI keine zwei Klassen "fast gleich" produziert — sie produziert sie genau gleich, was wir refactoren |
| Bugs durch Unverständnis | weniger (jede Zeile von uns) | mehr Risiko, weil wir Code akzeptieren der "plausibel aussieht" — Review-Disziplin als Gegengewicht |
| Doku-Qualität | Minimum | ausführlich, ADRs + DEVELOPMENT.md + diese Process-Doc — auch hier AI als Draft-Generator |

**Was wir nicht gemacht hätten ohne AI:**

- **Reviews + Federation (P2P, Ed25519, Ktor-Relay):** Konzept stand, aber die saubere Tink-Anbindung und Canonical-Form-Implementation hätten ohne AI 2–3× so lange gedauert. Ohne AI wäre das wahrscheinlich nicht im Scope geblieben.
- **Tablet-Optimierung mit 3 Layouts:** Same — der `AdaptiveScaffold`-Code mit `WindowSizeClass`-Switch ist AI-Boilerplate.
- **Migration-Tests + Schema-Export:** Wir hätten die Disziplin wahrscheinlich nicht ohne AI-Skelette gefunden.

**Was AI nicht ersetzt hat:**

- Manuelle QA auf realen Geräten (Tablet-Bugs, App-Icon-Crashes).
- Die Wahl zwischen `BottomSheet-stacking` und `dedicated-Page` für die Bewerten-UI — das ist UX-Geschmack.
- Das Schreiben der ADRs als finale Begründung (AI-Drafts wurden umfangreich umgeschrieben).

## Co-Authored-By und Sichtbarkeit

Ab Mitte Juli 2026 wird jeder Commit, der in einer gemeinsamen Pair-Session entsteht, einen Co-Authored-By-Trailer für Johann tragen. Die früheren Commits sind technisch alle unter Kjells privatem GitHub-Account `khk@duck.com` — dokumentiert in [06-team-and-contributions.md](06-team-and-contributions.md).

Claude bekommt explizit **keinen** Co-Authored-By-Trailer. AI-Anteil wird stattdessen über diese Dokumentation und `AI_USAGE.md` deklariert.
