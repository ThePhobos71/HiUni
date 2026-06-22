package de.transio.hiuni.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CasLoginViewModel @Inject constructor(
    private val casSession: CasSession
) : ViewModel() {

    val state: StateFlow<CasState> = casSession.state

    /**
     * Wird vom Activity-Result-Callback der WebLoginActivity gerufen. Bei Success ist
     * der State schon von der Activity aktualisiert; wir refreshen nur, falls sich die
     * Persistenz vs. State-Flow noch nicht synchronisiert hat.
     */
    fun onLoginResult(success: Boolean) {
        if (success) casSession.refreshState()
    }

    fun logout() {
        casSession.logout()
    }
}
