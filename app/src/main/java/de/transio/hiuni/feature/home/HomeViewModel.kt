package de.transio.hiuni.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.UserProfile
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
    casSession: CasSession,
    settings: SettingsDataStore
) : ViewModel() {

    private val nextEventFlow = calendarRepository
        .observeRange(Instant.now(), Instant.now().plusSeconds(60L * 60 * 24 * 14))

    private val todaysMealsFlow = mensaRepository.observeForDate(LocalDate.now())
    private val upcomingMoviesFlow = moviesRepository.observeUpcoming()

    private val greetingNameFlow = combine(
        casSession.profile,
        settings.displayNameMode,
        settings.customDisplayName
    ) { profile, mode, custom -> computeGreetingName(profile, mode, custom) }

    val state: StateFlow<HomeUiState> = combine(
        combine(nextEventFlow, todaysMealsFlow) { e, m -> e to m },
        combine(settings.mensaLocationId, upcomingMoviesFlow) { id, movies -> id to movies },
        greetingNameFlow
    ) { eventsAndMeals, locationAndMovies, greetingName ->
        val (upcomingEvents, meals) = eventsAndMeals
        val (locationId, movies) = locationAndMovies
        val now = Instant.now()
        val nextEvent = upcomingEvents.firstOrNull { it.startTime.isAfter(now) }
        HomeUiState(
            today = LocalDate.now(),
            greetingName = greetingName,
            nextEvent = nextEvent,
            todaysMeals = meals,
            mensaLocation = locationById(locationId),
            isMensaOpen = MensaHours.isOpenNow(),
            upcomingMovies = movies.take(5)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun computeGreetingName(
        profile: UserProfile,
        mode: String,
        custom: String
    ): String {
        return when (mode) {
            SettingsDataStore.DISPLAY_NAME_MODE_CUSTOM ->
                custom.trim().takeIf { it.isNotBlank() } ?: defaultName(profile)
            SettingsDataStore.DISPLAY_NAME_MODE_ALL ->
                profile.vorname?.takeIf { it.isNotBlank() } ?: defaultName(profile)
            else /* FIRST */ ->
                profile.firstName ?: defaultName(profile)
        }
    }

    private fun defaultName(profile: UserProfile): String =
        profile.uid?.takeIf { it.isNotBlank() } ?: "Studi"
}
