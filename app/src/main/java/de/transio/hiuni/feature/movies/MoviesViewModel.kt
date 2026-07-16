package de.transio.hiuni.feature.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.network.ConnectivityObserver
import de.transio.hiuni.core.network.OfflineMessages
import de.transio.hiuni.feature.movies.data.MoviesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val repository: MoviesRepository,
    private val connectivity: ConnectivityObserver
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private data class LoadState(
        val isRefreshing: Boolean,
        val isLoading: Boolean,
        val errorMessage: String?
    )

    private val loadStateFlow = combine(
        _isRefreshing,
        _isLoading,
        _errorMessage
    ) { refreshing, loading, error -> LoadState(refreshing, loading, error) }

    val state: StateFlow<MoviesUiState> = combine(
        repository.observeUpcoming(),
        loadStateFlow
    ) { movies, load ->
        MoviesUiState(
            movies = movies,
            isRefreshing = load.isRefreshing,
            isLoading = load.isLoading,
            errorMessage = load.errorMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), MoviesUiState())

    /** Erst-Load läuft genau einmal (init) mit Skeleton-Anzeige. */
    private var isInitialLoad = true

    init { refresh(force = false) }

    fun refresh(force: Boolean = true) = viewModelScope.launch {
        val initial = isInitialLoad
        isInitialLoad = false
        if (initial) _isLoading.value = true else _isRefreshing.value = true
        _errorMessage.value = null
        when (val result = repository.refresh(force = force)) {
            is AppResult.Success -> Unit
            is AppResult.Failure -> _errorMessage.value =
                if (!connectivity.isOnline.value) OfflineMessages.NO_CONNECTION
                else result.error.message ?: "unifilm.de nicht erreichbar"
        }
        _isLoading.value = false
        _isRefreshing.value = false
    }
}
