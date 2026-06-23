package de.transio.hiuni.feature.todos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import de.transio.hiuni.core.design.HiUniColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Relative Beschriftung + semantische Akzentfarbe für eine Fälligkeit.
 *
 * Regeln:
 * - Überfällig (Datum < heute & nicht erledigt) → rot, "Überfällig" oder "Vor X Tagen"
 * - Heute → rot ("Heute")
 * - Morgen → amber ("Morgen")
 * - 2–7 Tage → amber ("In N Tagen")
 * - 8–30 Tage → muted (formatiertes Datum, z. B. "Di, 15. Jul")
 * - > 30 Tage → muted (Datum mit Jahr)
 *
 * Erledigte Aufgaben bekommen immer den muted-Ton, unabhängig vom Datum.
 */
data class TodoDueChip(
    val label: String,
    val accent: Color
)

private val shortFmt = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)
private val longFmt = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)

@Composable
@ReadOnlyComposable
fun rememberDueChip(due: LocalDate?, isDone: Boolean, today: LocalDate = LocalDate.now()): TodoDueChip? {
    if (due == null) return null
    val semantics = HiUniColors.semantics
    if (isDone) {
        return TodoDueChip(
            label = formatDateLabel(due, today),
            accent = semantics.onSurfaceMuted
        )
    }
    val days = ChronoUnit.DAYS.between(today, due)
    return when {
        days < 0L -> {
            val abs = -days
            val label = if (abs == 1L) "Gestern überfällig" else "Vor $abs Tagen fällig"
            TodoDueChip(label = label, accent = semantics.red)
        }
        days == 0L -> TodoDueChip("Heute", semantics.red)
        days == 1L -> TodoDueChip("Morgen", semantics.amber)
        days in 2L..7L -> TodoDueChip("In $days Tagen", semantics.amber)
        else -> TodoDueChip(formatDateLabel(due, today), semantics.onSurfaceMuted)
    }
}

private fun formatDateLabel(due: LocalDate, today: LocalDate): String =
    if (due.year == today.year) due.format(shortFmt) else due.format(longFmt)
