# ADR-0004: Single AppDatabase mit Feature-owned Entities + DAOs

**Datum:** 2026-05-23
**Status:** Accepted

## Kontext

Mit Feature-First-Packages (ADR-0001) stellt sich die Frage, wo Room-Entities und DAOs leben:

- **Variante A:** Eine zentrale `core/database/`-Stelle hält alle Entities und DAOs
- **Variante B:** Jedes Feature hat eine eigene Room-Database (separate `.db`-Dateien)
- **Variante C:** Single `AppDatabase` in `core/database/`, aber Entities + DAOs leben pro Feature

## Entscheidung

**Variante C** — Single AppDatabase mit Feature-owned Entities + DAOs.

```
core/database/
└── AppDatabase.kt         # @Database listet Entities aller Features

feature/calendar/data/
├── CustomEventEntity.kt   # Room-Entity
├── CustomEventDao.kt      # DAO
└── CalendarRepository.kt  # Repo-Interface + Impl
```

`AppDatabase` referenziert alle Feature-Entities und exponiert die DAOs als abstrakte Funktionen.

## Begründung

- **Cross-Feature-Queries möglich** wenn nötig (kein Multi-DB-Join-Schmerz)
- **Migrations zentral** in `core/database/migrations/` — einfacher zu reviewen
- **Feature-Ownership der Schemas** bleibt erhalten: Entity-Änderungen passieren im Feature-Package
- **`exportSchema = true`** generiert JSON-Snapshots in `app/schemas/` für Migration-Tests

## Trade-offs

- Neues Feature anlegen erfordert Update an `AppDatabase.kt` (kleine Kollisionspunkt-Stelle)
- Bei wirklich autarken Features (späterer Module-Split) müsste die DB aufgeteilt werden
- Single-DB-Datei statt Multi-DB — bei riesigen Datenmengen ineffizienter, hier irrelevant

## Migration-Strategie

- Version startet bei `1`
- **KEIN `fallbackToDestructiveMigration()`** (v1-Lesson — verlor User-Daten beim Update)
- Echte Migrations werden in `core/database/migrations/` als `Migration(from, to)`-Objects geschrieben
