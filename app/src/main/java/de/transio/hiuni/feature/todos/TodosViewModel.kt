package de.transio.hiuni.feature.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.feature.todos.data.TodoEntity
import de.transio.hiuni.feature.todos.data.TodosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TodosViewModel @Inject constructor(
    private val repository: TodosRepository
) : ViewModel() {

    private val _editing = MutableStateFlow<TodoEntity?>(null)
    private val _isAddSheetOpen = MutableStateFlow(false)

    val state: StateFlow<TodosUiState> = combine(
        repository.observeAll(),
        _editing,
        _isAddSheetOpen
    ) { todos, editing, sheetOpen ->
        TodosUiState(todos = todos, editing = editing, isAddSheetOpen = sheetOpen)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodosUiState())

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

    fun save(existingId: Long, title: String, dueDate: LocalDate?) = viewModelScope.launch {
        val clean = title.trim()
        if (clean.isEmpty()) return@launch
        val existing = existingId.takeIf { it != 0L }?.let { repository.observeAll() }
        // Wir brauchen die Werte (isDone/createdAt/sortIndex) der bestehenden Aufgabe nicht
        // separat zu laden — beim Edit setzen wir nur Titel + Fälligkeit, der Rest bleibt
        // erhalten. Für saubere Semantik wird beim Edit ein update über die Repo-Funktion
        // mit `copy()` durchgeführt; beim Insert legt Room id=0 ⇒ AUTOINCREMENT.
        if (existingId == 0L) {
            repository.upsert(
                TodoEntity(
                    id = 0L,
                    title = clean,
                    dueDate = dueDate,
                    isDone = false
                )
            )
        } else {
            // Edit-Pfad: bestehende Felder beibehalten, nur Titel + Fälligkeit anpassen.
            val current = _editing.value
            if (current != null && current.id == existingId) {
                repository.upsert(current.copy(title = clean, dueDate = dueDate))
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
