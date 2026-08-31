# Widget-Todo — Implementation Report

## Files Created

- `app/src/main/java/de/transio/hiuni/feature/widgets/todos/TodoWidgetReceiver.kt`
  Manifest-registrierter `GlanceAppWidgetReceiver` — delegiert an `TodoWidget`.
- `app/src/main/java/de/transio/hiuni/feature/widgets/todos/TodoWidget.kt`
  Glance-`AppWidget` mit `SizeMode.Responsive` (small/medium/large). Header
  ("Aufgaben" + "+"), Empty-State, Count-Only (small), LazyColumn mit
  Checkbox + Titel + Due-Chip (medium/large). Widget-eigene DayNight-
  `ColorProvider`-Palette gespiegelt aus `core.design.Color.kt` (Glance kann
  Compose-`ColorScheme` nicht direkt teilen).
- `app/src/main/java/de/transio/hiuni/feature/widgets/todos/TodoWidgetActions.kt`
  `ToggleDoneAction` (liest aktuellen Zustand aus dem Flow und toggelt via
  `TodosRepository.setDone`), `NewTodoAction` (startet MainActivity mit Deep-
  Link). Konstanten für Action-Name + Extra-Keys.

## Manifest

`AndroidManifest.xml` — `<receiver>` für `TodoWidgetReceiver` mit
`exported=true`, `APPWIDGET_UPDATE`-Intent-Filter und `todo_widget_info`-
Meta-Data hinzugefügt.

## Build

`./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL** in 13s.

## Deep-Link-Signature (Wrap-up-Task muss darauf reagieren)

`MainActivity.onNewIntent`/`onCreate` sollte prüfen:

- `intent.action == "de.transio.hiuni.OPEN_TODOS"`  (Konstante `ACTION_OPEN_TODOS`)
- `intent.getStringExtra("mode")` — bei `"new"` → Neu-Aufgabe-Sheet öffnen
  (Konstante `EXTRA_MODE`)
- `intent.getLongExtra("todoId", -1L)` — bei `> 0` → Aufgaben-Detail bzw.
  Aufgabenliste mit vorausgewählter Zeile (Konstante `EXTRA_TODO_ID`)
- Fallback ohne Extras: nur Aufgaben-Liste öffnen

Konstanten sind in `TodoWidgetActions.kt` als `internal const val`
definiert — für Import in MainActivity ggf. auf `const val` (public)
promoten oder in ein eigenes `WidgetIntents.kt` verschieben.

## Concerns

1. **`ToggleDoneAction` liest via `observeOpen(limit = Int.MAX_VALUE).first()`**
   — funktioniert, aber ineffizient. Sauberer wäre ein DAO-Getter
   `suspend fun getById(id: Long): TodoEntity?`. Reicht für V1; wenn die
   Todo-Liste sehr groß wird, gerne umbauen.
2. **`observeOpen` liefert nur nicht-erledigte Todos** — bei Toggle wird
   `newState = !current.isDone`. Da `current.isDone` durch die `observeOpen`-
   Filterung immer `false` ist, wird der Toggle-Zielzustand immer `true`
   ("erledigt"). Das ist der gewünschte User-Flow: der Widget zeigt nur
   offene Aufgaben, ein Tap hakt sie ab.
3. **Kein Widget-Preview** — `previewLayout` in `todo_widget_info.xml` zeigt
   auf `glance_default_loading_layout`. Für einen echten Preview mit
   Dummy-Inhalt bräuchte Glance einen `provideGlance`-Preview-Pfad oder ein
   statisches Preview-XML.
4. **Kein "+"-Icon** — nur ein Unicode-Plus im Bold-Text-Style. Für ein echtes
   Material-Icon müsste ein Vector-Drawable ins `res/drawable/` und via
   `ImageProvider(R.drawable.ic_add)` in ein `Image`-Composable gehen.
   Bewusst weggelassen für V1.
5. **`SizeMode.Responsive` liefert auf Android < 12 nur die kleinste Größe**
   — die drei Layouts sind erst ab API 31 sichtbar. Für ältere Android-
   Versionen wäre `SizeMode.Exact` nötig; das kann in einem Follow-up
   nachgezogen werden falls minSdk das erfordert.
