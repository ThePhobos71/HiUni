package de.transio.hiuni.feature.widgets.scheduleweek

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Manifest-registrierter Receiver für das Wochen-Stundenplan-Widget.
 * Instanziert pro Update das [SchedulaWeekWidget], das dann via Glance-
 * Session gegen den Kalender-Repository-Flow rendert und die kommenden
 * 7 Tage als Agenda-Liste zeigt.
 */
class SchedulaWeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SchedulaWeekWidget()
}
