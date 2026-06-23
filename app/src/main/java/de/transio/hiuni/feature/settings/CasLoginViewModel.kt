package de.transio.hiuni.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.sync.LsfSyncScheduler
import de.transio.hiuni.feature.lsf.data.LsfClient
import de.transio.hiuni.feature.lsf.data.LsfConnectionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CasUiState(
    val testing: Boolean = false,
    val lastTestResult: LsfConnectionInfo? = null
)

@HiltViewModel
class CasLoginViewModel @Inject constructor(
    private val casSession: CasSession,
    private val lsfClient: LsfClient,
    private val lsfSyncScheduler: LsfSyncScheduler
) : ViewModel() {

    val state: StateFlow<CasState> = casSession.state

    private val _ui = MutableStateFlow(CasUiState())
    val ui: StateFlow<CasUiState> = _ui.asStateFlow()

    init {
        // First-Login-Trigger: beobachte den CAS-State und feuere genau dann
        // einen one-time LSF-Sync, wenn der State von Non-Authenticated nach
        // Authenticated wechselt. Dadurch decken wir sowohl den allerersten
        // Login als auch ein Re-Auth nach abgelaufener Sitzung ab.
        viewModelScope.launch {
            var lastWasAuthenticated = casSession.state.value is CasState.Authenticated
            casSession.state.collect { current ->
                val isAuthenticated = current is CasState.Authenticated
                if (isAuthenticated && !lastWasAuthenticated) {
                    lsfSyncScheduler.triggerNow()
                }
                lastWasAuthenticated = isAuthenticated
            }
        }
    }

    fun onLoginResult(success: Boolean) {
        if (success) casSession.refreshState()
    }

    fun logout() {
        casSession.logout()
        _ui.update { CasUiState() }
    }

    fun testLsfConnection() = viewModelScope.launch {
        _ui.update { it.copy(testing = true, lastTestResult = null) }
        val result = lsfClient.testConnection()
        _ui.update { it.copy(testing = false, lastTestResult = result) }
    }
}
