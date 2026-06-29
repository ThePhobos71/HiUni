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
 * Ein im Learnweb (Moodle) eingeschriebener Kurs. Logischer Primärschlüssel ist
 * die `courseId` (aus Moodle), `rowId` ist Room-Autogenerate damit REPLACE-Upserts
 * stabil bleiben. Wir spiegeln zusätzlich die direkt klickbare Kurs-URL, damit
 * die UI ohne Repository-Lookup einen Browser-Intent feuern kann.
 */
@Entity(
    tableName = "learnweb_courses",
    indices = [Index(value = ["courseId"], unique = true)]
)
data class LearnwebCourse(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0L,
    val courseId: Long,
    val name: String,
    val url: String,
    val syncedAt: Long
)

@Dao
interface LearnwebCourseDao {

    @Query("SELECT * FROM learnweb_courses ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<LearnwebCourse>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(courses: List<LearnwebCourse>)

    @Query("DELETE FROM learnweb_courses WHERE courseId NOT IN (:keep)")
    suspend fun pruneNotIn(keep: List<Long>)

    @Query("DELETE FROM learnweb_courses")
    suspend fun deleteAll()
}
