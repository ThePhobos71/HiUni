package de.transio.hiuni.feature.bib

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.LoadStatus
import de.transio.hiuni.core.sync.PrefetchOrchestrator
import de.transio.hiuni.feature.bib.data.BibConfig
import de.transio.hiuni.feature.bib.data.BibRepository
import de.transio.hiuni.feature.bib.data.BibUiData
import de.transio.hiuni.feature.bib.data.MyBooking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * State des Vollbild-Buchungsscreens. [selected] ist eine sortierte, lückenlose
 * Liste von 30-Min-Slot-Indizes (Index 0 = 8:00–8:30, … Index 23 = 19:30–20:00).
 */
data class BookingScreenState(
    val roomId: Int,
    val date: LocalDate,
    val selected: List<Int> = emptyList(),
    val confirmed: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null
)

data class BibUiState(
    val data: BibUiData = BibUiData(),
    val hasSession: Boolean = false,
    val cancelInProgress: Boolean = false,
    /**
     * Vereinheitlichter Lade-/Fehler-Status (siehe [LoadStatus]). Bib nutzt hier
     * nur `isRefreshing` (Pull-to-Refresh) — der Erst-Load-/Fehler-Zustand des
     * Snapshots lebt im Data-Layer [BibUiData] (`loading`/`lastError`). Der
     * Accessor unten hält `state.isRefreshing` im Screen unverändert lesbar.
     */
    val load: LoadStatus = LoadStatus.Idle,
    val booking: BookingScreenState? = null,
    val snackbar: String? = null,
    val selectedDate: LocalDate? = null
) {
    val isRefreshing: Boolean get() = load.isRefreshing
}

