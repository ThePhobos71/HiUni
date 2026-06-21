# ADR-0001: Feature-First Packages innerhalb von `:app`

**Datum:** 2026-05-23
**Status:** Accepted

## Kontext

Wir hatten zwei Architektur-Stile zur Auswahl:

- **Layer-First Multi-Module:** `app + core/* + domain + data/* + feature/*` als getrennte Gradle-Module
- **Feature-First Single-Module:** Alle Features als Packages innerhalb von `:app`

## Entscheidung

Wir nutzen **Feature-First Packages** innerhalb eines einzigen `:app`-Moduls:

```
de.transio.hiuni/
├── core/              # geteilte Infrastruktur (Theme, Database, Network, ...)
├── di/                # App-wide Hilt Module
├── navigation/        # NavGraph
├── ui/responsive/     # WindowSizeClass-Switching
└── feature/<name>/    # alle Feature-Inhalte (ui, data, ViewModel)
```

Jedes Feature bringt seine eigene UI, ViewModel, Repository, Scraper/API, Models, Room-Entity + DAO mit.

## Begründung

- **Build-Speed:** Single-Module-Builds laufen 3-5× schneller als Multi-Module-Setups in dieser Größe. Bei einer Studi-App mit ~3000 LOC ist die Module-Isolation den Build-Overhead nicht wert.
- **Klare Feature-Owner-Modell:** Jedes Feature hat einen einzigen Ort. Kein Hin-und-Her-Springen zwischen `domain/`, `data/`, `ui/`.
- **Cross-Feature-Kontrolle:** Die Trennung wird per Convention durchgesetzt (ADR-0006). Sollte sich später echte Module-Isolation lohnen, sind die Packages 1:1 in Module umwandelbar.

## Trade-offs

- Keine echte Compile-Time-Isolation zwischen Features
- Architektur-Punkte beim Prof kommen aus „klare Feature-Trennung", nicht aus „Multi-Module"
- Späterer Split in Gradle-Module ist möglich, weil Cross-Feature-Imports nur über Repo-Interfaces laufen

## Cross-Feature-Ausnahmen

- `feature.home.*` darf Feature-Repos aller anderen Features read-only injecten
- `feature.email.*` darf `core.security.CredentialsManager` nutzen
- Alle anderen Cross-Feature-Imports sind verboten
