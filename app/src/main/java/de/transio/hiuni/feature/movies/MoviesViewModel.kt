package de.transio.hiuni.feature.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.common.AppResult
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
    private val repository: MoviesRepository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<MoviesUiState> = combine(
        repository.observeUpcoming(),
        _isRefreshing,
        _errorMessage
    ) { movies, refreshing, error ->
        MoviesUiState(
            movies = movies,
            isRefreshing = refreshing,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoviesUiState())

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _isRefreshing.value = true
        _errorMessage.value = null
        when (val result = repository.refresh()) {
            is AppResult.Success -> Unit
            is AppResult.Failure -> _errorMessage.value =
                result.error.message ?: "unifilm.de nicht erreichbar"
        }
        _isRefreshing.value = false
    }
}
