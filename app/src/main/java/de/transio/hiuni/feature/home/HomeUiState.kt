package de.transio.hiuni.feature.home

import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.mensa.data.MealEntity
import de.transio.hiuni.feature.movies.data.MovieEntity
import de.transio.hiuni.feature.settings.data.MensaLocation
import java.time.LocalDate

data class HomeUiState(
    val today: LocalDate = LocalDate.now(),
    val greetingName: String = "",
    val nextEvent: CustomEventEntity? = null,
    val todaysMeals: List<MealEntity> = emptyList(),
    val mensaLocation: MensaLocation? = null,
    val isMensaOpen: Boolean = false,
    val upcomingMovies: List<MovieEntity> = emptyList()
)
