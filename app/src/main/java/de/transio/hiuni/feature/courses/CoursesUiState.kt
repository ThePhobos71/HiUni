package de.transio.hiuni.feature.courses

import de.transio.hiuni.feature.courses.data.CourseEntity

data class CoursesUiState(
    val courses: List<CourseEntity> = emptyList(),
    val selectedCourseId: String? = null,
    val editing: CourseEntity? = null,
    val showAddSheet: Boolean = false
) {
    val totalCredits: Int get() = courses.sumOf { it.credits }
    val semestersSeen: Int get() = courses.map { it.semester }.distinct().size
    val selectedCourse: CourseEntity?
        get() = selectedCourseId?.let { id -> courses.firstOrNull { it.id == id } }
}
