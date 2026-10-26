package de.transio.hiuni.feature.movies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.common.LoadStatus
import de.transio.hiuni.feature.movies.data.MoviesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Liest den Film samt TMDB-Details (Backdrop, Bewertung, Regie, Cast) nur aus Room.
 * Die Details holt der Programm-Refresh im Hintergrund (siehe
 * `MoviesRepositoryImpl.enrichWithTmdb`), das Öffnen kostet also keinen Netz-Roundtrip.
 */
@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MoviesRepository
) : ViewModel() {

    private val filmId: String = savedStateHandle["filmId"] ?: ""
    private val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _state = MutableStateFlow(MovieDetailUiState())
    val state: StateFlow<MovieDetailUiState> = _state.asStateFlow()

    init { load() }

    private fun load() = viewModelScope.launch {
        val movie = repository.findById(filmId, sessionId)
        _state.update {
            it.copy(
                load = LoadStatus.Idle,
                movie = movie,
                rating = movie?.tmdbRating,
                voteCount = movie?.tmdbVoteCount,
                backdropUrl = movie?.backdropUrl,
                cast = movie?.castNames.orEmpty(),
                crewDirector = movie?.director
            )
        }
    }
}
