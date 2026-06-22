package de.transio.hiuni.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.mensa.data.MensaRepository
import de.transio.hiuni.feature.movies.data.MoviesRepository
import de.transio.hiuni.feature.settings.data.locationById
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
            isMensaOpen = isMensaOpenNow(),
            upcomingMovies = movies.take(5)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun isMensaOpenNow(): Boolean {
        val today = LocalDate.now()
        if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) return false
        val now = LocalTime.now()
        val lunch = LocalTime.of(11, 30) to LocalTime.of(14, 30)
        val dinner = LocalTime.of(17, 30) to LocalTime.of(20, 0)
        return (now.isAfter(lunch.first) && now.isBefore(lunch.second)) ||
            (now.isAfter(dinner.first) && now.isBefore(dinner.second))
    }
}
