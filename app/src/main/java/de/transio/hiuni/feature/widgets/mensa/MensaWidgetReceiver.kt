package de.transio.hiuni.feature.widgets.mensa

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Manifest-registrierter Receiver für das Mensa-Widget. Instanziert pro
 * Update das [MensaWidget], das dann via Glance-Session gegen den
 * MensaRepository-Flow rendert.
 */
class MensaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MensaWidget()
}
