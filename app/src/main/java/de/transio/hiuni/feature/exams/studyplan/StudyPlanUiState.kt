package de.transio.hiuni.feature.exams.studyplan

import java.time.LocalDate

/** Eine Klausur in der Auswahl des Lernplan-Sheets. */
data class StudyPlanCandidate(
    val exam: StudyPlanExam,
    val selected: Boolean
)

/** Wie der Starttag gewählt wurde. */
enum class StudyPlanStart { TODAY, TOMORROW, CUSTOM }

/**
 * View-State des Lernplan-Sheets und der Lernplan-Karte auf dem Klausuren-Screen.
 */
data class StudyPlanUiState(
    val isOpen: Boolean = false,
    val settings: StudyPlanSettings = StudyPlanSettings(),
    /** Alle künftigen Klausuren mit Datum, nach Datum sortiert. */
    val candidates: List<StudyPlanCandidate> = emptyList(),
    val startDate: LocalDate? = null,
    val start: StudyPlanStart = StudyPlanStart.TOMORROW,
    /** Vorschau mit den aktuellen Einstellungen, `null` solange sie berechnet wird. */
    val preview: StudyPlan? = null,
    /** Bereits eingetragene, noch nicht begonnene Lernblöcke. */
    val activeBlockCount: Int = 0,
    /** Datum des letzten eingetragenen Lernblocks. */
    val activeUntil: LocalDate? = null,
    val isApplying: Boolean = false,
    /** Einmalige Rückmeldung (Snackbar), z. B. nach dem Übernehmen. */
    val message: String? = null
) {
    val hasCandidates: Boolean get() = candidates.isNotEmpty()
    val selectedCount: Int get() = candidates.count { it.selected }
    val canApply: Boolean get() = !isApplying && preview?.blocks?.isNotEmpty() == true
    val hasActivePlan: Boolean get() = activeBlockCount > 0
}
