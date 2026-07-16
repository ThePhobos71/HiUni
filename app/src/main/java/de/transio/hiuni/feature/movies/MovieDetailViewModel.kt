package de.transio.hiuni.feature.movies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.common.LoadStatus
import de.transio.hiuni.feature.movies.data.MoviesRepository
import de.transio.hiuni.feature.movies.data.TmdbApiService
import de.transio.hiuni.feature.movies.data.tmdbSearchCandidates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MoviesRepository,
    private val tmdb: TmdbApiService
) : ViewModel() {

    private val filmId: String = savedStateHandle["filmId"] ?: ""
    private val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _state = MutableStateFlow(MovieDetailUiState())
    val state: StateFlow<MovieDetailUiState> = _state.asStateFlow()

    init { load() }

    private fun load() = viewModelScope.launch {
        val movie = repository.findById(filmId, sessionId)
        if (movie == null) {
            _state.update { it.copy(load = LoadStatus.Idle) }
            return@launch
        }
        _state.update { it.copy(load = LoadStatus.Idle, movie = movie, crewDirector = movie.director) }

        if (!tmdb.isConfigured) return@launch
        val candidates = tmdbSearchCandidates(movie)
        val match = candidates.firstNotNullOfOrNull { candidate ->
            tmdb.searchMovie(candidate, movie.date?.year)
        } ?: return@launch
        val backdrop = tmdb.backdropUrl(match.backdropPath)
        _state.update {
            it.copy(
                rating = match.voteAverage,
                voteCount = match.voteCount,
                backdropUrl = backdrop ?: it.backdropUrl
            )
        }
        val tmdbId = match.id ?: return@launch
        val credits = tmdb.fetchCredits(tmdbId) ?: return@launch
        val director = credits.crew
            .firstOrNull { it.job.equals("Director", ignoreCase = true) }
            ?.name
        val cast = credits.cast
            .sortedBy { it.order ?: Int.MAX_VALUE }
            .mapNotNull { it.name }
            .take(6)
        _state.update {
            it.copy(
                crewDirector = director ?: it.crewDirector,
                cast = cast
            )
        }
    }
}
