package de.transio.hiuni.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.mensa.data.MensaHours
import de.transio.hiuni.feature.mensa.data.MensaRepository
import de.transio.hiuni.feature.movies.data.MoviesRepository
import de.transio.hiuni.feature.settings.data.locationById
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    calendarRepository: CalendarRepository,
    mensaRepository: MensaRepository,
    moviesRepository: MoviesRepository,
    settings: SettingsDataStore
) : ViewModel() {

    private val nextEventFlow = calendarRepository
        .observeRange(Instant.now(), Instant.now().plusSeconds(60L * 60 * 24 * 14))

    private val todaysMealsFlow = mensaRepository.observeForDate(LocalDate.now())
    private val upcomingMoviesFlow = moviesRepository.observeUpcoming()

    val state: StateFlow<HomeUiState> = combine(
        nextEventFlow,
        todaysMealsFlow,
        settings.mensaLocationId,
        upcomingMoviesFlow
    ) { upcomingEvents, meals, locationId, movies ->
        val now = Instant.now()
        val nextEvent = upcomingEvents.firstOrNull { it.startTime.isAfter(now) }
        HomeUiState(
            today = LocalDate.now(),
            greetingName = "Studi",
            nextEvent = nextEvent,
            todaysMeals = meals,
            mensaLocation = locationById(locationId),
            isMensaOpen = MensaHours.isOpenNow(),
            upcomingMovies = movies.take(5)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
