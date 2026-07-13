# Stundenplan-Widget — Bericht

## Erstellte Dateien
- `app/src/main/java/de/transio/hiuni/feature/widgets/schedule/StundenplanWidgetReceiver.kt`
- `app/src/main/java/de/transio/hiuni/feature/widgets/schedule/StundenplanWidget.kt`

## Geänderte Dateien
- `app/src/main/AndroidManifest.xml` — `<receiver>`-Eintrag für `StundenplanWidgetReceiver`
  (exported=true, APPWIDGET_UPDATE-Intent-Filter, Meta-Data auf
  `@xml/schedule_widget_info`).

## Build
`./gradlew :app:assembleDebug` — BUILD SUCCESSFUL (nur bestehende Deprecation-Warnungen aus anderen Modulen).

## Deep-Link
Ein Tap auf eine Widget-Zeile / den Plus-Button feuert:

```
Intent(context, MainActivity::class.java).apply {
    action = "de.transio.hiuni.OPEN_CALENDAR"        // ACTION_OPEN_CALENDAR
    putExtra("eventId", <event.id oder -1L>)          // EXTRA_EVENT_ID
    flags = Intent.FLAG_ACTIVITY_NEW_TASK
}
```

- `eventId = -1L` → Header/Plus-Button, öffnet Kalender-Root ohne Ziel-Event.
- `eventId = <Long>` → EventRow-Tap, springt idealerweise auf das Event.

Konstanten sind `internal const val` (`ACTION_OPEN_CALENDAR`, `EXTRA_EVENT_ID`) in
`StundenplanWidget.kt`. Wrap-up-Task muss diese Action in `MainActivity.onNewIntent`
verdrahten (analog zum bestehenden `NotificationPresenter.EXTRA_NAV_TARGET`-Handling).

## Widget-Verhalten
- **Größen (SizeMode.Responsive):** 250×110 (klein), 250×180 (mittel), 250×250 (groß).
  Größenanpassung passiert real via `LazyColumn`, die entsprechend mehr Zeilen zeigt.
- **Query:** `CalendarRepository.observeRange(fromInstant, toInstant)` für Heute [00:00, 24:00).
  Zusätzlich ein 7-Tage-Fenster für den Empty-State-Hint.
- **Filter:** `endTime.isAfter(now)` — laufende Events bleiben sichtbar, vergangene fallen raus.
- **Sortierung:** `sortedBy { startTime }`.
- **Empty-State:**
  - Mo–Do: „Nichts anstehend heute — genieße den freien Tag“.
  - Fr–So: „Nächster Uni-Tag: <Wochentag>“ (aus 7-Tage-Fenster) oder Standard-Text als Fallback.
- **Farb-Bar pro Event:** deterministisch aus `sourceReference` (LSF-Series-Uid vor '#'),
  Fallback `courseLsfId` → Titel. 5-Farb-Palette inline (Indigo/Green/Amber/Purple/Red),
  jede Farbe hat Day- und Night-Wert (`androidx.glance.color.ColorProvider(day, night)`).

## WORKTREE PATH
`/Users/kjell/AndroidStudioProjects/UniHi/.claude/worktrees/agent-ad9d16f1005fcdcec`

Branch: `worktree-agent-ad9d16f1005fcdcec`

## Concerns
- **`ColorProvider`-Import-Pfad:** Die Task-Beschreibung nannte
  `androidx.glance.color.ColorProvider` als Rückgabetyp — dort liegt aber nur die
  Day/Night-**Factory** (`ColorProvider(day, night)`), der eigentliche Typ ist
  `androidx.glance.unit.ColorProvider`. Ich importiere die Factory als
  `DayNightColorProvider` und nutze den Typ aus `unit`. Compiliert und funktioniert;
  falls Reviewer den Alias unschön findet, kann er direkt auf
  `androidx.glance.color.ColorProviderKt.ColorProvider` verweisen.
- **Deep-Link nur konstruiert, nicht empfangen:** MainActivity handhabt heute nur
  `NotificationPresenter.EXTRA_NAV_TARGET`; ein `OPEN_CALENDAR`-Handler fehlt noch.
  Der Wrap-up-Task muss das Extra lesen und über `NavController` auf
  `Destinations.Calendar` navigieren, plus optional das `eventId` durchreichen
  (dafür bräuchte `CalendarViewModel` einen `pendingEventId`-State).
- **Kein WorkManager-Refresh:** `updatePeriodMillis="0"` heißt: der Widget wird
  nur aktualisiert, wenn (a) das System Rebindings triggert (Boot/Locale/…) oder
  (b) jemand `updateAll<StundenplanWidget>(context)` aufruft. Aktuell wird das
  nirgends aus dem App-Code getan; sichtbare Änderungen (neue Vorlesungen im
  Kalender-Room) landen erst beim nächsten OS-Rebind oder Add. Wrap-up-Task
  sollte einen Worker/Observer daran ketten (z. B. am Ende von `LsfSyncWorker`
  oder in einem `CustomEventDao`-Observer).
- **LocaleFormat:** Wochentags-Kurzform via `Locale.GERMAN` fest verdrahtet.
  Falls die App später Multi-Locale wird, hier `context.resources.configuration.locales[0]`
  nachziehen.
