package de.transio.hiuni.feature.courses

import de.transio.hiuni.core.common.Semester
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.grades.data.GradeEntity
import de.transio.hiuni.feature.grades.data.GradeStatus
import java.util.Locale

/**
 * Herkunft der pro Kurs angezeigten „effektiven" Note.
 */
enum class GradeSource {
    /** Manuell im Kurs-Edit-Sheet gesetzte [CourseEntity.grade] — hat immer Vorrang. */
    MANUAL,

    /** Aus dem LSF/QIS-Notenspiegel gematchte [GradeEntity] (read-only, abgeleitet). */
    NOTENSPIEGEL,

    /** Keine Note vorhanden → „Note steht noch aus". */
    NONE
}

/**
 * Read-only abgeleitete Note eines Kurses. [label] ist der anzeigefertige Text
 * (deutsche Komma-Note bzw. „bestanden"), null wenn keine Note existiert.
 */
data class EffectiveGrade(
    val source: GradeSource,
    val label: String?
) {
    val hasGrade: Boolean get() = label != null

    companion object {
        val NONE = EffectiveGrade(GradeSource.NONE, null)
    }
}

/**
 * Verknüpft Kurse read-only mit den Notenspiegel-Leistungen. [CourseEntity.grade]
 * bleibt user-editierbar und wird NIE automatisch überschrieben — hier wird die
 * Verknüpfung nur abgeleitet.
 *
 * Match-Strategie pro Kurs, in absteigender Verlässlichkeit (analog Klausur→Kurs
 * über [de.transio.hiuni.feature.lsf.data.LsfExamsRepository]):
 *  1. **Veranstaltungs-Nr** — `grade.veranstaltungsNr == course.lsfCode`. Deterministisch.
 *  2. **Titel + Semester** — normalisierter Titel-Match (Klein/Leerzeichen/Klammer-
 *     Suffixe wie „(Vorlesung)" entfernt) UND gleiches Semester ([Semester.parseLabel]).
 *
 * Bei mehreren Treffern (Wiederholungsversuche): [pickBest] wählt die beste Zeile —
 * bestandene vor nicht bestandenen, dann höchster Versuch, dann jüngstes Prüfungsdatum.
 */
object CourseGradeMatcher {

    /**
     * Effektive Note für [course] gegen alle [grades].
     *
     * Präzedenz: manuell gesetzte [CourseEntity.grade] hat Vorrang; sonst die beste
     * gematchte Notenspiegel-Note; sonst [EffectiveGrade.NONE].
     */
    fun effectiveGrade(course: CourseEntity, grades: List<GradeEntity>): EffectiveGrade {
        course.grade?.takeIf { it.isNotBlank() }?.let { manual ->
            return EffectiveGrade(GradeSource.MANUAL, manual.trim())
        }
        val match = bestMatch(course, grades) ?: return EffectiveGrade.NONE
        val label = displayLabel(match) ?: return EffectiveGrade.NONE
        return EffectiveGrade(GradeSource.NOTENSPIEGEL, label)
    }

    /**
     * Beste passende Notenspiegel-Zeile für [course] (ohne Rücksicht auf eine
     * manuelle Note). Null, wenn nichts matcht.
     */
    fun bestMatch(course: CourseEntity, grades: List<GradeEntity>): GradeEntity? {
        // 1) Veranstaltungs-Nr (deterministisch).
        val byNr = course.lsfCode?.takeIf { it.isNotBlank() }?.let { code ->
            grades.filter { it.veranstaltungsNr == code }
        }.orEmpty()
        if (byNr.isNotEmpty()) return pickBest(byNr)

        // 2) Titel + Semester (normalisiert).
        val courseTitle = normalizeTitle(course.name)
        if (courseTitle.isBlank()) return null
        val courseSem = Semester.parseLabel(course.semester)
        val byTitle = grades.filter { g ->
            normalizeTitle(g.titel) == courseTitle && sameSemester(courseSem, g.semester)
        }
        return if (byTitle.isNotEmpty()) pickBest(byTitle) else null
    }

    /**
     * Wählt aus mehreren Kandidaten (Wiederholungsversuche) den relevantesten:
     * bestandene bevorzugt, sonst höchster Versuch, dann jüngstes Prüfungsdatum.
     */
    private fun pickBest(candidates: List<GradeEntity>): GradeEntity? =
        candidates.maxWithOrNull(
            compareBy<GradeEntity> { if (it.status == GradeStatus.PASSED) 1 else 0 }
                .thenBy { it.versuch }
                .thenBy { it.pruefungsDatum ?: Long.MIN_VALUE }
        )

    /**
     * Anzeige-Text einer gematchten Note:
     *  - nicht bestanden → „nicht bestanden" (aussagekräftiger als die nackte 5,0),
     *  - sonst konkrete Note → deutsche Komma-Darstellung („2,7"),
     *  - bestanden ohne Note (z.B. Praktikum) → „bestanden",
     *  - angemeldet (REGISTERED) ohne Note → null → „steht noch aus".
     */
    private fun displayLabel(grade: GradeEntity): String? = when {
        grade.status == GradeStatus.FAILED -> "nicht bestanden"
        grade.note != null -> String.format(Locale.GERMAN, "%.1f", grade.note)
        grade.status == GradeStatus.PASSED -> "bestanden"
        else -> null // REGISTERED ohne Note → keine Note
    }

    private fun sameSemester(course: Semester?, gradeLabel: String): Boolean {
        val g = Semester.parseLabel(gradeLabel) ?: return false
        return course != null && course.ordinal == g.ordinal
    }

    /**
     * Normalisiert einen Titel für den Fallback-Vergleich: Klein, kollabierte
     * Whitespaces, entfernte Klammer-Suffixe („(Vorlesung)", „(Übung)" …) und
     * eine evtl. führende Veranstaltungs-Nr.
     */
    private fun normalizeTitle(raw: String): String =
        raw.lowercase()
            .replace(' ', ' ')
            .replace(LEADING_NR_REGEX, "")
            .replace(PAREN_SUFFIX_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private val LEADING_NR_REGEX = Regex("^\\s*\\d{3,6}\\s+")
    private val PAREN_SUFFIX_REGEX = Regex("\\([^)]*\\)")
    private val WHITESPACE_REGEX = Regex("\\s+")
}
