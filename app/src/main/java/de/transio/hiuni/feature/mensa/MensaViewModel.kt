package de.transio.hiuni.feature.mensa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.network.ConnectivityObserver
import de.transio.hiuni.core.network.OfflineMessages
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
import javax.inject.Inject

@HiltViewModel
class MensaViewModel @Inject constructor(
    private val repository: MensaRepository,
    private val connectivity: ConnectivityObserver,
    settings: SettingsDataStore
) : ViewModel() {

    private val locationIdFlow = settings.mensaLocationId
    private val lastRefreshEpochFlow = settings.lastMensaRefreshEpoch

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _selectedMealtime = MutableStateFlow(Mealtime.autoSelect())
    private val _activeDietFilter = MutableStateFlow<DietFilter?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val _isSearchOpen = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _mealDetail = MutableStateFlow<MealEntity?>(null)

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

    private data class LoadState(
        val isRefreshing: Boolean,
        val isLoading: Boolean,
        val errorMessage: String?,
        val search: SearchState,
        val isOnline: Boolean,
        val lastRefreshEpoch: Long
    )

    private val connectivityStateFlow = combine(
        connectivity.isOnline,
        lastRefreshEpochFlow
    ) { online, epoch -> online to epoch }

    private val loadStateFlow = combine(
        _isRefreshing,
        _isLoading,
        _errorMessage,
        searchStateFlow,
        connectivityStateFlow
    ) { refreshing, loading, error, search, conn ->
        LoadState(refreshing, loading, error, search, conn.first, conn.second)
    }

    val state: StateFlow<MensaUiState> = combine(
        combine(_selectedDate, _selectedMealtime) { d, m -> d to m },
        availableDates,
        combine(mealsFlow, announcementsFlow) { m, a -> m to a },
        combine(_activeDietFilter, locationIdFlow, _mealDetail) { d, l, det -> Triple(d, l, det) },
        loadStateFlow
    ) { dateMealtime, dates, mealsAndAnnouncements, dietLocationDetail, load ->
        val (date, mealtime) = dateMealtime
        val (meals, announcements) = mealsAndAnnouncements
        val (dietFilter, locationId, detail) = dietLocationDetail
        val search = load.search
        val filtered = meals.filter { matchesMealtime(it, mealtime) }
            .map { it.copy(category = stripMealtimePrefix(it.category)) }
        MensaUiState(
            selectedDate = date,
            selectedMealtime = mealtime,
            availableDates = dates,
            mealsByCategory = filtered.groupBy { it.category }.toSortedMap(categoryOrder()),
            activeDietFilter = dietFilter,
            announcements = announcements.filter { matchesMealtimeAnnouncement(it, mealtime) },
            isRefreshing = load.isRefreshing,
            isLoading = load.isLoading,
            errorMessage = load.errorMessage,
            isSearchOpen = search.isOpen,
            searchQuery = search.query,
            searchResults = search.results,
            mensaLocationId = locationId,
            mealDetail = detail,
            isOnline = load.isOnline,
            lastRefreshEpoch = load.lastRefreshEpoch
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), MensaUiState())

    /** Erst-Load läuft genau einmal (init) mit Skeleton-Anzeige. */
    private var isInitialLoad = true

    init {
        refresh(force = false)
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.update { date }
    }

    fun selectMealtime(mealtime: Mealtime) {
        _selectedMealtime.update { mealtime }
        _activeDietFilter.update { null }
    }

    fun openMealDetail(meal: MealEntity) {
        _mealDetail.value = meal
    }

    fun closeMealDetail() {
        _mealDetail.value = null
    }

    fun toggleDietFilter(filter: DietFilter?) {
        _activeDietFilter.update { current -> if (current == filter) null else filter }
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
        _activeDietFilter.value = null
        closeSearch()
    }

    fun refresh(force: Boolean = true) = viewModelScope.launch {
        // Erster Load zeigt Skeleton (isLoading), spätere Refreshes den
        // Pull-to-Refresh-Spinner (isRefreshing).
        val initial = isInitialLoad
        isInitialLoad = false
        if (initial) _isLoading.value = true else _isRefreshing.value = true
        _errorMessage.value = null
        when (val result = repository.refresh(force = force)) {
            is AppResult.Success -> Unit
            is AppResult.Failure -> _errorMessage.value =
                // Offline → sprechende Meldung statt technischem API-Fehler; der Cache bleibt sichtbar.
                if (!connectivity.isOnline.value) OfflineMessages.NO_CONNECTION
                else result.error.message ?: "STW-ON-API nicht erreichbar"
        }
        _isLoading.value = false
        _isRefreshing.value = false
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
