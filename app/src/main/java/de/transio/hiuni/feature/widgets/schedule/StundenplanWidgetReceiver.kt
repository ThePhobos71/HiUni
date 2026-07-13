package de.transio.hiuni.feature.widgets.schedule

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Manifest-registrierter Receiver für das Stundenplan-Widget. Instanziert
 * pro Update das [StundenplanWidget], das dann via Glance-Session gegen den
 * Kalender-Repository-Flow rendert.
 */
class StundenplanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StundenplanWidget()
}
