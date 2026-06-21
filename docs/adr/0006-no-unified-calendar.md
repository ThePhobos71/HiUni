# ADR-0006: Kein Unified Calendar

**Datum:** 2026-05-23
**Status:** Accepted

## Kontext

In HiUni v1 wurden Mensa-API-Events, Movie-Scraper-Events und User-Custom-Events in **einer einzigen** `calendar_events`-Tabelle gespeichert (Diskriminator `sourceOrigin`). Das v1-Pattern hieß „Unified Calendar".

Der Vorteil: Eine Query bringt alle Events. Der Preis: jedes Feature schreibt in eine gemeinsame Tabelle und kennt das gemeinsame Schema. Die Modulare Trennung (ADR-0001) wird damit gebrochen.

## Entscheidung

**Wir verwerfen das Unified-Calendar-Pattern.** Stattdessen:

- **`feature.calendar`** ist ein autonomes Feature mit eigener `custom_events`-Tabelle. Es enthält nur **User-erstellte** Events.
- **Mensa** zeigt Mensa-Daten in `feature.mensa` (eigene Tabelle / API-Call).
- **Movies** zeigt Filme in `feature.movies` (eigene Tabelle / Scraper).
- **Bib** zeigt Verfügbarkeit in `feature.bib` (eigene In-Memory- oder lokale Cache-Lösung).

### „In Kalender packen" für Mensa/Movies

Wenn der User in der Mensa- oder Movie-Detail-Ansicht auf „In Kalender packen" klickt, wird ein **Snapshot** als neuer `CustomEventEntity` mit `sourceKind = "MENSA_PIN"` bzw. `"MOVIE_PIN"` in der Calendar-Tabelle angelegt. Die Originalquelle bleibt unangetastet.

```kotlin
data class CustomEventEntity(
    val title: String,
    val startTime: Instant,
    val endTime: Instant,
    val location: String?,
    val sourceKind: String,        // USER | MENSA_PIN | MOVIE_PIN
    val sourceReference: String?,  // z.B. Mensa-Meal-ID, Movie-Film-ID
    ...
)
```

## Begründung

- **Klare Feature-Trennung:** Jedes Feature ist autark, keine implizite Cross-Feature-Coupling
- **Calendar wird testbar ohne Mensa-/Movie-Daten:** Repository hat keine externen Repo-Dependencies
- **Snapshot statt Live-Verknüpfung:** Wenn unifilm.de eine Filmzeit ändert, bleibt der vom User gepinte Calendar-Event unverändert. Bewusst gewähltes Verhalten (Pin = "ich habe das so für mich notiert").

## Trade-offs

- Datendupli kation bei Pins (ein Mensa-Gericht das gepint wird existiert in `meals` und in `custom_events`)
- Pin-Update bei Quelländerung (z.B. Film-Verlegung) muss explizit angeboten werden
- Home-Screen muss zwei Quellen abfragen ("nächster User-Event aus Calendar" + "heute Mensa aus Mensa")

## Was Home macht (Cross-Feature-Ausnahme)

`feature.home` darf alle Feature-Repos read-only injecten:

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val calendarRepo: CalendarRepository,
    private val mensaRepo: MensaRepository,
    private val emailRepo: EmailRepository
) : ViewModel()
```

Das ist die einzige Cross-Feature-Ausnahme neben `feature.email → core.security.CredentialsManager`.
