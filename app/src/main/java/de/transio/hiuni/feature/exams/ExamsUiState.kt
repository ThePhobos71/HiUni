package de.transio.hiuni.feature.exams

import de.transio.hiuni.core.common.LoadStatus
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
    /**
     * Vereinheitlichter Lade-/Fehler-Status (siehe [LoadStatus]). `isLoading`
     * = Erst-Load bis zur ersten Emission (Skeleton), `isRefreshing` =
     * laufender LSF-Klausur-Sync. Klausuren kennen keine dedizierte
     * Fehlermeldung. Die Accessoren unten halten `state.isLoading`/
     * `isRefreshing` in Screen und Tests unverändert lesbar.
     */
    val load: LoadStatus = LoadStatus.Idle,
    /** Kurse für die optionale Kurs-Auswahl im Add/Edit-Sheet (neuestes Semester zuerst). */
    val courses: List<CourseEntity> = emptyList(),
    /**
     * Aktiver Editor-Zustand: `null` = geschlossen. Für Neuanlage steht eine
     * [ExamEntity] mit `rowId == 0` drin, fürs Bearbeiten der geladene Eintrag.
     */
    val editing: ExamEntity? = null,
    /**
     * Aus der Spotlight-Suche angesprungene Klausur (rowId): der Screen scrollt zur
     * Karte und hebt sie kurz hervor, danach räumt er das Feld per
     * [ExamsViewModel.clearExamFocus] ab.
     */
    val focusedExamRowId: Long? = null,
    /** Prozessweiter Netz-Status. Für die Stale-/Offline-Kennzeichnung unter der TopBar. */
    val isOnline: Boolean = true,
    /** Epoch-ms des letzten erfolgreichen LSF-Klausur-Refresh (0 = nie). Speist das StalenessLabel. */
    val lastRefreshEpoch: Long = 0L
) {
    val isLoading: Boolean get() = load.isLoading
    val isRefreshing: Boolean get() = load.isRefreshing

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

    /**
     * Position der Klausur in der Lazy-Liste bzw. im Grid: Hero (falls vorhanden)
     * an 0, danach der „Weitere Termine“-Header, dann die Timeline. `null`, wenn
     * die Klausur nicht (mehr) in der Liste steht.
     */
    fun listIndexOf(rowId: Long): Int? {
        val hero = nextExam
        if (hero?.rowId == rowId) return 0
        val timelineIndex = timelineExams.indexOfFirst { it.rowId == rowId }
        if (timelineIndex < 0) return null
        val heroOffset = if (hero != null) 1 else 0
        return heroOffset + 1 + timelineIndex
    }
}
