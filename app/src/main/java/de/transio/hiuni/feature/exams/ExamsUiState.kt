package de.transio.hiuni.feature.exams

import de.transio.hiuni.feature.lsf.data.ExamEntity
import java.time.LocalDate

/**
 * View-State des Klausurplan-Screens.
 *
 * Sortierung: Klausuren MIT [ExamEntity.examDate] zuerst aufsteigend nach Datum,
 * danach die ohne Datum ans Ende — das DAO liefert bereits in dieser Reihenfolge
 * (`ORDER BY (examDate IS NULL), examDate ASC`), wir verlassen uns aber nicht
 * darauf und sortieren defensiv im VM nochmal.
 */
data class ExamsUiState(
    val exams: List<ExamEntity> = emptyList(),
    val isLoading: Boolean = false
) {
    /**
     * Die nächste zukünftige Klausur mit Datum — Basis fürs Countdown-Hero.
     * Klausuren ohne Datum oder in der Vergangenheit sind kein Hero-Kandidat.
     */
    val nextExam: ExamEntity?
        get() {
            val today = LocalDate.now()
            return exams.firstOrNull { it.examDate != null && !it.examDate.isBefore(today) }
        }

    /**
     * Alle anderen Klausuren für die Timeline-Liste — das Hero-Item wird hier
     * ausgespart, damit es nicht doppelt erscheint.
     */
    val timelineExams: List<ExamEntity>
        get() {
            val hero = nextExam ?: return exams
            return exams.filter { it.rowId != hero.rowId }
        }
}
