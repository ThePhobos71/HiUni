package de.transio.hiuni.feature.sport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.LoadStatus
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.network.ConnectivityObserver
import de.transio.hiuni.core.network.OfflineMessages
import de.transio.hiuni.feature.sport.data.SportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SportViewModel @Inject constructor(
    private val repository: SportRepository,
    private val connectivity: ConnectivityObserver,
    settings: SettingsDataStore
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _selectedFilter = MutableStateFlow<String?>(null)
    private val _lastError = MutableStateFlow<String?>(null)

    private val connectivityStateFlow = combine(
        connectivity.isOnline,
        settings.lastSportRefreshEpoch
    ) { online, epoch -> online to epoch }

    val state: StateFlow<SportUiState> = combine(
        repository.observeUpcoming(),
        repository.observeDistinctTitles(),
        _selectedFilter,
        combine(_isRefreshing, _lastError) { r, e -> r to e },
        connectivityStateFlow
    ) { events, titles, filter, refreshError, conn ->
        // Aktive Filter-Selektion, die im neuen Datenbestand nicht mehr existiert,
        // wird transparent gedroppt — UX-Detail damit kein "leeres Filter-Loch" entsteht.
        val cleanedFilter = filter?.takeIf { it in titles }
        SportUiState(
            events = events,
            distinctTitles = titles,
            selectedFilter = cleanedFilter,
            load = LoadStatus(isRefreshing = refreshError.first, error = refreshError.second),
            isOnline = conn.first,
            lastRefreshEpoch = conn.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), SportUiState())

    init {
        viewModelScope.launch {
            // Beim Cold-Start einmal das aktuelle Fenster ziehen — wenn die
            // Throttle 6h Refresh schon belegt, no-op.
            triggerRefresh(force = false)
        }
    }

    fun refresh() = viewModelScope.launch {
        triggerRefresh(force = true)
    }

    fun setFilter(title: String?) {
        _selectedFilter.value = title
    }

    fun consumeError() {
        _lastError.value = null
    }

    private suspend fun triggerRefresh(force: Boolean) {
        _isRefreshing.value = true
        try {
            when (val res = repository.refresh(force = force)) {
                is AppResult.Success -> _lastError.value = null
                is AppResult.Failure -> _lastError.value =
                    if (!connectivity.isOnline.value) OfflineMessages.NO_CONNECTION
                    else res.error.message ?: "Aktualisieren fehlgeschlagen"
            }
        } finally {
            _isRefreshing.value = false
        }
    }
}
