package de.transio.hiuni.feature.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.courses.data.CourseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val repository: CourseRepository
) : ViewModel() {

    private val _selectedId = MutableStateFlow<String?>(null)
    private val _editing = MutableStateFlow<CourseEntity?>(null)
    private val _showAddSheet = MutableStateFlow(false)

    val state: StateFlow<CoursesUiState> = combine(
        repository.observeAll(),
        _selectedId,
        _editing,
        _showAddSheet
    ) { courses, selectedId, editing, showAddSheet ->
        CoursesUiState(
            courses = courses,
            selectedCourseId = selectedId,
            editing = editing,
            showAddSheet = showAddSheet
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoursesUiState())

    fun select(id: String?) { _selectedId.update { id } }

    fun startAdd() {
        _editing.update { null }
        _showAddSheet.update { true }
    }

    fun startEdit(course: CourseEntity) {
        _editing.update { course }
        _showAddSheet.update { true }
    }

    fun dismissSheet() {
        _showAddSheet.update { false }
        _editing.update { null }
    }

    fun save(course: CourseEntity) = viewModelScope.launch {
        repository.upsert(course)
        dismissSheet()
    }

    fun delete(id: String) = viewModelScope.launch {
        repository.deleteById(id)
        if (_selectedId.value == id) _selectedId.update { null }
    }
}
