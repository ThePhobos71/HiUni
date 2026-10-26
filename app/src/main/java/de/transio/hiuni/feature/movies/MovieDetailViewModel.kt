package de.transio.hiuni.feature.movies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.common.LoadStatus
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.movies.data.MovieEntity
import de.transio.hiuni.feature.movies.data.MoviesRepository
import de.transio.hiuni.feature.movies.data.isSurpriseScreening
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Liest den Film samt TMDB-Details (Backdrop, Bewertung, Regie, Cast) nur aus Room.
 * Die Details holt der Programm-Refresh im Hintergrund (siehe
 * `MoviesRepositoryImpl.enrichWithTmdb`), das Öffnen kostet also keinen Netz-Roundtrip.
 *
 * Zusätzlich lässt sich die Vorstellung in den Kalender pinnen (Vorlage:
 * `SportDetailViewModel`). Der Pin ist ein normaler Einzeltermin mit
 * `sourceKind = MOVIE_PIN`; sein Reminder wird hier geplant und nach Reboot/Update
 * vom `ReminderRestorer` wie jeder andere Einzeltermin wiederhergestellt.
 */
@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MoviesRepository,
    private val calendarRepository: CalendarRepository,
    private val scheduler: NotificationScheduler,
    private val settings: SettingsDataStore,
    private val clock: Clock
) : ViewModel() {

    private val filmId: String = savedStateHandle["filmId"] ?: ""
    private val sessionId: String = savedStateHandle["sessionId"] ?: ""
    private val sourceRef: String = "movie:$filmId:$sessionId"

    private val _state = MutableStateFlow(MovieDetailUiState())
    val state: StateFlow<MovieDetailUiState> = _state.asStateFlow()

    // Serialisiert Pin/Unpin, damit ein schneller Doppel-Tap kein zweites Insert
    // auslöst, bevor der erste Lookup den frisch angelegten Eintrag sieht.
    private val pinMutex = Mutex()

    init { load() }

    private fun load() = viewModelScope.launch {
        val movie = repository.findById(filmId, sessionId)
        val pinned = calendarRepository
            .findBySourceReference(CustomEventEntity.SOURCE_MOVIE_PIN, sourceRef) != null
        _state.update {
            it.copy(
                load = LoadStatus.Idle,
                movie = movie,
                rating = movie?.tmdbRating,
                voteCount = movie?.tmdbVoteCount,
                backdropUrl = movie?.backdropUrl,
                cast = movie?.castNames.orEmpty(),
                crewDirector = movie?.director,
                canPin = movie != null && isPinnable(movie),
                isPinned = pinned
            )
        }
    }

    /** Tap auf den Kalender-Button: legt an oder entfernt, je nach aktuellem Stand. */
    fun togglePin() {
        if (_state.value.isPinned) unpin() else pinToCalendar()
    }

    fun pinToCalendar() = viewModelScope.launch {
        pinMutex.withLock {
            val movie = _state.value.movie ?: return@withLock
            if (!isPinnable(movie)) return@withLock
            val (start, end) = pinWindow(movie) ?: return@withLock
            // Bestehender Pin: ID übernehmen, damit `upsert` aktualisiert statt dupliziert.
            // Dessen Reminder bleibt, auch wenn er im Kalender angepasst wurde.
            val existing = calendarRepository
                .findBySourceReference(CustomEventEntity.SOURCE_MOVIE_PIN, sourceRef)
            val reminderMinutes = if (existing != null) {
                existing.reminderMinutesBefore
            } else {
                defaultReminderMinutes()
            }
            val candidate = CustomEventEntity(
                id = existing?.id ?: 0L,
                title = pinTitle(movie),
                location = movie.location?.takeIf { it.isNotBlank() },
                startTime = start,
                endTime = end,
                sourceKind = CustomEventEntity.SOURCE_MOVIE_PIN,
                sourceReference = sourceRef,
                reminderMinutesBefore = reminderMinutes
            )
            val id = calendarRepository.upsert(candidate)
            scheduler.cancel(id)
            if (reminderMinutes != null) {
                val triggerAt = start.minus(Duration.ofMinutes(reminderMinutes.toLong()))
                if (triggerAt.isAfter(clock.instant())) {
                    scheduler.schedule(id, candidate.title, triggerAt)
                }
            }
            _state.update { it.copy(isPinned = true) }
        }
    }

    fun unpin() = viewModelScope.launch {
        pinMutex.withLock {
            val existing = calendarRepository
                .findBySourceReference(CustomEventEntity.SOURCE_MOVIE_PIN, sourceRef)
            if (existing != null) {
                scheduler.cancel(existing.id)
                calendarRepository.delete(existing)
            }
            _state.update { it.copy(isPinned = false) }
        }
    }

    /**
     * Standard-Reminder aus den Einstellungen, wie ihn der Kalender für neue Termine
     * vorschlägt. Lässt sich die Einstellung nicht lesen, gibt es keinen Reminder.
     */
    private suspend fun defaultReminderMinutes(): Int? =
        runCatching { settings.notificationMinutesBefore.first() }.getOrNull()

    /** Nur kommende Vorstellungen mit bekanntem Datum und Uhrzeit landen im Kalender. */
    private fun isPinnable(movie: MovieEntity): Boolean {
        if (movie.isPast) return false
        val (start, _) = pinWindow(movie) ?: return false
        return start.isAfter(clock.instant())
    }

    /**
     * Start und Ende der Vorstellung. `null`, wenn Datum oder Uhrzeit fehlen: dann
     * gibt es nichts Sinnvolles einzutragen. Ohne Laufzeit rechnen wir mit zwei Stunden.
     */
    private fun pinWindow(movie: MovieEntity): Pair<Instant, Instant>? {
        val date = movie.date ?: return null
        val time = movie.time ?: return null
        val start = date.atTime(time).atZone(CINEMA_ZONE).toInstant()
        val minutes = movie.durationMinutes?.takeIf { it > 0 }?.toLong() ?: FALLBACK_DURATION_MINUTES
        return start to start.plus(Duration.ofMinutes(minutes))
    }

    private fun pinTitle(movie: MovieEntity): String = when {
        movie.isSurpriseScreening() -> "Überraschungsfilm im Uni-Kino"
        movie.title.isBlank() -> "Uni-Kino"
        else -> movie.title
    }

    private companion object {
        /** Das Uni-Kino spielt in Münster, Programmzeiten sind Ortszeit. */
        val CINEMA_ZONE: ZoneId = ZoneId.of("Europe/Berlin")
        const val FALLBACK_DURATION_MINUTES = 120L
    }
}
