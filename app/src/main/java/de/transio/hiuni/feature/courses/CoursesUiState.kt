package de.transio.hiuni.feature.courses

import de.transio.hiuni.core.common.LoadStatus
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.grades.data.GradeEntity

data class CoursesUiState(
    val courses: List<CourseEntity> = emptyList(),
    /**
     * Alle Notenspiegel-Leistungen. Rein read-only als Match-Basis für die pro Kurs
     * abgeleitete effektive Note ([effectiveGrade]) — die Kurs-DB wird nie überschrieben.
     */
    val grades: List<GradeEntity> = emptyList(),
    val selectedSemester: String? = null,
    val selectedCourseId: String? = null,
    val editing: CourseEntity? = null,
    /**
     * Vereinheitlichter Lade-/Fehler-Status (siehe [LoadStatus]). Kurse nutzen
     * nur `isRefreshing` (Pull-to-Refresh); der Accessor unten hält
     * `state.isRefreshing` im Screen unverändert lesbar.
     */
    val load: LoadStatus = LoadStatus.Idle
) {
    val isRefreshing: Boolean get() = load.isRefreshing

    val availableSemesters: List<String>
        get() = courses.map { it.semester }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedByDescending(::semesterSortKey)

    val visibleCourses: List<CourseEntity>
        get() {
            val filtered = if (selectedSemester == null) courses
            else courses.filter { it.semester == selectedSemester }
            // Tutorien/Übungen direkt unter ihre Mutter-Vorlesung ziehen.
            val parents = filtered.filter { it.parentLsfId == null }
            val ordered = mutableListOf<CourseEntity>()
            val seenIds = HashSet<String>()
            for (parent in parents) {
                ordered += parent
                seenIds += parent.id
                val children = filtered.filter { it.parentLsfId != null && it.parentLsfId == parent.lsfId }
                ordered += children
                seenIds += children.map { it.id }
            }
            // Waisen (Parent außerhalb der Filterung) hinten anhängen, damit nichts wegfällt.
            ordered += filtered.filter { it.id !in seenIds }
            return ordered
        }

    fun parentOf(course: CourseEntity): CourseEntity? =
        course.parentLsfId?.let { pid -> courses.firstOrNull { it.lsfId == pid } }

    val totalCredits: Int get() = visibleCourses.sumOf { it.credits }
    val semestersSeen: Int get() = availableSemesters.size

    val selectedCourse: CourseEntity?
        get() = selectedCourseId?.let { id -> courses.firstOrNull { it.id == id } }

    val showEditSheet: Boolean get() = editing != null

    /**
     * Effektive Note für [course]: manuell gesetzte [CourseEntity.grade] hat Vorrang,
     * sonst die aus dem Notenspiegel gematchte Note, sonst „steht noch aus".
     */
    fun effectiveGrade(course: CourseEntity): EffectiveGrade =
        CourseGradeMatcher.effectiveGrade(course, grades)
}

/**
 * Vergleichsschlüssel für deutsche Uni-Semester-Strings ("Sommer 2026", "Winter 2025/26").
 * Sortiert nach Akademiejahr-Ende, danach Wintersemester vor Sommersemester desselben
 * Endjahres. Unbekannte Formate landen am Ende.
 */
internal fun semesterSortKey(semester: String): Long {
    val sommer = Regex("Sommer\\s+(\\d{4})").find(semester)
    if (sommer != null) {
        val year = sommer.groupValues[1].toLong()
        return year * 10 + 1
    }
    val winter = Regex("Winter\\s+(\\d{4})").find(semester)
    if (winter != null) {
        val startYear = winter.groupValues[1].toLong()
        return (startYear + 1) * 10
    }
    return Long.MIN_VALUE
}
