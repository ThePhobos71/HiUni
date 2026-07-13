package de.transio.hiuni.feature.widgets.todos

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Manifest-registrierter Receiver für das Todo-Widget. Delegiert alles an
 * `TodoWidget`; die Klasse existiert nur, weil AppWidgetProvider auf Manifest-
 * Ebene eine konkrete BroadcastReceiver-Subklasse erwartet und Glance selbst
 * kein Receiver-Alias bietet.
 */
class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget()
}
