package de.transio.hiuni.feature.rooms.data

import de.transio.hiuni.core.common.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.IsoFields
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Ladefortschritt: [done] von [total] Raumplänen geholt (erfolgreich oder nicht). */
data class LoadProgress(val done: Int, val total: Int)

/**
 * Lädt die Raumpläne eines Campus für heute, erst wenn gesucht wird, und merkt sie
 * sich bis Tagesende ([FreeRoomsStore]).
 *
 * Ablauf eines vollen Ladens: Raumliste holen und auf den Campus filtern, dann je
 * Raum den Wochenplan der aktuellen ISO-Kalenderwoche (`week=46_2026`) und daraus den
 * iCal-Export; die Termine werden für das heutige Datum ausgewertet
 * ([RoomIcsParser]). Höchstens [MAX_PARALLEL] Abrufe laufen gleichzeitig. Scheitert
 * ein Raum, fehlt nur er ([CampusSnapshot.missing]); [retryMissing] holt genau diese nach.
 */
@Singleton
class FreeRoomsRepository @Inject constructor(
    private val source: LsfRoomSource,
    private val store: FreeRoomsStore,
    private val clock: Clock
) {

    /** Stand für [campus], aber nur, wenn er von heute ist. */
    suspend fun cached(campus: Campus): CampusSnapshot? =
        store.snapshot(campus)?.takeIf { it.date == today().toString() }

    /** Lädt alle Räume von [campus] neu und ersetzt den Stand. */
    suspend fun load(campus: Campus, onProgress: (LoadProgress) -> Unit = {}): AppResult<CampusSnapshot> =
        guarded {
            val today = today()
            val rooms = LsfRoomListParser.parse(source.fetchRoomList()).filter { it.campus == campus }
            if (rooms.isEmpty()) throw RoomDataException("Keine Räume für ${campus.label} im LSF gefunden")
            onProgress(LoadProgress(0, rooms.size))
            val (loaded, missing) = fetchAll(rooms, today, onProgress)
            if (loaded.isEmpty()) throw RoomDataException("Kein Raumplan konnte geladen werden")
            save(campus, today, loaded, missing)
        }

    /**
     * Holt nur die Räume nach, die im heutigen Stand fehlen. Ohne heutigen Stand
     * wird voll geladen.
     */
    suspend fun retryMissing(campus: Campus, onProgress: (LoadProgress) -> Unit = {}): AppResult<CampusSnapshot> {
        val existing = cached(campus) ?: return load(campus, onProgress)
        if (existing.missing.isEmpty()) return AppResult.Success(existing)
        return guarded {
            val today = today()
            onProgress(LoadProgress(0, existing.missing.size))
            val (loaded, missing) = fetchAll(existing.missing, today, onProgress)
            save(campus, today, existing.rooms + loaded, missing)
        }
    }

    private suspend fun save(
        campus: Campus,
        today: LocalDate,
        rooms: List<RoomDay>,
        missing: List<LsfRoom>
    ): CampusSnapshot {
        val snapshot = CampusSnapshot(
            campus = campus,
            date = today.toString(),
            fetchedAt = clock.millis(),
            rooms = rooms.sortedBy { it.room.code },
            missing = missing.sortedBy { it.code }
        )
        store.save(snapshot)
        return snapshot
    }

    private suspend fun fetchAll(
        rooms: List<LsfRoom>,
        today: LocalDate,
        onProgress: (LoadProgress) -> Unit
    ): Pair<List<RoomDay>, List<LsfRoom>> = coroutineScope {
        val week = lsfWeek(today)
        val semaphore = Semaphore(MAX_PARALLEL)
        val done = AtomicInteger(0)
        val results = rooms.map { room ->
            async {
                val day = semaphore.withPermit {
                    try {
                        fetchRoom(room, today, week)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        Timber.w(t, "Raumfinder: Plan für %s nicht geladen", room.code)
                        null
                    }
                }
                onProgress(LoadProgress(done.incrementAndGet(), rooms.size))
                room to day
            }
        }.awaitAll()
        results.mapNotNull { it.second } to results.filter { it.second == null }.map { it.first }
    }

    private suspend fun fetchRoom(room: LsfRoom, today: LocalDate, week: String): RoomDay {
        val busy = when (val plan = LsfRoomPlanParser.parse(source.fetchWeekPlan(room.rgid, week))) {
            is LsfRoomPlanParser.Result.Ical -> {
                val ics = source.fetchText(plan.url)
                if (!ics.contains("BEGIN:VCALENDAR")) throw IOException("Kein iCal für ${room.code}")
                RoomIcsParser.busySpansOn(ics, today)
            }
            LsfRoomPlanParser.Result.NoEvents -> emptyList()
            LsfRoomPlanParser.Result.Invalid -> throw IOException("Kein Raumplan für ${room.code}")
        }
        return RoomDay(room, busy)
    }

    private inline fun guarded(block: () -> CampusSnapshot): AppResult<CampusSnapshot> = try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Timber.w(t, "Raumfinder: Laden fehlgeschlagen")
        AppResult.Failure(t)
    }

    private fun today(): LocalDate = ZonedDateTime.ofInstant(clock.instant(), RoomIcsParser.BERLIN).toLocalDate()

    companion object {
        /** Höchstens so viele LSF-Abrufe gleichzeitig. */
        const val MAX_PARALLEL = 4

        /** LSF-Wochenschlüssel: ISO-Kalenderwoche ohne führende Null und Wochenjahr, `46_2026`. */
        fun lsfWeek(date: LocalDate): String =
            "${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}_${date.get(IsoFields.WEEK_BASED_YEAR)}"
    }
}

/**
 * Das LSF hat geantwortet, aber mit den Daten lässt sich nichts anfangen (keine
 * Räume für den Campus, kein auswertbarer Plan). Bewusst keine `IOException`: der
 * Screen soll dann den echten Grund nennen und nicht auf die Verbindung zeigen.
 */
class RoomDataException(message: String) : Exception(message)
