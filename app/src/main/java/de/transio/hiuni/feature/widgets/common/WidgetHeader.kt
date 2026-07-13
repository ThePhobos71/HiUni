package de.transio.hiuni.feature.widgets.common

import androidx.compose.runtime.Composable
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Vereinheitlichter Header für Widgets. Layout:
 *
 *   [icon] Titel                          Kontext-Text  [action?]
 *
 * - [iconRes]: kleines 18dp-Icon links (z.B. `R.drawable.ic_widget_todo`).
 * - [title]: primärer Widget-Titel, bold.
 * - [context]: optionaler kleiner grauer Kontext-Text rechts (z.B. Datum
 *   oder Zähler "(3)"). Nimmt keinen weight, damit der Titel den Rest
 *   der Zeile bekommt.
 * - [actionIconRes] / [onAction]: optionales rechts positioniertes
 *   Action-Icon (z.B. Plus für "Neues Todo"). Wenn beides gesetzt ist,
 *   wird der Icon-Button als eigener Tap-Zonen-Bereich gerendert.
 */
@Composable
fun WidgetHeader(
    iconRes: Int,
    title: String,
    context: String? = null,
    actionIconRes: Int? = null,
    onAction: Action? = null,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(WidgetTheme.OnSurface),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = title,
            maxLines = 1,
            style = TextStyle(
                color = WidgetTheme.OnSurface,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (context != null) {
            Text(
                text = context,
                maxLines = 1,
                style = TextStyle(color = WidgetTheme.OnSurfaceMuted),
            )
        }
        if (actionIconRes != null && onAction != null) {
            Spacer(GlanceModifier.width(8.dp))
            Image(
                provider = ImageProvider(actionIconRes),
                contentDescription = null,
                modifier = GlanceModifier
                    .size(22.dp)
                    .padding(2.dp)
                    .clickable(onAction),
                colorFilter = ColorFilter.tint(WidgetTheme.Primary),
            )
        }
    }
}
