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
 * First-Launch-Onboarding-VM. Beobachtet [CasSession] damit Slide 2 (Login)
 * den Login-Erfolg erkennt und [SettingsDataStore.lastLsfSyncEpoch] damit der
 * "Wir holen jetzt deine Kurse…"-Status sich von alleine schließt, sobald der
 * LsfSyncWorker einmal durchgelaufen ist. Notifications-Permission wird ans
 * VM gepusht (das System-Activity-Result kann nur die UI sehen), nicht inferiert.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val casSession: CasSession
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        // CasState + Profile + LSF-Sync-Timestamp bündeln: Slide 2 braucht den
        // Authenticated-Flag, das Profil für die Begrüßung, und das Timestamp-
        // Flag, um zu entscheiden ob noch der Progress-Hinweis angezeigt wird.
        viewModelScope.launch {
            combine(
                casSession.state,
                casSession.profile,
                settings.lastLsfSyncEpoch
            ) { cas, profile, lastSyncEpoch ->
                Triple(cas is CasState.Authenticated, profile, lastSyncEpoch)
            }.collect { (authenticated, profile, lastSyncEpoch) ->
                _state.update {
                    it.copy(
                        isAuthenticated = authenticated,
                        profile = profile,
                        initialLsfSyncDone = lastSyncEpoch > 0L
                    )
                }
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

    /**
     * Markiert den initialen Sync als "lange genug gewartet" — der UI-Status
     * geht von "wir holen…" auf "läuft im Hintergrund weiter, du kannst schon
     * weitermachen". Aufruf erfolgt nach 15s durch einen LaunchedEffect im
     * Screen, falls in der Zwischenzeit keine echte Completion eingetreten ist.
     */
    fun markInitialSyncTimedOut() {
        _state.update { it.copy(initialLsfSyncTimedOut = true) }
    }

    /**
     * Speichert die User-Entscheidung aus der Bio-Schutz-Slide. Wird nur nach
     * erfolgreichem BiometricPrompt aufgerufen — bei "Später entscheiden"
     * bleibt der Default `false` und es passiert hier nichts.
     */
    fun setMailRequiresBiometric(enabled: Boolean) {
        viewModelScope.launch {
            settings.setMailRequiresBiometric(enabled)
        }
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
