package de.transio.hiuni.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.courses.data.CourseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val courseRepository: CourseRepository,
    private val scheduler: NotificationScheduler,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val _viewMode = MutableStateFlow(CalendarViewMode.DAY)
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _editing = MutableStateFlow<CustomEventEntity?>(null)
    private val _isAddSheetOpen = MutableStateFlow(false)
    private val _initialDateForAdd = MutableStateFlow<LocalDate?>(null)

    private val _isSearchOpen = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventsFlow = _viewMode
        .combine(_selectedDate) { mode, date -> mode to date }
        .flatMapLatest { (mode, date) ->
            val (from, to) = rangeFor(mode, date)
            repository.observeRange(from, to)
        }

    // Modulkürzel-Map für LSF-Veranstaltungen, damit der Kalender knackige Labels
    // ("IT-EINF1") statt langer Titel ("3204 Einführung in die Informatik") anzeigen kann.
    private val coursesFlow = courseRepository.observeAll()
    private val courseShortNamesFlow = coursesFlow
        .map { courses ->
            courses
                .filter { it.source == CourseEntity.SOURCE_LSF && it.lsfId != null }
                .associate { course ->
                    val short = course.moduleAbbreviation?.takeIf { it.isNotBlank() }
                        ?: course.name
                    course.lsfId!! to short
                }
        }

    // Weiter Such-Korpus: ~4M zurück + ~6M voraus. Bewusst NICHT an eventsFlow gekoppelt,
    // sonst werden Treffer beim View-Mode-Wechsel zerfetzt. Ein Singleton-Range reicht.
    private val searchCorpusFlow = run {
        val now = Instant.now()
        val from = now.minus(Duration.ofDays(120))
        val to = now.plus(Duration.ofDays(180))
        repository.observeRange(from, to)
    }

    private val searchResultsFlow = combine(
        searchCorpusFlow,
        coursesFlow,
        _searchQuery
    ) { events, courses, query ->
        if (query.isBlank()) return@combine emptyList()
        val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return@combine emptyList()
        // Pro Course ein Haystack (Name + Prof + Modulkürzel), gemappt über lsfId.
        val courseHaystack: Map<String, String> = courses
            .filter { it.lsfId != null }
            .associate { course ->
                course.lsfId!! to buildString {
                    append(course.name)
                    append(' ')
                    append(course.professor)
                    course.moduleAbbreviation?.let { append(' ').append(it) }
                }.lowercase()
            }
        val nowEpoch = Instant.now().toEpochMilli()
        events.asSequence()
            .filter { event ->
                val courseHay = event.courseLsfId?.let { courseHaystack[it] }.orEmpty()
                val hay = buildString {
                    append(event.title.lowercase())
                    append(' ')
                    event.location?.let { append(it.lowercase()).append(' ') }
                    event.description?.let { append(it.lowercase()).append(' ') }
                    append(courseHay)
                }
                tokens.all { it in hay }
            }
            // Treffer ab heute (Zukunft) zuerst aufsteigend; danach Vergangenheit
            // absteigend (jüngste Vergangenheit oben). So sieht der User Bevorstehendes
            // zuerst, ohne dass weit zurückliegende Treffer die Liste füllen.
            .sortedWith(
                compareBy(
                    { if (it.startTime.toEpochMilli() >= nowEpoch) 0 else 1 },
                    {
                        val millis = it.startTime.toEpochMilli()
                        if (millis >= nowEpoch) millis else -millis
                    }
                )
            )
            .take(40)
            .toList()
    }

    private data class SearchState(
        val isOpen: Boolean,
        val query: String,
        val results: List<CustomEventEntity>
    )

    private val searchStateFlow = combine(
        _isSearchOpen,
        _searchQuery,
        searchResultsFlow
    ) { open, query, results -> SearchState(open, query, results) }

    val state: StateFlow<CalendarUiState> = combine(
        combine(_viewMode, _selectedDate, eventsFlow) { mode, date, events -> Triple(mode, date, events) },
        combine(_editing, _isAddSheetOpen, _initialDateForAdd) { editing, open, date -> Triple(editing, open, date) },
        courseShortNamesFlow,
        searchStateFlow
    ) { (mode, date, events), (editing, isAddSheetOpen, initialDate), shortNames, search ->
        CalendarUiState(
            viewMode = mode,
            selectedDate = date,
            events = events,
            isLoading = false,
            editing = editing,
            isAddSheetOpen = isAddSheetOpen,
            initialDateForAdd = initialDate,
            courseShortNameByLsfId = shortNames,
            isSearchOpen = search.isOpen,
            searchQuery = search.query,
            searchResults = search.results
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState()
    )

    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    fun selectViewMode(mode: CalendarViewMode) { _viewMode.update { mode } }
    fun selectDate(date: LocalDate) { _selectedDate.update { date } }

    fun openAdd() {
        _editing.value = null
        _initialDateForAdd.value = null
        _isAddSheetOpen.value = true
    }

    fun openAddOnDate(date: LocalDate) {
        _editing.value = null
        _initialDateForAdd.value = date
        _isAddSheetOpen.value = true
    }

    fun openEdit(event: CustomEventEntity) {
        _editing.value = event
        _initialDateForAdd.value = null
        _isAddSheetOpen.value = true
    }

    fun closeAddOrEdit() {
        _isAddSheetOpen.value = false
        _editing.value = null
        _initialDateForAdd.value = null
    }

    fun save(
        existingId: Long,
        title: String,
        description: String?,
        location: String?,
        start: Instant,
        end: Instant,
        reminderMinutesBefore: Int?
    ) = viewModelScope.launch {
        val candidate = CustomEventEntity(
            id = existingId,
            title = title.ifBlank { "Ohne Titel" },
            description = description?.takeIf { it.isNotBlank() },
            location = location?.takeIf { it.isNotBlank() },
            startTime = start,
            endTime = end,
            sourceKind = CustomEventEntity.SOURCE_USER,
            reminderMinutesBefore = reminderMinutesBefore
        )
        val finalId = repository.upsert(candidate)
        val persisted = candidate.copy(id = finalId)
        scheduleReminder(persisted)
        closeAddOrEdit()
    }

    fun delete(event: CustomEventEntity) = viewModelScope.launch {
        scheduler.cancel(event.id)
        repository.delete(event)
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

    fun selectSearchResult(event: CustomEventEntity) {
        val date = event.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
        _selectedDate.value = date
        _viewMode.value = CalendarViewMode.DAY
        closeSearch()
    }

    fun toggleViewMode() {
        _viewMode.update {
            when (it) {
                CalendarViewMode.DAY -> CalendarViewMode.WEEK
                CalendarViewMode.WEEK -> CalendarViewMode.MONTH
                CalendarViewMode.MONTH -> CalendarViewMode.DAY
            }
        }
    }

    suspend fun defaultReminderMinutes(): Int = settings.notificationMinutesBefore.first()

    private suspend fun scheduleReminder(event: CustomEventEntity) {
        scheduler.cancel(event.id)
        val minutes = event.reminderMinutesBefore ?: return
        val triggerAt = event.startTime.minus(Duration.ofMinutes(minutes.toLong()))
        if (triggerAt.isAfter(Instant.now())) {
            scheduler.schedule(event.id, event.title, triggerAt)
        }
    }

    private fun rangeFor(mode: CalendarViewMode, date: LocalDate): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        return when (mode) {
            CalendarViewMode.DAY -> {
                // Lade die ganze Woche, damit der Day-Picker (Mo–Fr) Dots/Inhalte sofort
                // sehen kann, wenn der User zwischen Tagen wechselt.
                val weekStart = date.with(java.time.DayOfWeek.MONDAY)
                val from = weekStart.atStartOfDay(zone).toInstant()
                val to = weekStart.plusDays(7).atStartOfDay(zone).toInstant()
                from to to
            }
            CalendarViewMode.WEEK -> {
                val weekStart = date.with(java.time.DayOfWeek.MONDAY)
                val from = weekStart.atStartOfDay(zone).toInstant()
                val to = weekStart.plusDays(7).atStartOfDay(zone).toInstant()
                from to to
            }
            CalendarViewMode.MONTH -> {
                // 6-Wochen-Grid abdecken: vom Montag der Woche, in der der Monat startet,
                // bis 42 Tage später. Reicht für jede Monatsgröße.
                val monthStart = date.withDayOfMonth(1)
                val gridStart = monthStart.with(java.time.DayOfWeek.MONDAY)
                val from = gridStart.atStartOfDay(zone).toInstant()
                val to = gridStart.plusDays(42).atStartOfDay(zone).toInstant()
                from to to
            }
        }
    }
}
