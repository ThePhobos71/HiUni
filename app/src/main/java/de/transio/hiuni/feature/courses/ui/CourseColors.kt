package de.transio.hiuni.feature.courses.ui

import androidx.compose.ui.graphics.Color

data class CourseAccent(val base: Color, val surface: Color)

private val palette = listOf(
    Color(0xFF7C5BFF),
    Color(0xFFFF6B81),
    Color(0xFF13C4A3),
    Color(0xFFFFA94D),
    Color(0xFF4DA8FF),
    Color(0xFFE15CFF),
    Color(0xFF66B82F),
    Color(0xFFFFB627)
)

fun courseAccent(id: String): CourseAccent {
    val base = palette[(id.hashCode() and 0x7FFFFFFF) % palette.size]
    return CourseAccent(base = base, surface = base.copy(alpha = 0.16f))
}
