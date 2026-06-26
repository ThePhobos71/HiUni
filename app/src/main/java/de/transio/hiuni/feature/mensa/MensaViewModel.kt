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

    private val _isSearchOpen = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val mealsFlow = _selectedDate.flatMapLatest { repository.observeForDate(it) }
    private val availableDates = repository.observeAvailableDates(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val announcementsFlow = _selectedDate.flatMapLatest { repository.observeAnnouncements(it) }

    // Suche zieht aus dem 14-Tage-Korpus der aktuellen Mensa-Location. Bewusst NICHT
    // an _selectedDate gekoppelt: Treffer dürfen über alle Tage gehen.
    private val searchCorpusFlow = repository.observeSearchWindow(daysAhead = 13)

    private val searchResultsFlow = combine(
        searchCorpusFlow,
        _searchQuery
    ) { meals, query ->
        if (query.isBlank()) return@combine emptyList()
        val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return@combine emptyList()
        val today = LocalDate.now()
        meals.asSequence()
            .filter { meal ->
                val hay = buildString {
                    append(meal.name.lowercase())
                    append(' ')
                    meal.description?.let { append(it.lowercase()).append(' ') }
                    append(meal.category.lowercase())
                }
                tokens.all { it in hay }
            }
            .filter { !it.date.isBefore(today) }
            .sortedBy { it.date }
            .take(40)
            .toList()
    }

    private data class SearchState(
        val isOpen: Boolean,
        val query: String,
        val results: List<MealEntity>
    )

    private val searchStateFlow = combine(
        _isSearchOpen,
        _searchQuery,
        searchResultsFlow
    ) { open, query, results -> SearchState(open, query, results) }

    val state: StateFlow<MensaUiState> = combine(
        combine(_selectedDate, _selectedMealtime) { d, m -> d to m },
        availableDates,
        combine(mealsFlow, announcementsFlow) { m, a -> m to a },
        _activeCategory,
        combine(_isRefreshing, _errorMessage, searchStateFlow) { r, e, s -> Triple(r, e, s) }
    ) { dateMealtime, dates, mealsAndAnnouncements, category, refreshingErrorSearch ->
        val (date, mealtime) = dateMealtime
        val (meals, announcements) = mealsAndAnnouncements
        val (isRefreshing, errorMessage, search) = refreshingErrorSearch
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
            errorMessage = errorMessage,
            isSearchOpen = search.isOpen,
            searchQuery = search.query,
            searchResults = search.results
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

    fun openSearch() {
        _isSearchOpen.value = true
    }

    fun closeSearch() {
        _isSearchOpen.value = false
        _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Tap auf einen Treffer springt zum jeweiligen Datum und schließt die Suche. Die
     * Mealtime wird auf den Slot des Treffers gesetzt (Abend → ABEND, sonst MITTAG),
     * damit der User die gefundene Mahlzeit auch tatsächlich sieht.
     */
    fun selectSearchResult(meal: MealEntity) {
        _selectedDate.value = meal.date
        _selectedMealtime.value = if (meal.category.startsWith("Abend", ignoreCase = true)) {
            Mealtime.ABEND
        } else {
            Mealtime.MITTAG
        }
        _activeCategory.value = null
        closeSearch()
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
