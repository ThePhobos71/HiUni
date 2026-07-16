package de.transio.hiuni.feature.courses

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Start-/Enddatum eines Semesters, grob abgeleitet aus dem Semester-String
 * (z.B. "Sommer 2026", "Winter 2025/26" — siehe [semesterSortKey]).
 *
 * Wir haben in [de.transio.hiuni.feature.courses.data.CourseEntity] keine
 * echten Start-/End-Daten (die DB wird parallel von einem anderen Agenten
 * geändert) — deshalb rein textuell aus dem Semester-Namen abgeleitet, nach
 * deutscher Konvention:
 *   - Sommersemester (SoSe): 01.04. – 30.09.
 *   - Wintersemester (WiSe): 01.10. – 31.03. des Folgejahres
 * Das ist bewusst nicht vorlesungszeit-genau (Vorlesungsbeginn/-ende weichen
 * je nach Hochschuljahr um ein paar Tage ab) — für einen groben
 * Fortschrittsbalken reicht die Kalender-Näherung.
 */
internal data class SemesterRange(val start: LocalDate, val end: LocalDate)

/**
 * Parst einen Semester-String wie "Sommer 2026" oder "Winter 2025/26" in ein
 * [SemesterRange]. Gibt `null` zurück, wenn der String nicht dem bekannten
 * Format entspricht (z.B. leer oder unbekanntes Format) — dann soll die UI
 * keinen Fortschrittsbalken zeigen.
 */
internal fun parseSemesterRange(semester: String): SemesterRange? {
    val sommer = Regex("Sommer\\s+(\\d{4})").find(semester)
    if (sommer != null) {
        val year = sommer.groupValues[1].toInt()
        return SemesterRange(
            start = LocalDate.of(year, 4, 1),
            end = LocalDate.of(year, 9, 30)
        )
    }
    val winter = Regex("Winter\\s+(\\d{4})").find(semester)
    if (winter != null) {
        val startYear = winter.groupValues[1].toInt()
        return SemesterRange(
            start = LocalDate.of(startYear, 10, 1),
            end = LocalDate.of(startYear + 1, 3, 31)
        )
    }
    return null
}

/**
 * Fortschritt von `today` innerhalb des Semesters, geklemmt auf 0..1.
 * Vor Semesterbeginn = 0, nach Semesterende = 1. Gibt `null` zurück, wenn der
 * Semester-String nicht parsebar ist — die UI zeigt dann keinen Balken.
 */
internal fun semesterProgress(semester: String, today: LocalDate = LocalDate.now()): Float? {
    val range = parseSemesterRange(semester) ?: return null
    val totalDays = daysBetween(range.start, range.end)
    if (totalDays <= 0) return null
    val elapsedDays = daysBetween(range.start, today)
    return (elapsedDays.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
}

private fun daysBetween(start: LocalDate, end: LocalDate): Long =
    ChronoUnit.DAYS.between(start, end)
