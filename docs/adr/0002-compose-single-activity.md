# ADR-0002: Compose + Single-Activity-Architektur

**Datum:** 2026-05-23
**Status:** Accepted

## Kontext

Android bietet historisch zwei UI-Stacks: das klassische View-System mit Fragments und Activities, oder Jetpack Compose mit Single-Activity + Composables.

## Entscheidung

Wir nutzen **Jetpack Compose mit Single-Activity-Architektur**. Es gibt genau eine `MainActivity`, alle Screens sind `@Composable`-Funktionen, Navigation läuft über `androidx.navigation:navigation-compose`.

## Begründung

- Compose ist der von Google empfohlene Stack seit 2021 und stable.
- Single-Activity entkoppelt Lifecycle-Pain (Fragment-Transactions, Backstack-Bugs).
- State-Management mit `StateFlow` + `collectAsStateWithLifecycle` ist deutlich kompakter als `LiveData` + View-Bindings.
- Hilt's `@HiltViewModel` + `hiltViewModel()` in Compose ist nahtlos.
- Material 3 + Dynamic Color sind nur via Compose ergonomisch erreichbar.

## Trade-offs

- Es gibt Lücken: z.B. ist `androidx.compose.material3.windowsizeclass` noch `@ExperimentalMaterial3WindowSizeClassApi`. Wir markieren das per Opt-in im Build.
- Compose-Compiler braucht Java 17 — Build-Toolchain entsprechend.
- Performance-Profiling von Compose ist anders als View-System (Layout-Inspector, Recomposition-Counts).
