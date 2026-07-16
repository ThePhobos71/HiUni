package de.transio.hiuni.feature.exams

import de.transio.hiuni.feature.courses.data.CourseEntity
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
    val isLoading: Boolean = false,
    /** Pull-to-Refresh-Indicator. True solange ein LSF-Klausur-Sync läuft. */
    val isRefreshing: Boolean = false,
    /** Kurse für die optionale Kurs-Auswahl im Add/Edit-Sheet (neuestes Semester zuerst). */
    val courses: List<CourseEntity> = emptyList(),
    /**
     * Aktiver Editor-Zustand: `null` = geschlossen. Für Neuanlage steht eine
     * [ExamEntity] mit `rowId == 0` drin, fürs Bearbeiten der geladene Eintrag.
     */
    val editing: ExamEntity? = null
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
