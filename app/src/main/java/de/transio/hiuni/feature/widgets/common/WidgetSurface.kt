package de.transio.hiuni.feature.widgets.common

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding

/**
 * Einheitlicher Card-Rahmen für alle Widgets: gleiche Hintergrund-Farbe,
 * Ecken-Rundung und Padding. Ganze Fläche ist optional klickbar für den
 * App-Deep-Link.
 */
@Composable
fun WidgetSurface(
    onClick: Action? = null,
    content: @Composable () -> Unit,
) {
    val base = GlanceModifier
        .fillMaxSize()
        .cornerRadius(WidgetTheme.CardCornerRadius)
        .background(WidgetTheme.Surface)
        .padding(
            horizontal = WidgetTheme.CardPaddingHorizontal,
            vertical = WidgetTheme.CardPaddingVertical,
        )
    val modifier = if (onClick != null) base.clickable(onClick) else base
    Column(modifier = modifier) { content() }
}
