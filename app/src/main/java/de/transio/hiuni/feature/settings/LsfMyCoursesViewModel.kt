package de.transio.hiuni.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.feature.lsf.data.LsfMyCoursesRepository
import de.transio.hiuni.feature.lsf.data.MyCoursesSyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyCoursesUiState(
    val syncing: Boolean = false,
    val lastResult: MyCoursesSyncResult? = null,
    val errorMessage: String? = null,
    val hasSession: Boolean = false
)

@HiltViewModel
class LsfMyCoursesViewModel @Inject constructor(
    private val repository: LsfMyCoursesRepository,
    casSession: CasSession
) : ViewModel() {

    private val _syncing = MutableStateFlow(false)
    private val _lastResult = MutableStateFlow<MyCoursesSyncResult?>(null)
    private val _error = MutableStateFlow<String?>(null)

    val state: StateFlow<MyCoursesUiState> = combine(
        casSession.state, _syncing, _lastResult, _error
    ) { casState, syncing, last, err ->
        MyCoursesUiState(
            syncing = syncing,
            lastResult = last,
            errorMessage = err,
            hasSession = casState is CasState.Authenticated
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyCoursesUiState())

    fun sync() = viewModelScope.launch {
        _syncing.update { true }
        _error.update { null }
        when (val result = repository.sync()) {
            is AppResult.Success -> _lastResult.update { result.data }
            is AppResult.Failure -> _error.update { result.error.message ?: "Sync fehlgeschlagen" }
        }
        _syncing.update { false }
    }
}
