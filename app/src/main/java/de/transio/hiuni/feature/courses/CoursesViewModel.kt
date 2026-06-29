package de.transio.hiuni.feature.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.courses.data.CourseRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Kurse werden ausschließlich über den LSF-Import (Settings) angelegt und gelöscht.
 * Der User kann pro Kurs nur seine eigenen Tracking-Felder editieren — Stammdaten
 * (Name, Dozent, LP, Semester, Raum) sind read-only weil sie beim nächsten Sync
 * sowieso vom LSF überschrieben werden.
 *
 * Semester-Switcher: default ist das neueste vorhandene Semester. User kann via
 * Chip-Row in der UI wechseln; die Auswahl überlebt nicht App-Restart (kein DataStore).
 */
@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val repository: CourseRepository
) : ViewModel() {

    private val _selectedId = MutableStateFlow<String?>(null)
    private val _editing = MutableStateFlow<CourseEntity?>(null)
    private val _selectedSemester = MutableStateFlow<String?>(null)

    val state: StateFlow<CoursesUiState> = combine(
        repository.observeAll(),
        _selectedId,
        _editing,
        _selectedSemester
    ) { courses, selectedId, editing, userSemester ->
        // Wenn der User noch nichts gewählt hat → neuestes Semester aus der Liste.
        val semesters = courses.map { it.semester }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedByDescending(::semesterSortKey)
        val effectiveSemester = userSemester?.takeIf { it in semesters }
            ?: semesters.firstOrNull()
        CoursesUiState(
            courses = courses,
            selectedSemester = effectiveSemester,
            selectedCourseId = selectedId,
            editing = editing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), CoursesUiState())

    fun select(id: String?) { _selectedId.update { id } }

    /** Springe direkt in die Detail-Ansicht eines Kurses anhand seiner LSF-publishid. */
    fun selectByLsfId(lsfId: String) = viewModelScope.launch {
        repository.findByLsfId(lsfId)?.let { course ->
            _selectedSemester.update { course.semester }
            _selectedId.update { course.id }
        }
    }

    fun selectSemester(semester: String) { _selectedSemester.update { semester } }

    fun startEdit(course: CourseEntity) { _editing.update { course } }

    fun dismissSheet() { _editing.update { null } }

    fun save(course: CourseEntity) = viewModelScope.launch {
        repository.upsert(course)
        dismissSheet()
    }

    // ── Notes-Autosave ──────────────────────────────────────────────────────
    //
    // Pro Keystroke kommt ein (courseId, notes)-Paar rein. Wir debouncen 500ms,
    // damit nur die letzte Pause-Variante in die DB geschrieben wird, statt jede
    // Buchstaben-Änderung einzeln. Course-spezifische DistinctUntilChanged-Logik
    // brauchen wir nicht — REPLACE-Upsert ist idempotent.

    private val _notesEdits = MutableSharedFlow<Pair<String, String>>(
        extraBufferCapacity = 16
    )

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _notesEdits
                .debounce(500L)
                .collect { (courseId, notes) ->
                    val current = repository.findById(courseId) ?: return@collect
                    val clean = notes.takeIf { it.isNotBlank() }
                    if (current.notes == clean) return@collect
                    repository.upsert(current.copy(notes = clean))
                }
        }
    }

    /**
     * Setzt die Notiz für [courseId]. Aufruf pro Keystroke OK — wird gedebounced.
     * Beim ersten Wechsel zwischen Kursen geht ggf. ein pending Edit für den
     * vorigen Kurs trotzdem durch (debounce sammelt nicht nach Key).
     */
    fun updateNotes(courseId: String, notes: String) {
        _notesEdits.tryEmit(courseId to notes)
    }
}
