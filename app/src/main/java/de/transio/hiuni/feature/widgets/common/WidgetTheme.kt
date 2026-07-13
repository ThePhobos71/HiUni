package de.transio.hiuni.feature.widgets.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.unit.ColorProvider

/**
 * Zentrale Farben, Spacing-Werte und Farbpalette für alle Home-Screen-
 * Widgets. Wir können hier kein `MaterialTheme` benutzen — Glance rendert
 * in einem fremden Prozess ohne Compose-Runtime — also sind die Werte
 * hart-kodiert und liefern via [DayNightColorProvider] jeweils einen
 * Light- und einen Dark-Wert für den Launcher.
 *
 * Ziel ist visuelle Familienähnlichkeit über die 5 Widgets hinweg: gleiche
 * Card-Farbe, gleicher Text-Kontrast, gleiche Akzentfarben aus derselben
 * Palette wie die App ([de.transio.hiuni.feature.calendar.ui.CourseColor]).
 */
object WidgetTheme {

    // Surface
    val Surface: ColorProvider = DayNightColorProvider(
        day = Color(0xFFFFFFFF),
        night = Color(0xFF1B1B1F),
    )

    // Foreground / Text
    val OnSurface: ColorProvider = DayNightColorProvider(
        day = Color(0xFF1B1B1F),
        night = Color(0xFFECECEE),
    )
    val OnSurfaceMuted: ColorProvider = DayNightColorProvider(
        day = Color(0xFF5A5A63),
        night = Color(0xFFAFAFB6),
    )
    val OnSurfaceFaint: ColorProvider = DayNightColorProvider(
        day = Color(0xFF9A9AA3),
        night = Color(0xFF6B6B75),
    )

    // Accents
    val Primary: ColorProvider = DayNightColorProvider(
        day = Color(0xFF4B57C6),
        night = Color(0xFFB0B8FF),
    )
    val PrimaryContainer: ColorProvider = DayNightColorProvider(
        day = Color(0xFFE0E4FF),
        night = Color(0xFF2C3675),
    )

    // Semantic (mirror HiUniColors.semantics for consistency)
    val Green: ColorProvider = DayNightColorProvider(
        day = Color(0xFF1E7A3E),
        night = Color(0xFF7CD9A2),
    )
    val GreenSurface: ColorProvider = DayNightColorProvider(
        day = Color(0xFFDFF5E4),
        night = Color(0xFF224A32),
    )
    val Amber: ColorProvider = DayNightColorProvider(
        day = Color(0xFFB57000),
        night = Color(0xFFF7C25E),
    )
    val AmberSurface: ColorProvider = DayNightColorProvider(
        day = Color(0xFFFFECC7),
        night = Color(0xFF5A3E00),
    )
    val Red: ColorProvider = DayNightColorProvider(
        day = Color(0xFFB3261E),
        night = Color(0xFFF2B8B5),
    )
    val RedSurface: ColorProvider = DayNightColorProvider(
        day = Color(0xFFFADAD8),
        night = Color(0xFF5A1D1B),
    )

    // Layout
    val CardCornerRadius = 16.dp
    val CardPaddingHorizontal = 12.dp
    val CardPaddingVertical = 10.dp
    val RowSpacing = 6.dp
    val HeaderBottomSpacing = 8.dp
}
