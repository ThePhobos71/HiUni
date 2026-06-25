package de.transio.hiuni.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * First-Launch-Onboarding-VM. Beobachtet [CasSession] damit Slide 2 automatisch
 * den Login-Erfolg erkennt; der Screen reagiert via LaunchedEffect und springt
 * fließend auf Slide 3 weiter. Notifications-Permission wird ans VM gepusht
 * (das System-Activity-Result kann nur die UI sehen), nicht inferiert.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val casSession: CasSession
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        // CasState + Profile bündeln, damit Slide 2 sowohl den Authenticated-Flag
        // als auch den Vornamen für das Begrüßungs-Häkchen anzeigen kann.
        viewModelScope.launch {
            combine(casSession.state, casSession.profile) { cas, profile ->
                (cas is CasState.Authenticated) to profile
            }.collect { (authenticated, profile) ->
                _state.update { it.copy(isAuthenticated = authenticated, profile = profile) }
            }
        }
    }

    fun nextSlide() {
        _state.update { current ->
            val next = (current.currentSlide + 1).coerceAtMost(OnboardingUiState.SLIDE_COUNT - 1)
            current.copy(currentSlide = next)
        }
    }

    fun prevSlide() {
        _state.update { current ->
            val prev = (current.currentSlide - 1).coerceAtLeast(0)
            current.copy(currentSlide = prev)
        }
    }

    fun goTo(index: Int) {
        val clamped = index.coerceIn(0, OnboardingUiState.SLIDE_COUNT - 1)
        _state.update { it.copy(currentSlide = clamped) }
    }

    fun onNotificationPermissionChanged(granted: Boolean) {
        _state.update { it.copy(hasNotificationsPermission = granted) }
    }

    fun markCompleted() {
        viewModelScope.launch {
            settings.setOnboardingCompleted(true)
        }
    }

    /**
     * Stellt sicher, dass nach erfolgreichem CAS-Login der CasSession-State frisch
     * gelesen wird. Die WebLoginActivity ruft `onLoginSuccess` selbst — dieser
     * Aufruf ist redundant aber günstig (idempotent) und macht den auto-forward
     * Flow robust gegen Race-Conditions zwischen Activity-Result und State-Update.
     */
    fun refreshAuthState() {
        casSession.refreshState()
    }
}
