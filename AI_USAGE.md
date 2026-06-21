# AI Usage Disclosure

> Lebende Datei. Wird pro relevantem Commit aktualisiert.

## Tools

- **Claude Code (Anthropic)** — primärer AI-Assistent für Code-Generierung, Architektur-Review, Doku-Drafts. Modell: Opus 4.x (1M context).

## Workflow-Prinzip

Wir „vibecoden mit Plan": Architektur-Entscheidungen treffen wir manuell (ADRs in `docs/adr/`), AI generiert daraus Code-Skelette. Jeder generierte Code wird gelesen, getestet und vom Team gegengezeichnet bevor er gemergt wird. Bei Pair-Defense kann jeder Teamteil jedes Modul erklären.

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

- Die 17 Mock-Screens (Klausuren, Sport, Lerngruppen, Noten, Push-Center, Campus-Plan, Mensa-Card-Reader, etc.). Unsere App hat 8 Destinations (Home, Calendar, Mensa, Movies, Bib, Email, Settings, About) gemäß HIUNI_REBUILD_PLAN.md.
- In-App Tweaks-Panel (Akzent-Hue, Begrüßung, Dark Mode). Settings in unserer App laufen über `feature/settings`.
- Mock-Daten im Home (Lineare Algebra, VWL, Uni Kino Filme). Phase 2 ersetzt sie mit echten Repos.

**Build-Status:** `./gradlew assembleDebug lintDebug` grün nach der UI-Übernahme.
