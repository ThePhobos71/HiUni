package de.transio.hiuni.feature.grades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.AuthRequiredException
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.network.ConnectivityObserver
import de.transio.hiuni.feature.grades.data.GradesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel des Noten-Screens.
 *
 * Fasst die drei Repository-Flows (Leistungen, Summen, CAS-State) plus die drei
 * lokalen Status-Flags (Refreshing / erster-Sync-abgeschlossen / Fehler) zu
 * einem [GradesUiState] zusammen. Der State-Aufbau folgt exakt dem Learnweb-VM:
 *  - Cold-Start feuert genau dann einen Refresh, wenn eine CAS-Session besteht.
 *  - `isLoading` = erster Roundtrip läuft UND noch kein Content im Cache.
 *  - Fehler landet in `errorMessage`; der Screen entscheidet Snackbar vs. ErrorState.
 */
@HiltViewModel
class GradesViewModel @Inject constructor(
    private val repository: GradesRepository,
    private val casSession: CasSession,
    private val connectivity: ConnectivityObserver,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _initialSyncDone = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _authRequired = MutableStateFlow(false)

    private val statusFlow = combine(
        _isRefreshing,
        _initialSyncDone,
        _error,
        _authRequired
    ) { refreshing, syncDone, error, auth -> Status(refreshing, syncDone, error, auth) }

    // isOnline + letzter Refresh-Timestamp gebündelt, damit der Top-Level-combine
    // beim 5-Arg-Limit bleibt.
    private val staleFlow = combine(
        connectivity.isOnline,
        settings.lastGradesRefreshEpoch
    ) { online, lastRefresh -> online to lastRefresh }

    val state: StateFlow<GradesUiState> = combine(
        repository.observeAll(),
        repository.observeSummary(),
        casSession.state,
        statusFlow,
        staleFlow
    ) { grades, summary, casState, status, stale ->
        val (isOnline, lastRefresh) = stale
        val semesters = GradesUiState.groupBySemester(grades)
        val hasCache = grades.isNotEmpty() || summary != null
        // Session weg/abgelaufen? „Weg" heißt hier: VM-Flag ODER CAS nicht mehr
        // authenticated.
        val sessionGone = status.authRequired || casState !is CasState.Authenticated
        GradesUiState(
            gpa = summary?.gpa,
            totalLp = summary?.totalLp,
            semesters = semesters,
            // Erster Cold-Start läuft noch und wir haben nichts im Cache → Skeleton.
            isLoading = !status.syncDone && !hasCache,
            isRefreshing = status.refreshing,
            // Auth-Hinweis (Vollbild) nur ohne Cache — mit Cache bleibt der Nutzer
            // lieber bei den Stale-Daten.
            isAuthRequired = sessionGone && !hasCache,
            // Reauth-Banner: Session weg ABER Cache vorhanden → dezenter Hinweis
            // über den (stale) Daten statt sie zu verstecken.
            showReauthBanner = sessionGone && hasCache,
            isOnline = isOnline,
            lastRefreshEpoch = lastRefresh,
            errorMessage = status.error
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(60_000),
        GradesUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            if (casSession.state.value is CasState.Authenticated) {
                triggerRefresh(force = false)
            } else {
                // Ohne Session gar nicht erst versuchen — Cold-Start ist „fertig",
                // der Auth-Hinweis (bzw. Cache) übernimmt.
                _authRequired.value = true
                _initialSyncDone.value = true
            }
        }
    }

    /** Pull-to-Refresh / Retry-Button: erzwingt einen frischen LSF-Roundtrip. */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            triggerRefresh(force = true)
        }
    }

    /** Snackbar-Consume: nachdem der Fehler einmal angezeigt wurde. */
    fun consumeError() {
        _error.value = null
    }

    private suspend fun triggerRefresh(force: Boolean) {
        _isRefreshing.value = true
        try {
            when (val res = repository.refresh(force = force)) {
                is AppResult.Success -> {
                    _error.value = null
                    _authRequired.value = false
                }
                is AppResult.Failure -> {
                    if (res.error is AuthRequiredException) {
                        _authRequired.value = true
                        // Kein Snackbar-Fehler zusätzlich — die Auth-Karte trägt die Info.
                        _error.value = null
                    } else {
                        _authRequired.value = false
                        _error.value = res.error.message ?: "Aktualisieren fehlgeschlagen"
                    }
                }
            }
        } finally {
            _isRefreshing.value = false
            _initialSyncDone.value = true
        }
    }

    private data class Status(
        val refreshing: Boolean,
        val syncDone: Boolean,
        val error: String?,
        val authRequired: Boolean
    )
}
