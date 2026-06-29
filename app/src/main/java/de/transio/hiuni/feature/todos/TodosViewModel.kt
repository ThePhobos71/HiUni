package de.transio.hiuni.feature.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.feature.courses.data.CourseRepository
import de.transio.hiuni.feature.todos.data.TodoEntity
import de.transio.hiuni.feature.todos.data.TodosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TodosViewModel @Inject constructor(
    private val repository: TodosRepository,
    courseRepository: CourseRepository
) : ViewModel() {

    private val _editing = MutableStateFlow<TodoEntity?>(null)
    private val _isAddSheetOpen = MutableStateFlow(false)

    val state: StateFlow<TodosUiState> = combine(
        repository.observeAll(),
        courseRepository.observeAll(),
        _editing,
        _isAddSheetOpen
    ) { todos, courses, editing, sheetOpen ->
        TodosUiState(
            todos = todos,
            courses = courses,
            editing = editing,
            isAddSheetOpen = sheetOpen
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), TodosUiState())

    fun openAdd() {
        _editing.value = null
        _isAddSheetOpen.value = true
    }

    fun openEdit(todo: TodoEntity) {
        _editing.value = todo
        _isAddSheetOpen.value = true
    }

    fun closeSheet() {
        _isAddSheetOpen.value = false
        _editing.value = null
    }

    fun save(
        existingId: Long,
        title: String,
        dueDate: LocalDate?,
        courseId: String?
    ) = viewModelScope.launch {
        val clean = title.trim()
        if (clean.isEmpty()) return@launch
        if (existingId == 0L) {
            repository.upsert(
                TodoEntity(
                    id = 0L,
                    title = clean,
                    dueDate = dueDate,
                    isDone = false,
                    courseId = courseId
                )
            )
        } else {
            // Edit-Pfad: bestehende Felder (isDone/createdAt/sortIndex/...) beibehalten,
            // nur Titel, Fälligkeit und Kurs-Zuordnung anpassen.
            val current = _editing.value
            if (current != null && current.id == existingId) {
                repository.upsert(
                    current.copy(title = clean, dueDate = dueDate, courseId = courseId)
                )
            }
        }
        closeSheet()
    }

    fun toggleDone(todo: TodoEntity) = viewModelScope.launch {
        repository.setDone(todo.id, !todo.isDone)
    }

    fun delete(todo: TodoEntity) = viewModelScope.launch {
        repository.delete(todo.id)
    }
}
