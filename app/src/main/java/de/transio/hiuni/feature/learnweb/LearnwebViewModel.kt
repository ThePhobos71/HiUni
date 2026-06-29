package de.transio.hiuni.feature.learnweb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.feature.learnweb.data.LearnwebCourse
import de.transio.hiuni.feature.learnweb.data.LearnwebRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LearnwebUiState(
    val courses: List<LearnwebCourse> = emptyList(),
    val isRefreshing: Boolean = false,
    val isAuthenticated: Boolean = false,
    /**
     * `true` sobald wir nach Cold-Start mindestens einen `refresh()`-Roundtrip
     * abgeschlossen haben (egal ob Erfolg oder Fehler). Wird im UI gebraucht,
     * um „noch nicht synchronisiert" vs. „synchronisiert, aber leer" zu
     * unterscheiden.
     */
    val initialSyncDone: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class LearnwebViewModel @Inject constructor(
    private val repository: LearnwebRepository,
    private val casSession: CasSession
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _initialSyncDone = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val state: StateFlow<LearnwebUiState> = combine(
        repository.observeCourses(),
        casSession.state,
        _isRefreshing,
        _initialSyncDone,
        _error
    ) { courses, casState, refreshing, syncDone, err ->
        LearnwebUiState(
            courses = courses,
            isRefreshing = refreshing,
            isAuthenticated = casState is CasState.Authenticated,
            initialSyncDone = syncDone,
            errorMessage = err
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), LearnwebUiState())

    init {
        viewModelScope.launch {
            // Beim Cold-Start einmal probieren, nur wenn auth — sonst no-op
            // (Repository würde sonst sowieso direkt mit "keine Session" abbrechen).
            if (casSession.state.value is CasState.Authenticated) {
                triggerRefresh(force = false)
            } else {
                _initialSyncDone.value = true
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            triggerRefresh(force = true)
        }
    }

    fun consumeError() {
        _error.value = null
    }

    private suspend fun triggerRefresh(force: Boolean) {
        _isRefreshing.value = true
        try {
            when (val res = repository.refresh(force = force)) {
                is AppResult.Success -> _error.value = null
                is AppResult.Failure -> _error.value =
                    res.error.message ?: "Aktualisieren fehlgeschlagen"
            }
        } finally {
            _isRefreshing.value = false
            _initialSyncDone.value = true
        }
    }
}
