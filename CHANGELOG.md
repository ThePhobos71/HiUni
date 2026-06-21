# Changelog

Format orientiert an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/).

## [Unreleased]

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
