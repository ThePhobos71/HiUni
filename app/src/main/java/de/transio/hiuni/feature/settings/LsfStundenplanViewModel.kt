package de.transio.hiuni.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.feature.lsf.data.LsfStundenplanRepository
import de.transio.hiuni.feature.lsf.data.StundenplanSyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StundenplanUiState(
    val syncing: Boolean = false,
    val lastResult: StundenplanSyncResult? = null,
    val errorMessage: String? = null,
    val hasSession: Boolean = false
)

@HiltViewModel
class LsfStundenplanViewModel @Inject constructor(
    private val repository: LsfStundenplanRepository,
    casSession: CasSession
) : ViewModel() {

    private val _syncing = MutableStateFlow(false)
    private val _lastResult = MutableStateFlow<StundenplanSyncResult?>(null)
    private val _error = MutableStateFlow<String?>(null)

    val state: StateFlow<StundenplanUiState> = combine(
        casSession.state, _syncing, _lastResult, _error
    ) { casState, syncing, last, err ->
        StundenplanUiState(
            syncing = syncing,
            lastResult = last,
            errorMessage = err,
            hasSession = casState is CasState.Authenticated
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), StundenplanUiState())

    fun sync() = viewModelScope.launch {
        _syncing.update { true }
        _error.update { null }
        when (val result = repository.sync()) {
            is AppResult.Success -> _lastResult.update { result.data }
            is AppResult.Failure -> _error.update { result.error.message ?: "Sync fehlgeschlagen" }
        }
        _syncing.update { false }
    }

    fun consumeError() { _error.update { null } }
}
