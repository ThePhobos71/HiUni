package de.transio.hiuni.feature.calendar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.courses.data.CourseEntity

/**
 * Farbtripel pro Kursblock, angelehnt an die `CC[c.id]` Struktur aus dem Design-Mock:
 * [bg] = weiche Hintergrundfläche, [fg] = lesbarer Text auf [bg], [dot] = kräftiger Akzent
 * für Streifen, Punkte und Schatten.
 */
data class CourseColor(val bg: Color, val fg: Color, val dot: Color)

/**
 * Liefert ein stabiles Farbtripel für ein Event. Schlüssel ist die LSF-Series-Uid
 * (alles vor `#` in sourceReference) — so kriegen alle Instanzen einer Vorlesung
 * dieselbe Farbe. Für USER/PIN-Events fallen wir auf den Titel zurück.
 */
@Composable
@ReadOnlyComposable
fun rememberCourseColor(event: CustomEventEntity): CourseColor {
    val palette = coursePalette()
    val key = event.sourceReference?.substringBefore('#')?.takeIf { it.isNotBlank() }
        ?: event.title
    val index = (key.hashCode().rem(palette.size).let { if (it < 0) it + palette.size else it })
    return palette[index]
}

/**
 * Liefert ein stabiles Farbtripel für einen Kurs. Wir bevorzugen `lsfId` (stabil über
 * Reimporte hinweg), fallen auf `id` (UUID für USER-Kurse) zurück und nutzen schließlich
 * den Namen — so kriegt ein Kurs dieselbe Farbe wie seine LSF-Kalender-Events
 * (deren Series-Uid identisch zur lsfId ist).
 */
@Composable
@ReadOnlyComposable
fun courseColorFor(course: CourseEntity): CourseColor {
    val palette = coursePalette()
    val key = course.lsfId?.takeIf { it.isNotBlank() }
        ?: course.id.takeIf { it.isNotBlank() }
        ?: course.name
    val index = (key.hashCode().rem(palette.size).let { if (it < 0) it + palette.size else it })
    return palette[index]
}

@Composable
@ReadOnlyComposable
private fun coursePalette(): List<CourseColor> {
    val colors = MaterialTheme.colorScheme
    val s = HiUniColors.semantics
    return listOf(
        // Indigo (primary)
        CourseColor(bg = colors.primaryContainer, fg = colors.onPrimaryContainer, dot = colors.primary),
        // Green
        CourseColor(bg = s.greenSurface, fg = s.green, dot = s.green),
        // Amber
        CourseColor(bg = s.amberSurface, fg = s.amber, dot = s.amber),
        // Purple
        CourseColor(bg = s.purpleSurface, fg = s.purple, dot = s.purple),
        // Red
        CourseColor(bg = s.redSurface, fg = s.red, dot = s.red),
    )
}
