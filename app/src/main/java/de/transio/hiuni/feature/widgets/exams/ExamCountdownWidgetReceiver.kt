package de.transio.hiuni.feature.widgets.exams

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Manifest-registrierter Receiver für das Klausur-Countdown-Widget. Delegiert
 * alles an [ExamCountdownWidget]; die Klasse existiert nur, weil AppWidget-
 * Provider auf Manifest-Ebene eine konkrete BroadcastReceiver-Subklasse
 * erwartet und Glance selbst kein Receiver-Alias bietet.
 */
class ExamCountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExamCountdownWidget()
}