@HiltViewModel
class BibViewModel @Inject constructor(
    private val repository: BibRepository,
    private val casSession: CasSession
) : ViewModel() {

    private val _cancelInProgress = MutableStateFlow(false)
    private val _snackbar = MutableStateFlow<String?>(null)
    private val _booking = MutableStateFlow<BookingScreenState?>(null)
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    // Eigenes Flag für Pull-to-Refresh — synchron in viewModelScope getoggelt,
    // damit der Indikator nicht "kurz blitzt" wie das transitionierende
    // repository.state.loading. Pattern wie in MensaViewModel.
    private val _isRefreshing = MutableStateFlow(false)

    val state: StateFlow<BibUiState> = combine(
        repository.state,
        casSession.state,
        _cancelInProgress,
        _booking,
        _snackbar,
        _selectedDate,
        _isRefreshing
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val data = values[0] as BibUiData
        val explicit = values[5] as LocalDate?
        BibUiState(
            data = data,
            hasSession = (values[1] as CasState) is CasState.Authenticated,
            cancelInProgress = values[2] as Boolean,
            booking = values[3] as BookingScreenState?,
            snackbar = values[4] as String?,
            selectedDate = explicit ?: defaultSelectedDate(data),
            load = LoadStatus(isRefreshing = values[6] as Boolean)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), BibUiState())

    private fun defaultSelectedDate(data: BibUiData): LocalDate? {
        val snap = data.snapshot ?: return null
        val today = snap.today
        val todayHasOpen = snap.roomsToday.any { it.openCount > 0 }
        if (todayHasOpen) return today
        return snap.availableDates().firstOrNull { date ->
            BibConfig.ROOM_IDS.any { roomId ->
                snap.forRoomDay(date, roomId)?.openCount?.let { it > 0 } == true
            }
        } ?: today
    }

    fun selectDate(date: LocalDate) { _selectedDate.value = date }

    init {
        viewModelScope.launch {
            // Cache zuerst — zeigt sofort den letzten bekannten Stand, damit
            // beim Cold-Start kein leerer Screen blinkt. Danach nur nachladen,
            // wenn der Cache-Snapshot älter als die Bib-TTL ist (der Warmup hat
            // ihn ggf. schon frisch gezogen). Pull-to-Refresh bleibt forciert.
            repository.warmUpFromCache()
            repository.refreshIfStale(PrefetchOrchestrator.TTL_BIB_MS)
        }
    }

    fun refresh() = viewModelScope.launch {
        _isRefreshing.value = true
        try {
            repository.refresh()
        } finally {
            _isRefreshing.value = false
        }
    }

    fun cancel(booking: MyBooking) = viewModelScope.launch {
        _cancelInProgress.update { true }
        // Spinner sichtbar machen, weil repository.cancel intern refresh() ruft.
        // Sonst storniert man, Snackbar kommt, Snapshot aktualisiert sich
        // unsichtbar im Hintergrund — wirkt als hätte sich nichts getan.
        _isRefreshing.value = true
        try {
            when (val res = repository.cancel(booking)) {
                is AppResult.Success -> _snackbar.update { "Buchung storniert" }
                is AppResult.Failure -> _snackbar.update {
                    res.error.message ?: "Stornieren fehlgeschlagen"
                }
            }
        } finally {
            _isRefreshing.value = false
            _cancelInProgress.update { false }
        }
    }

    fun consumeSnackbar() { _snackbar.update { null } }

    // ── Booking-Screen ──────────────────────────────────────────────────

    fun openBookingScreen(roomId: Int, date: LocalDate? = null) {
        if (casSession.state.value !is CasState.Authenticated) {
            _snackbar.value = "Zum Buchen bitte mit Uni-Login anmelden."
            return
        }
        val resolvedDate = date
            ?: _selectedDate.value
            ?: repository.state.value.snapshot?.today
            ?: return
        _booking.value = BookingScreenState(roomId = roomId, date = resolvedDate)
    }

    fun closeBookingScreen() { _booking.value = null }

    /**
     * Raumwechsel innerhalb der offenen Buchungs-Sicht (Tap auf den Floorplan).
     * Behält das gewählte Datum bei und setzt eine halb getroffene Slot-Auswahl
     * zurück — ein angefangener Slot galt für den alten Raum. Die Belegung des
     * neuen Raums kommt aus dem bereits geladenen Snapshot (forRoomDay), es ist
     * kein separater Fetch nötig. No-op ohne aktive Buchung oder bei gleichem Raum.
     */
    fun switchRoom(roomId: Int) {
        val current = _booking.value ?: return
        if (current.roomId == roomId) return
        // Datum halten, Auswahl/Fehler zurücksetzen — frischer State für den
        // neuen Raum, damit keine Slot-Reste vom alten Raum übrig bleiben.
        _booking.value = BookingScreenState(roomId = roomId, date = current.date)
    }

    /**
     * Toggle eines 30-Min-Slots in der Vorauswahl. Auswahl bleibt immer
     * lückenlos: nur Slots, die an min/max angrenzen, dürfen hinzukommen.
     * Klick auf einen bereits gewählten Slot kürzt die Auswahl vom Ende.
     */
    fun toggleSlot(idx: Int, isBlocked: (Int) -> Boolean) {
        if (isBlocked(idx)) return
        val current = _booking.value ?: return
        val selected = current.selected
        val next: List<Int> = if (idx in selected) {
            // Slot ist schon gewählt → vom Ende her kürzen.
            val sorted = selected.sorted()
            val keep = mutableListOf(sorted.first())
            for (i in 1 until sorted.size) {
                val prev = keep.last()
                val candidate = sorted[i]
                if (candidate == idx) break
                if (candidate == prev + 1) keep += candidate else break
            }
            // Wenn der Klick den Start trifft: ganz zurücksetzen.
            if (idx == sorted.first()) emptyList() else keep
        } else if (selected.isEmpty()) {
            listOf(idx)
        } else {
            val sorted = selected.sorted()
            val lo = sorted.first()
            val hi = sorted.last()
            if (idx == lo - 1 || idx == hi + 1) {
                val merged = (sorted + idx).sorted()
                val newLo = merged.first()
                val newHi = merged.last()
                val rangeOk = (newLo..newHi).none { isBlocked(it) }
                if (rangeOk) merged else current.selected
            } else {
                listOf(idx)
            }
        }
        _booking.update { it?.copy(selected = next, error = null) }
    }

    fun confirmBookingScreen(slotIdxToStartHHMM: (Int) -> Pair<Int, Int>) {
        val current = _booking.value ?: return
        if (current.selected.isEmpty() || current.submitting || current.confirmed) return
        val sorted = current.selected.sorted()
        val startSlot = sorted.first()
        val lastSlot = sorted.last()
        val (sh, sm) = slotIdxToStartHHMM(startSlot)
        val (eh, em) = slotIdxToStartHHMM(lastSlot + 1)
        val start = LocalTime.of(sh, sm)
        val end = LocalTime.of(eh, em)
        _booking.update { it?.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val res = repository.book(current.date, start, end, current.roomId)) {
                is AppResult.Success -> _booking.update {
                    it?.copy(submitting = false, confirmed = true)
                }
                is AppResult.Failure -> {
                    val msg = res.error.message ?: "Buchung fehlgeschlagen"
                    _booking.update { it?.copy(submitting = false, error = msg) }
                    _snackbar.value = msg
                }
            }
        }
    }

    fun acknowledgeBookingDone() {
        _booking.value = null
        _snackbar.update { "Buchung bestätigt" }
    }
}
