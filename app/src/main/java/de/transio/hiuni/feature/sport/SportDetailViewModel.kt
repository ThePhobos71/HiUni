package de.transio.hiuni.feature.sport

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.sport.data.SportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * ViewModel hinter dem Sport-Detail-Screen. Beobachtet einen einzelnen
 * `SportEventEntity` via stabiler `supersaasSlotId` und spiegelt den Pin-Status
 * im Kalender.
 *
 * Pin-Status wird in einem eigenen Flow geführt, weil Room's Custom-Event-Tabelle
 * keinen flowfähigen Single-Source-Lookup via `(sourceKind, sourceReference)`
 * hat. Statt eine neue DAO-Methode dafür einzuziehen, ticken wir ihn nach jedem
 * Pin/Unpin manuell — das reicht für diesen Screen.
 */
@HiltViewModel
class SportDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SportRepository,
    private val calendarRepository: CalendarRepository,
    private val scheduler: NotificationScheduler,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val slotId: Long = savedStateHandle["slotId"] ?: -1L

    private val _isCalendarPinned = MutableStateFlow(false)
    private val _pinMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<SportDetailUiState> = combine(
        repository.observeBySlotId(slotId),
        _isCalendarPinned,
        _pinMessage
    ) { event, pinned, message ->
        SportDetailUiState(event = event, isCalendarPinned = pinned, pinMessage = message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), SportDetailUiState())

    init {
        refreshPinState()
    }

    fun pinToCalendar() = viewModelScope.launch {
        val event = state.value.event ?: return@launch
        val reminderMinutes = settings.notificationMinutesBefore.first()
        // Beschreibung kann lang sein — Kalender-Liste leidet sonst. 300 chars reichen für eine Preview.
        val shortDescription = event.description
            ?.replace("\r\n", "\n")
            ?.replace("\r", "\n")
            ?.trim()
            ?.take(300)
            ?.takeIf { it.isNotBlank() }
        val ref = sourceRef(event.supersaasSlotId)
        // Existierender Pin → ID übernehmen, damit `upsert` ein Update statt Duplikat macht.
        val existing = calendarRepository.findBySourceReference(CustomEventEntity.SOURCE_SPORT_PIN, ref)
        val candidate = CustomEventEntity(
            id = existing?.id ?: 0L,
            title = event.title.ifBlank { "Hochschulsport" },
            description = shortDescription,
            location = event.location?.takeIf { it.isNotBlank() },
            startTime = event.startTime,
            endTime = event.endTime,
            sourceKind = CustomEventEntity.SOURCE_SPORT_PIN,
            sourceReference = ref,
            reminderMinutesBefore = reminderMinutes
        )
        val finalId = calendarRepository.upsert(candidate)
        scheduler.cancel(finalId)
        val triggerAt = event.startTime.minus(Duration.ofMinutes(reminderMinutes.toLong()))
        if (triggerAt.isAfter(Instant.now())) {
            scheduler.schedule(finalId, candidate.title, triggerAt)
        }
        _isCalendarPinned.value = true
        _pinMessage.value = "In Kalender übernommen"
    }

    fun unpinFromCalendar() = viewModelScope.launch {
        val event = state.value.event ?: return@launch
        val ref = sourceRef(event.supersaasSlotId)
        val existing = calendarRepository.findBySourceReference(CustomEventEntity.SOURCE_SPORT_PIN, ref)
            ?: run {
                _isCalendarPinned.value = false
                return@launch
            }
        scheduler.cancel(existing.id)
        calendarRepository.delete(existing)
        _isCalendarPinned.value = false
        _pinMessage.value = "Aus Kalender entfernt"
    }

    fun consumeMessage() {
        _pinMessage.value = null
    }

    private fun refreshPinState() = viewModelScope.launch {
        if (slotId <= 0L) return@launch
        val pinned = calendarRepository
            .findBySourceReference(CustomEventEntity.SOURCE_SPORT_PIN, sourceRef(slotId)) != null
        _isCalendarPinned.value = pinned
    }

    private fun sourceRef(slotId: Long): String = "sport:$slotId"
}
