package de.transio.hiuni.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
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
    private val lsfClient: LsfClient
) : ViewModel() {

    val state: StateFlow<CasState> = casSession.state

    private val _ui = MutableStateFlow(CasUiState())
    val ui: StateFlow<CasUiState> = _ui.asStateFlow()

    // Der First-Login-Trigger (LSF-Sync + Email-Refresh bei auth-Transition)
    // sitzt jetzt im App-scoped LoginSyncOrchestrator. So feuert er auch dann,
    // wenn der allererste CAS-Login im Onboarding läuft — also bevor die
    // CasLoginCard (und damit dieses ViewModel) je instanziiert wurde.

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
