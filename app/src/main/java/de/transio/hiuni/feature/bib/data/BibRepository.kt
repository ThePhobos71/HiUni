package de.transio.hiuni.feature.bib.data

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.runCatchingApp
import de.transio.hiuni.di.IoDispatcher
import de.transio.hiuni.feature.calendar.data.CustomEventDao
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface BibRepository {
    val state: StateFlow<BibUiData>
    suspend fun refresh(): AppResult<BibSnapshot>
    /**
     * Lädt einen ggf. vorhandenen Disk-Cache (letzte erfolgreiche Refresh-Response)
     * und emittiert sofort die geparsten Daten als Snapshot, damit der Bib-Screen
     * beim Cold-Start nicht erst blank ist. Wird parallel zu [refresh] aufgerufen
     * — der Live-Fetch überschreibt den Cache-Snapshot, sobald er fertig ist.
     */
    suspend fun warmUpFromCache()
    suspend fun book(
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        roomId: Int
    ): AppResult<Unit>
    suspend fun cancel(booking: MyBooking): AppResult<Unit>
    suspend fun fetchEndTimes(
        date: LocalDate,
        start: LocalTime,
        roomId: Int
    ): AppResult<List<LocalTime>>
}

/** Was die UI aus dem Repository raussieht. */
data class BibUiData(
    val snapshot: BibSnapshot? = null,
    val loading: Boolean = false,
    val lastError: String? = null,
    val needsLogin: Boolean = false
)

