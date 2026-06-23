package de.transio.hiuni.feature.todos

import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.todos.data.TodoEntity

/**
 * View-State des Aufgaben-Screens.
 */
data class TodosUiState(
    val todos: List<TodoEntity> = emptyList(),
    val courses: List<CourseEntity> = emptyList(),
    val isAddSheetOpen: Boolean = false,
    val editing: TodoEntity? = null
) {
    val openCount: Int get() = todos.count { !it.isDone }

    /** Schnelles Lookup zum Rendern der Kurs-Pille; `null` wenn Aufgabe keinem Kurs zugeordnet. */
    val coursesById: Map<String, CourseEntity>
        get() = courses.associateBy { it.id }
}
