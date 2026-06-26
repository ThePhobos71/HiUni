package de.transio.hiuni.feature.calendar.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface CustomEventDao {

    @Query(
        "SELECT * FROM custom_events " +
            "WHERE startTime BETWEEN :fromMillis AND :toMillis " +
            "ORDER BY startTime ASC"
    )
    fun observeRange(fromMillis: Long, toMillis: Long): Flow<List<CustomEventEntity>>

    /**
     * Master-Events mit Recurrence-Rule, deren Master-Start vor dem Window-Ende liegt
     * (also potentiell Occurrences ins Window expandieren könnten). Wir filtern bewusst
     * NICHT nach `endTime` oder einem Window-Start, weil ein Event von vor Wochen
     * weiterhin in der Zukunft Occurrences haben kann (das ist ja Sinn der Recurrence).
     * Die `until`-Auswertung passiert in [RecurrenceExpander].
     */
    @Query(
        "SELECT * FROM custom_events " +
            "WHERE recurrenceRule IS NOT NULL " +
            "AND startTime <= :toMillis"
    )
    fun observeRecurringMastersUntil(toMillis: Long): Flow<List<CustomEventEntity>>

    @Query("SELECT * FROM custom_events ORDER BY startTime ASC")
    fun observeAll(): Flow<List<CustomEventEntity>>

    @Query("SELECT * FROM custom_events WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CustomEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CustomEventEntity): Long

    @Update
    suspend fun update(event: CustomEventEntity)

    @Delete
    suspend fun delete(event: CustomEventEntity)

    @Query("DELETE FROM custom_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "SELECT * FROM custom_events " +
            "WHERE startTime >= :nowMillis " +
            "ORDER BY startTime ASC LIMIT 1"
    )
    suspend fun findNextEvent(nowMillis: Long = Instant.now().toEpochMilli()): CustomEventEntity?

    @Query("SELECT * FROM custom_events WHERE sourceKind = :kind AND sourceReference = :ref LIMIT 1")
    suspend fun findBySourceReference(kind: String, ref: String): CustomEventEntity?

    @Query("SELECT sourceReference FROM custom_events WHERE sourceKind = :kind AND sourceReference IS NOT NULL")
    suspend fun sourceReferencesFor(kind: String): List<String>

    @Query("DELETE FROM custom_events WHERE sourceKind = :kind AND sourceReference NOT IN (:keep)")
    suspend fun pruneBySourceKind(kind: String, keep: List<String>)
}