@Singleton
class BibRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: BibClient,
    private val scraper: BibScraper,
    private val casSession: CasSession,
    private val eventDao: CustomEventDao,
    @IoDispatcher private val io: CoroutineDispatcher
) : BibRepository {

    private val _state = MutableStateFlow(BibUiData())
    override val state: StateFlow<BibUiData> = _state.asStateFlow()

    private fun cacheFile(): File = File(context.filesDir, "bib_index.html")

    override suspend fun warmUpFromCache(): Unit = withContext(io) {
        if (_state.value.snapshot != null) return@withContext
        val file = cacheFile().takeIf { it.exists() && it.length() > 0 } ?: return@withContext
        try {
            val html = file.readText()
            val snapshot = buildSnapshot(html, fetchedAt = Instant.ofEpochMilli(file.lastModified()))
            _state.update { it.copy(snapshot = snapshot, lastError = null) }
            Timber.i("Bib warmUpFromCache: snapshot age=${(System.currentTimeMillis() - file.lastModified()) / 1000}s")
        } catch (t: Throwable) {
            Timber.w(t, "Bib warmUpFromCache fehlgeschlagen — Cache wird ignoriert")
        }
    }

    override suspend fun refresh(): AppResult<BibSnapshot> = runCatchingApp {
        withContext(io) {
            _state.update { it.copy(loading = true, lastError = null) }
            try {
                val authenticated = casSession.state.value is CasState.Authenticated
                // Authenticated: Index-HTML mit OWN_BOOKING-Markern. Anonym:
                // Fall-back auf öffentliche Sicht (kein eigener Buchungs-Indikator).
                val html = if (authenticated) {
                    client.fetchIndexHtmlAuthenticated()
                } else {
                    client.fetchIndexHtmlAnonymous()
                }
                val snapshot = buildSnapshot(html, fetchedAt = Instant.now())
                if (authenticated) {
                    syncBookingsToCalendar(snapshot.myBookings)
                }
                runCatching {
                    cacheFile().writeText(html)
                }.onFailure { Timber.w(it, "Bib refresh: Cache-Write fehlgeschlagen") }
                _state.update {
                    it.copy(
                        snapshot = snapshot,
                        loading = false,
                        lastError = null,
                        needsLogin = !authenticated
                    )
                }
                snapshot
            } catch (t: Throwable) {
                Timber.e(t, "Bib refresh fehlgeschlagen")
                _state.update {
                    it.copy(loading = false, lastError = t.message ?: "Refresh fehlgeschlagen")
                }
                throw t
            }
        }
    }

    private fun buildSnapshot(html: String, fetchedAt: Instant): BibSnapshot {
        val byRoomDay = scraper.parseAvailability(html)
        val today = LocalDate.now()
        val roomsToday = BibConfig.ROOM_IDS.mapNotNull { roomId ->
            byRoomDay[today to roomId]
                ?: RoomDayAvailability(today, roomId, emptyList())
        }
        val myBookings = byRoomDay.values
            .asSequence()
            .flatMap { day -> collapseOwnBookings(day).asSequence() }
            .sortedWith(compareBy({ it.date }, { it.startTime }))
            .toList()
        return BibSnapshot(
            fetchedAt = fetchedAt,
            today = today,
            roomsToday = roomsToday,
            myBookings = myBookings,
            allDays = byRoomDay
        )
    }

    override suspend fun book(
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        roomId: Int
    ): AppResult<Unit> = runCatchingApp {
        withContext(io) {
            val response = client.bookRoom(date, start, end, roomId)
            Timber.i("Bib bookRoom Antwort: ${response.take(200)}")
            // Backend antwortet immer HTTP 200 — Erfolg/Fehler steckt im Body.
            // "ok" = gespeichert. Alles andere = User-sichtbare Fehlermeldung
            // (z. B. "Sie dürfen pro Tag nur 1 Buchung vornehmen.").
            val trimmed = response.trim()
            if (!trimmed.equals("ok", ignoreCase = true)) {
                throw IllegalStateException(trimmed.ifBlank { "Buchung fehlgeschlagen" })
            }
            refresh()
        }
    }

    override suspend fun cancel(booking: MyBooking): AppResult<Unit> = runCatchingApp {
        withContext(io) {
            val response = client.cancelBooking(booking.date, booking.startTime, booking.roomId)
            Timber.i("Bib cancelBooking Antwort: ${response.take(200)}")
            val trimmed = response.trim()
            if (!trimmed.equals("ok", ignoreCase = true)) {
                throw IllegalStateException(trimmed.ifBlank { "Konnte nicht stornieren" })
            }
            refresh()
        }
    }

    override suspend fun fetchEndTimes(
        date: LocalDate,
        start: LocalTime,
        roomId: Int
    ): AppResult<List<LocalTime>> = runCatchingApp {
        withContext(io) {
            val html = client.fetchEndTimes(date, start, roomId)
            scraper.parseEndTimes(html)
        }
    }

    /**
     * Spiegelt eigene Bib-Buchungen als CustomEventEntity (sourceKind=BIB_BOOKING)
     * in den Kalender. Damit erscheinen sie sowohl im CalendarScreen als auch in
     * der "Next-Event"-Anzeige auf der Home-Seite. Pruning entfernt Bookings, die
     * im aktuellen Refresh nicht mehr im Grid stehen (= storniert oder abgelaufen).
     */
    private suspend fun syncBookingsToCalendar(bookings: List<MyBooking>) {
        val zone = ZoneId.of("Europe/Berlin")
        val keep = mutableListOf<String>()
        for (b in bookings) {
            val ref = b.id
            keep += ref
            val start = b.date.atTime(b.startTime).atZone(zone).toInstant()
            val end = b.date.atTime(b.endTime).atZone(zone).toInstant()
            val existing = eventDao.findBySourceReference(
                CustomEventEntity.SOURCE_BIB_BOOKING, ref
            )
            val entity = CustomEventEntity(
                id = existing?.id ?: 0,
                title = "Gruppenraum ${b.roomLabel}",
                description = null,
                location = "Bibliothek · ${b.roomLabel}",
                startTime = start,
                endTime = end,
                sourceKind = CustomEventEntity.SOURCE_BIB_BOOKING,
                sourceReference = ref,
                reminderMinutesBefore = existing?.reminderMinutesBefore
            )
            if (existing == null) eventDao.insert(entity) else eventDao.update(entity)
        }
        eventDao.pruneBySourceKind(CustomEventEntity.SOURCE_BIB_BOOKING, keep)
    }

    /**
     * Fasst aufeinander-folgende OWN_BOOKING-Slots eines Tages zu einer
     * Buchung zusammen. Aus 4 × 30 min wird eine 2 h-Buchung.
     */
    private fun collapseOwnBookings(day: RoomDayAvailability): List<MyBooking> {
        val out = mutableListOf<MyBooking>()
        var runStart: LocalTime? = null
        var runEnd: LocalTime? = null
        for (slot in day.slots) {
            val isMine = slot.status == SlotStatus.OWN_BOOKING
            if (isMine) {
                if (runStart == null) runStart = slot.startTime
                runEnd = slot.endTime
            } else if (runStart != null && runEnd != null) {
                out += toMyBooking(day, runStart, runEnd)
                runStart = null
                runEnd = null
            }
        }
        if (runStart != null && runEnd != null) {
            out += toMyBooking(day, runStart, runEnd)
        }
        return out
    }

    private fun toMyBooking(day: RoomDayAvailability, start: LocalTime, end: LocalTime): MyBooking {
        val label = BibConfig.ROOM_META[day.roomId]?.label ?: "F${day.roomId}"
        return MyBooking(
            id = "${day.date}-${start.hour}${start.minute}-${day.roomId}",
            date = day.date,
            startTime = start,
            endTime = end,
            roomId = day.roomId,
            roomLabel = label
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BibRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindBibRepository(impl: BibRepositoryImpl): BibRepository
}
