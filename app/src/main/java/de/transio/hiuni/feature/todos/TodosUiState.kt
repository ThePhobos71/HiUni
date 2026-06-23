package de.transio.hiuni.feature.todos

import de.transio.hiuni.feature.todos.data.TodoEntity

/**
 * View-State des Aufgaben-Screens.
 */
data class TodosUiState(
    val todos: List<TodoEntity> = emptyList(),
    val isAddSheetOpen: Boolean = false,
    val editing: TodoEntity? = null
) {
    val openCount: Int get() = todos.count { !it.isDone }
}
