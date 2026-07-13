package de.transio.hiuni.feature.widgets.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/**
 * Einheitlicher Empty-State: großes muted Icon oben, kurze Nachricht
 * darunter. Fill-Fläche und zentriert. Analog zur App-Empty-State-
 * Konvention.
 */
@Composable
fun WidgetEmpty(iconRes: Int, message: String) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(28.dp),
            colorFilter = ColorFilter.tint(WidgetTheme.OnSurfaceFaint),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = message,
            style = TextStyle(color = WidgetTheme.OnSurfaceMuted),
        )
    }
}
