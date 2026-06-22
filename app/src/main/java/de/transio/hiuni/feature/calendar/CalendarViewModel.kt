package de.transio.hiuni.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
    private val scheduler: NotificationScheduler,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val _viewMode = MutableStateFlow(CalendarViewMode.DAY)
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _editing = MutableStateFlow<CustomEventEntity?>(null)
    private val _isAddSheetOpen = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventsFlow = _viewMode
        .combine(_selectedDate) { mode, date -> mode to date }
        .flatMapLatest { (mode, date) ->
            val (from, to) = rangeFor(mode, date)
            repository.observeRange(from, to)
        }

    val state: StateFlow<CalendarUiState> = combine(
        _viewMode,
        _selectedDate,
        eventsFlow,
        _editing,
        _isAddSheetOpen
    ) { mode, date, events, editing, isAddSheetOpen ->
        CalendarUiState(
            viewMode = mode,
            selectedDate = date,
            events = events,
            isLoading = false,
            editing = editing,
            isAddSheetOpen = isAddSheetOpen
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
        _isAddSheetOpen.value = true
    }

    fun openEdit(event: CustomEventEntity) {
        _editing.value = event
        _isAddSheetOpen.value = true
    }

    fun closeAddOrEdit() {
        _isAddSheetOpen.value = false
        _editing.value = null
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
