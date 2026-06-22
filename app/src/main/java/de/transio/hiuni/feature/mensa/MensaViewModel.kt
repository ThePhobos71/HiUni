package de.transio.hiuni.feature.mensa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.mensa.data.Announcement
import de.transio.hiuni.feature.mensa.data.AnnouncementTime
import de.transio.hiuni.feature.mensa.data.MealEntity
import de.transio.hiuni.feature.mensa.data.MensaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class MensaViewModel @Inject constructor(
    private val repository: MensaRepository,
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _selectedMealtime = MutableStateFlow(Mealtime.autoSelect())
    private val _activeCategory = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val mealsFlow = _selectedDate.flatMapLatest { repository.observeForDate(it) }
    private val availableDates = repository.observeAvailableDates(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val announcementsFlow = _selectedDate.flatMapLatest { repository.observeAnnouncements(it) }

    val state: StateFlow<MensaUiState> = combine(
        combine(_selectedDate, _selectedMealtime) { d, m -> d to m },
        availableDates,
        combine(mealsFlow, announcementsFlow) { m, a -> m to a },
        _activeCategory,
        combine(_isRefreshing, _errorMessage) { r, e -> r to e }
    ) { dateMealtime, dates, mealsAndAnnouncements, category, refreshingAndError ->
        val (date, mealtime) = dateMealtime
        val (meals, announcements) = mealsAndAnnouncements
        val (isRefreshing, errorMessage) = refreshingAndError
        val filtered = meals.filter { matchesMealtime(it, mealtime) }
            .map { it.copy(category = stripMealtimePrefix(it.category)) }
        MensaUiState(
            selectedDate = date,
            selectedMealtime = mealtime,
            availableDates = dates,
            mealsByCategory = filtered.groupBy { it.category }.toSortedMap(categoryOrder()),
            activeCategory = category,
            announcements = announcements.filter { matchesMealtimeAnnouncement(it, mealtime) },
            isRefreshing = isRefreshing,
            errorMessage = errorMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MensaUiState())

    init {
        refresh(force = false)
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.update { date }
    }

    fun selectMealtime(mealtime: Mealtime) {
        _selectedMealtime.update { mealtime }
        _activeCategory.update { null }
    }

    fun toggleCategory(category: String?) {
        _activeCategory.update { current -> if (current == category) null else category }
    }

    fun refresh(force: Boolean = true) = viewModelScope.launch {
        _isRefreshing.value = true
        _errorMessage.value = null
        when (val result = repository.refresh(force = force)) {
            is AppResult.Success -> Unit
            is AppResult.Failure -> _errorMessage.value =
                result.error.message ?: "STW-ON-API nicht erreichbar"
        }
        _isRefreshing.value = false
    }

    fun pinToCalendar(meal: MealEntity) = viewModelScope.launch {
        val zone = ZoneId.systemDefault()
        val mealtime = if (meal.category.contains("Abend", ignoreCase = true) ||
            _selectedMealtime.value == Mealtime.ABEND) Mealtime.ABEND else Mealtime.MITTAG
        val start = LocalTime.of(if (mealtime == Mealtime.MITTAG) 12 else 18, 0)
        val end = start.plusHours(1)
        calendarRepository.upsert(
            CustomEventEntity(
                title = meal.name,
                description = meal.description,
                location = "Mensa",
                startTime = meal.date.atTime(start).atZone(zone).toInstant(),
                endTime = meal.date.atTime(end).atZone(zone).toInstant(),
                sourceKind = CustomEventEntity.SOURCE_MENSA_PIN,
                sourceReference = "${meal.locationId}/${meal.sourceId}",
                reminderMinutesBefore = null
            )
        )
    }

    private fun matchesMealtime(meal: MealEntity, mealtime: Mealtime): Boolean {
        val isEvening = meal.category.startsWith("Abend", ignoreCase = true)
        return when (mealtime) {
            Mealtime.MITTAG -> !isEvening && !meal.category.startsWith("Frühstück", ignoreCase = true)
            Mealtime.ABEND -> isEvening
        }
    }

    private fun matchesMealtimeAnnouncement(a: Announcement, mealtime: Mealtime): Boolean =
        when (mealtime) {
            Mealtime.MITTAG -> a.time == AnnouncementTime.NOON || a.time == AnnouncementTime.MORNING
            Mealtime.ABEND -> a.time == AnnouncementTime.EVENING
        }

    private fun stripMealtimePrefix(category: String): String =
        category.removePrefix("Abend · ").removePrefix("Frühstück · ").ifBlank { "Hauptgericht" }

    private fun categoryOrder(): Comparator<String> = Comparator { a, b ->
        val priority = listOf("Hauptgericht", "Essen", "Vegetarisch", "Beilage", "Suppe", "Salat", "Dessert", "Sonstiges")
        val ai = priority.indexOfFirst { a.startsWith(it, ignoreCase = true) }.let { if (it < 0) priority.size else it }
        val bi = priority.indexOfFirst { b.startsWith(it, ignoreCase = true) }.let { if (it < 0) priority.size else it }
        if (ai != bi) ai.compareTo(bi) else a.compareTo(b)
    }
}
