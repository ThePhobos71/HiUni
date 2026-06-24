package de.transio.hiuni.feature.sport.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SportDao {

    /** Alle Termine, deren Ende noch in der Zukunft liegt, früheste zuerst. */
    @Query("SELECT * FROM sport_events WHERE endTime > :now ORDER BY startTime ASC")
    fun observeUpcoming(now: Long): Flow<List<SportEventEntity>>

    /**
     * Alle Termine eines Tages (millis). `dayStart` inklusive, `dayEnd`
     * exklusive — passt zu `LocalDate.atStartOfDay(zone).toInstant()`.
     */
    @Query(
        "SELECT * FROM sport_events " +
            "WHERE startTime >= :dayStart AND startTime < :dayEnd " +
            "ORDER BY startTime ASC"
    )
    fun observeByDate(dayStart: Long, dayEnd: Long): Flow<List<SportEventEntity>>

    /** Distinkte Titel der zukünftigen Termine — Quelle für die Filter-Chips. */
    @Query(
        "SELECT DISTINCT title FROM sport_events " +
            "WHERE endTime > :now ORDER BY title ASC"
    )
    fun observeDistinctTitles(now: Long): Flow<List<String>>

    /** Zähler für die Quick-Access-Kachel auf Home. */
    @Query("SELECT COUNT(*) FROM sport_events WHERE endTime > :now")
    fun countUpcoming(now: Long): Flow<Int>

    /** Einzelner Termin via stabiler supersaas-Slot-ID — Quelle für den Detail-Screen. */
    @Query("SELECT * FROM sport_events WHERE supersaasSlotId = :slotId LIMIT 1")
    fun observeBySlotId(slotId: Long): Flow<SportEventEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<SportEventEntity>)

    @Query("DELETE FROM sport_events WHERE endTime < :before")
    suspend fun pruneBefore(before: Long)
}
