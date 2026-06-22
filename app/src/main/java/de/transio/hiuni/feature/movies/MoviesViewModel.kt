package de.transio.hiuni.feature.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.movies.data.MovieEntity
import de.transio.hiuni.feature.movies.data.MoviesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val repository: MoviesRepository,
    private val calendarRepository: CalendarRepository
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

    fun pinToCalendar(movie: MovieEntity) = viewModelScope.launch {
        val zone = ZoneId.systemDefault()
        val date = movie.date ?: return@launch
        val startTime = movie.time ?: LocalTime.of(20, 0)
        val durationMinutes = movie.durationMinutes ?: 120
        val end = startTime.plus(Duration.ofMinutes(durationMinutes.toLong()))
        calendarRepository.upsert(
            CustomEventEntity(
                title = "🎬 ${movie.title}",
                description = listOfNotNull(movie.subtitle, movie.genre, movie.description)
                    .joinToString(" · ")
                    .takeIf { it.isNotBlank() },
                location = movie.location ?: "Uni Kino",
                startTime = date.atTime(startTime).atZone(zone).toInstant(),
                endTime = date.atTime(end).atZone(zone).toInstant(),
                sourceKind = CustomEventEntity.SOURCE_MOVIE_PIN,
                sourceReference = "${movie.filmId}/${movie.sessionId}",
                reminderMinutesBefore = 30
            )
        )
    }
}
