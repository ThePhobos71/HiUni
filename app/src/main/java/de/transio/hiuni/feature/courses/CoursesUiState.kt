package de.transio.hiuni.feature.courses

import de.transio.hiuni.feature.courses.data.CourseEntity

data class CoursesUiState(
    val courses: List<CourseEntity> = emptyList(),
    val selectedSemester: String? = null,
    val selectedCourseId: String? = null,
    val editing: CourseEntity? = null,
    val isRefreshing: Boolean = false
) {
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
