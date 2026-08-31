# Widget Mensa — Report

## Files created

- `app/src/main/java/de/transio/hiuni/feature/widgets/mensa/MensaWidget.kt`
- `app/src/main/java/de/transio/hiuni/feature/widgets/mensa/MensaWidgetReceiver.kt`
- `app/src/main/res/xml/mensa_widget_info.xml`

## Files modified

- `app/src/main/java/de/transio/hiuni/feature/widgets/WidgetHiltEntryPoint.kt` — `mensaRepository()` hinzugefügt
- `app/src/main/java/de/transio/hiuni/feature/widgets/WidgetDeepLinkController.kt` — `_openMensa` SharedFlow + `ACTION_OPEN_MENSA` (`de.transio.hiuni.OPEN_MENSA`)
- `app/src/main/java/de/transio/hiuni/navigation/AppNavGraph.kt` — `NfcNavViewModel.openMensa` + `LaunchedEffect` navigiert zu `Destination.Mensa.route`
- `app/src/main/AndroidManifest.xml` — `MensaWidgetReceiver` registriert (analog `StundenplanWidgetReceiver`)
- `app/src/main/res/values/strings.xml` — `widget_mensa_description`

## Build

`./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL in 12s**.

## Design-Entscheidungen

- **Whole-widget-tap** löst den Deep-Link aus (via `.clickable(openApp)` auf der Outer-Column). Row-Level-Clickable weggelassen — für V1 kein Meal-Detail-Deeplink verfügbar; ein separater Row-Tap gäbe dem User falsche Erwartungen (kein Detail-Screen unter dem Ziel).
- **Category-Pill:** `shortCategory()` mapt STW-Strings ("Vegan Gericht 1", "Fleisch 2", "Beilage", "Suppe", "Dessert", "Aktion") auf kurze Labels + optionale trailing Zahl, damit sie in den 72dp-Pill passen. Fallback: raw category (Truncate übernimmt Text-Layout).
- **Farb-Logik:** vegan/vegetarisch → grüner Pill, sonst neutrales Grau — tag-basiert (`meal.tags` comma-split, case-insensitive), nicht category-basiert; damit greift die Grün-Markierung auch bei STW-Categories wie "Aktion vegan".
- **Empty-State:** Sa/So → "Heute keine Speisen — Wochenende?", Werktag → "Kein Menü heute". `dayOfWeek.value >= 6` — im Stundenplan-Widget ist die Weekend-Grenze `>= 5` (Fri/Sat/Sun), aber die Mensa ist Fr offen → hier bewusst enger.
- **Sort:** `meals.sortedBy { it.category }` als Safety über die vermutliche Room-Query-Order.
- **Kein Refresh-Icon im Header** — spec sagt "kleiner Refresh/App-Icon rechts", aber Refresh würde `MensaRepository.refresh(force=true)` als suspend-Fn brauchen; das ist im aktuellen Glance-`clickable` nur via ActionCallback machbar. Whole-widget-tap öffnet ohnehin die App und die triggert dort ein Refresh. Icon rechts weggelassen zugunsten mehr Breite fürs Datum.

## Concerns

- **Kein Row-Level-Deeplink** — bewusst, siehe oben. Sobald ein Meal-Detail-Sheet existiert, kann eine `EXTRA_MEAL_ID` in `WidgetDeepLinkController` nachgerüstet werden.
- **Auto-Refresh:** `updatePeriodMillis=0` wie bei den anderen Widgets — Glance re-collectiert bei jedem Composition-Trigger und der Room-Flow ist reaktiv, aber ohne Push von Repository-Refreshes (`MensaRepository.refresh` läuft via In-App-Nutzung + Throttle) kann der Widget-Inhalt stale sein, bis der User die App öffnet. Kein neues Problem; identisch zum Stundenplan-Widget-Setup.
- **`shortCategory()`-Heuristik** ist lokalisierungs-anfällig (nur DE, string-prefix-basiert). Für V1 okay, da STW-Categories DE liefert.
