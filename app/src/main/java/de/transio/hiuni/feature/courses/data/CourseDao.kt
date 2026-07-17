package de.transio.hiuni.feature.courses.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    @Query("SELECT * FROM courses ORDER BY semester DESC, name ASC")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CourseEntity?

    @Query("SELECT * FROM courses WHERE lsfId = :lsfId LIMIT 1")
    suspend fun findByLsfId(lsfId: String): CourseEntity?

    @Query("SELECT * FROM courses WHERE source = :source")
    suspend fun findBySource(source: String): List<CourseEntity>

    /**
     * Alle vorkommenden Semester-Labels (z.B. „Sommer 2026", „Winter 2025/26").
     * Distinct, damit der Icon-Unlock-Anker das früheste Semester aus dem
     * Studienverlauf mitziehen kann.
     */
    @Query("SELECT DISTINCT semester FROM courses WHERE semester != ''")
    suspend fun findDistinctSemesters(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(course: CourseEntity)

    @Update
    suspend fun update(course: CourseEntity)

    @Delete
    suspend fun delete(course: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Löscht alle LSF-Kurse, deren id NICHT in [keepIds] enthalten ist. */
    @Query("DELETE FROM courses WHERE source = :source AND id NOT IN (:keepIds)")
    suspend fun deleteSourcedNotIn(source: String, keepIds: List<String>): Int
}
