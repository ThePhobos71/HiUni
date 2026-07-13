package de.transio.hiuni.feature.widgets.common

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.unit.ColorProvider

/**
 * Deterministisches Farbtripel für einen Kurs/Event im Widget. Spiegelt die
 * App-Palette aus `feature/calendar/ui/CourseColor.kt` — dieselbe Vorlesung
 * kriegt in der App und im Widget denselben Akzent (Hashing über den
 * Kurs-Key, Palette-Index reproduzierbar).
 */
data class WidgetCourseColor(
    val bg: ColorProvider,
    val fg: ColorProvider,
    val dot: ColorProvider,
)

object WidgetPalette {

    /**
     * Palette-Reihenfolge identisch zu `coursePalette()` in CourseColor.kt —
     * Indigo, Green, Amber, Purple, Red.
     */
    private val palette: List<WidgetCourseColor> = listOf(
        WidgetCourseColor(
            bg = DayNightColorProvider(day = Color(0xFFE0E4FF), night = Color(0xFF2C3675)),
            fg = DayNightColorProvider(day = Color(0xFF1F2A6E), night = Color(0xFFDDE3FF)),
            dot = DayNightColorProvider(day = Color(0xFF4B57C6), night = Color(0xFFB0B8FF)),
        ),
        WidgetCourseColor(
            bg = DayNightColorProvider(day = Color(0xFFDFF5E4), night = Color(0xFF224A32)),
            fg = DayNightColorProvider(day = Color(0xFF1E7A3E), night = Color(0xFF7CD9A2)),
            dot = DayNightColorProvider(day = Color(0xFF1E7A3E), night = Color(0xFF7CD9A2)),
        ),
        WidgetCourseColor(
            bg = DayNightColorProvider(day = Color(0xFFFFECC7), night = Color(0xFF5A3E00)),
            fg = DayNightColorProvider(day = Color(0xFFB57000), night = Color(0xFFF7C25E)),
            dot = DayNightColorProvider(day = Color(0xFFB57000), night = Color(0xFFF7C25E)),
        ),
        WidgetCourseColor(
            bg = DayNightColorProvider(day = Color(0xFFEDE0FF), night = Color(0xFF3D2A6B)),
            fg = DayNightColorProvider(day = Color(0xFF6942C6), night = Color(0xFFC4A9FF)),
            dot = DayNightColorProvider(day = Color(0xFF6942C6), night = Color(0xFFC4A9FF)),
        ),
        WidgetCourseColor(
            bg = DayNightColorProvider(day = Color(0xFFFADAD8), night = Color(0xFF5A1D1B)),
            fg = DayNightColorProvider(day = Color(0xFFB3261E), night = Color(0xFFF2B8B5)),
            dot = DayNightColorProvider(day = Color(0xFFB3261E), night = Color(0xFFF2B8B5)),
        ),
    )

    /**
     * Stabile Zuweisung: gleicher `key` → gleiche Farbe. Für Kalender-Events
     * ist das die LSF-Series-Uid (in der App `sourceReference.substringBefore('#')`),
     * für Custom-Events der Titel — mimikry des App-Behaviors.
     */
    fun colorFor(key: String): WidgetCourseColor {
        val idx = key.hashCode().rem(palette.size).let { if (it < 0) it + palette.size else it }
        return palette[idx]
    }
}
