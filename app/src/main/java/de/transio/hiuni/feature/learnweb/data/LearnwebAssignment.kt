package de.transio.hiuni.feature.learnweb.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Eine im Learnweb (Moodle) registrierte Assignment-Deadline. Logischer
 * Primärschlüssel ist die `eventId` (Moodle-Calendar-Event-ID), `rowId` ist
 * Room-Autogenerate für REPLACE-Upserts.
 *
 * `dueEpoch` ist Millisekunden seit Epoch und kodiert sowohl Datum als auch
 * Uhrzeit der Abgabe — wenn der Scraper im Title-String eine Uhrzeit findet
 * (z.B. „23:59 Uhr"), wird die genommen, sonst Default 23:59 lokal.
 *
 * `submissionStatus`/`lastSubmittedEpoch` werden NACH dem Calendar-Sync per
 * sekundärem Hit gegen die Assignment-Detail-Seite (`mod/assign/view.php?id=<cmId>`)
 * gefüllt. Drosselung steuert [LearnwebRepository] — siehe dort.
 */
@Entity(
    tableName = "learnweb_assignments",
    indices = [Index(value = ["eventId"], unique = true)]
)
data class LearnwebAssignment(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0L,
    val eventId: Long,
    val title: String,
    val dueEpoch: Long,
    val url: String,
    val syncedAt: Long,
    /** „submitted" / „draft" / „not_submitted" / „unknown". */
    val submissionStatus: String = STATUS_UNKNOWN,
    /** Datum letzter Abgabe in millis. 0 = nie abgegeben oder unbekannt. */
    val lastSubmittedEpoch: Long = 0L
) {
    companion object {
        const val STATUS_SUBMITTED = "submitted"
        const val STATUS_DRAFT = "draft"
        const val STATUS_NOT_SUBMITTED = "not_submitted"
        const val STATUS_UNKNOWN = "unknown"
    }
}

@Dao
interface LearnwebAssignmentDao {

    @Query("SELECT * FROM learnweb_assignments ORDER BY dueEpoch ASC")
    fun observeAll(): Flow<List<LearnwebAssignment>>

    @Query("SELECT * FROM learnweb_assignments WHERE dueEpoch >= :now ORDER BY dueEpoch ASC")
    fun observeUpcoming(now: Long): Flow<List<LearnwebAssignment>>

    @Query("SELECT * FROM learnweb_assignments WHERE dueEpoch >= :now ORDER BY dueEpoch ASC")
    suspend fun findUpcoming(now: Long): List<LearnwebAssignment>

    @Query("SELECT * FROM learnweb_assignments WHERE eventId = :eventId LIMIT 1")
    suspend fun findByEventId(eventId: Long): LearnwebAssignment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(assignments: List<LearnwebAssignment>)

    @Query("DELETE FROM learnweb_assignments WHERE eventId NOT IN (:keep)")
    suspend fun pruneNotIn(keep: List<Long>)

    @Query(
        "UPDATE learnweb_assignments " +
            "SET submissionStatus = :status, lastSubmittedEpoch = :submittedAt " +
            "WHERE rowId = :rowId"
    )
    suspend fun updateSubmissionStatus(rowId: Long, status: String, submittedAt: Long)

    @Query("DELETE FROM learnweb_assignments")
    suspend fun deleteAll()
}
