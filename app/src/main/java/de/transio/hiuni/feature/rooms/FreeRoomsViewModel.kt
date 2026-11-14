package de.transio.hiuni.feature.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.feature.rooms.data.Campus
import de.transio.hiuni.feature.rooms.data.CampusSnapshot
import de.transio.hiuni.feature.rooms.data.FreeRoomCalculator
import de.transio.hiuni.feature.rooms.data.FreeRoomsRepository
import de.transio.hiuni.feature.rooms.data.FreeRoomsResult
import de.transio.hiuni.feature.rooms.data.FreeRoomsStore
import de.transio.hiuni.feature.rooms.data.LoadProgress
import de.transio.hiuni.feature.rooms.data.LsfRoom
import de.transio.hiuni.feature.rooms.data.RoomIcsParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject

data class FreeRoomsUiState(
    val today: LocalDate = LocalDate.MIN,
    /** Gewählter Campus, `null` bis zur ersten Wahl. */
    val campus: Campus? = null,
    /** Gewählte Uhrzeit in Minuten ab Mitternacht, `null` = "Jetzt". */
    val chosenMinute: Int? = null,
    /** Minute, für die [result] berechnet ist ("Jetzt" oder [chosenMinute]). */
    val atMinute: Int = 0,
    /** Wählbare spätere Uhrzeiten von heute (volle Stunden). */
    val timeOptions: List<Int> = emptyList(),
    val snapshot: CampusSnapshot? = null,
    val result: FreeRoomsResult = FreeRoomsResult(),
    /** Nicht `null`, solange geladen wird. */
    val progress: LoadProgress? = null,
    val error: String? = null
) {
    val isLoading: Boolean get() = progress != null
    val missing: List<LsfRoom> get() = snapshot?.missing.orEmpty()
}

/**
 * Raumfinder: Campus wählen, sofort die freien Räume sehen.
 *
 * Geladen wird nur auf Wunsch (Campus-Wahl, Neuladen, fehlende nachholen), nie im
 * Hintergrund. Ein heutiger Stand aus dem Zwischenspeicher wird ohne Abruf gezeigt.
 * Die Uhr läuft im Minutentakt mit, damit "Jetzt" aktuell bleibt; nach Mitternacht
 * wird der Stand vom Vortag verworfen.
 */
@HiltViewModel
class FreeRoomsViewModel @Inject constructor(
    private val repository: FreeRoomsRepository,
    private val store: FreeRoomsStore,
    private val clock: Clock
) : ViewModel() {

    private val _state = MutableStateFlow(FreeRoomsUiState())
    val state: StateFlow<FreeRoomsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        recompute()
        viewModelScope.launch {
            val remembered = runCatching { store.selectedCampus.first() }.getOrNull()
            if (remembered != null && _state.value.campus == null) showCampus(remembered, persist = false)
        }
        viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                recompute()
            }
        }
    }

    /** Campus-Pill: zeigt den heutigen Stand oder lädt ihn. */
    fun selectCampus(campus: Campus) {
        if (campus == _state.value.campus && (_state.value.snapshot != null || _state.value.isLoading)) return
        showCampus(campus, persist = true)
    }

    /** Uhrzeit-Pill: `null` = "Jetzt", sonst Minute ab Mitternacht. */
    fun selectTime(minute: Int?) {
        _state.update { it.copy(chosenMinute = minute) }
        recompute()
    }

    /** Manuelles Neuladen des gewählten Campus. */
    fun reload() {
        val campus = _state.value.campus ?: return
        startLoad { repository.load(campus, ::onProgress) }
    }

    /** Lädt nur die Räume nach, die beim letzten Mal gefehlt haben. */
    fun retryMissing() {
        val campus = _state.value.campus ?: return
        startLoad { repository.retryMissing(campus, ::onProgress) }
    }

    private fun showCampus(campus: Campus, persist: Boolean) {
        loadJob?.cancel()
        _state.update { it.copy(campus = campus, snapshot = null, progress = null, error = null) }
        recompute()
        loadJob = viewModelScope.launch {
            if (persist) runCatching { store.setCampus(campus) }
            val cached = runCatching { repository.cached(campus) }.getOrNull()
            if (cached != null) {
                _state.update { it.copy(snapshot = cached) }
                recompute()
            } else {
                runLoad { repository.load(campus, ::onProgress) }
            }
        }
    }

    private fun startLoad(block: suspend () -> AppResult<CampusSnapshot>) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { runLoad(block) }
    }

    private suspend fun runLoad(block: suspend () -> AppResult<CampusSnapshot>) {
        val campus = _state.value.campus
        _state.update { it.copy(progress = LoadProgress(0, 0), error = null) }
        val result = block()
        if (_state.value.campus != campus) return
        when (result) {
            is AppResult.Success -> _state.update { it.copy(snapshot = result.data, progress = null, error = null) }
            is AppResult.Failure -> _state.update { it.copy(progress = null, error = messageFor(result.error)) }
        }
        recompute()
    }

    private fun onProgress(progress: LoadProgress) {
        _state.update { if (it.progress != null) it.copy(progress = progress) else it }
    }

    /** Aktualisiert Datum, "Jetzt", Uhrzeit-Auswahl und Ergebnis. */
    private fun recompute() {
        val now = ZonedDateTime.ofInstant(clock.instant(), RoomIcsParser.BERLIN)
        val today = now.toLocalDate()
        val nowMinute = now.hour * 60 + now.minute
        _state.update { s ->
            val snapshot = s.snapshot?.takeIf { it.date == today.toString() }
            val options = timeOptionsAfter(nowMinute)
            val chosen = s.chosenMinute?.takeIf { it in options }
            val at = chosen ?: nowMinute
            s.copy(
                today = today,
                snapshot = snapshot,
                chosenMinute = chosen,
                atMinute = at,
                timeOptions = options,
                result = snapshot?.let { FreeRoomCalculator.evaluate(it.rooms, at) } ?: FreeRoomsResult()
            )
        }
    }

    private fun messageFor(t: Throwable): String = when (t) {
        is IOException -> "LSF nicht erreichbar. Prüfe deine Verbindung und versuch es erneut."
        else -> t.message ?: "Raumpläne konnten nicht geladen werden"
    }

    companion object {
        private const val TICK_MS = 60_000L

        /** Späteste wählbare Uhrzeit (volle Stunde). */
        const val LAST_HOUR = 20

        /** Volle Stunden nach [nowMinute] bis einschließlich [LAST_HOUR] Uhr. */
        fun timeOptionsAfter(nowMinute: Int): List<Int> =
            ((nowMinute / 60) + 1..LAST_HOUR).map { it * 60 }
    }
}
