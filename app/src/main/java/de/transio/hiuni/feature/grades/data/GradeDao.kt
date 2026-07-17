package de.transio.hiuni.feature.grades.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GradeDao {

    /**
     * Beobachtet alle Leistungen, gruppiert nach Konto und innerhalb dessen
     * chronologisch (Prüfungsdatum ASC, NULLS LAST über `pruefungsDatum IS NULL`),
     * dann nach Versuch — so stehen Wiederholungen einer Prüfung in Reihenfolge.
     */
    @Query(
        """
        SELECT * FROM grades
        ORDER BY kontoNr, (pruefungsDatum IS NULL), pruefungsDatum ASC, versuch ASC
        """
    )
    fun observeAll(): Flow<List<GradeEntity>>

    /** Beobachtet die einzelne Summen-Zeile (GPA / gewichtete LP / Gesamt-LP). */
    @Query("SELECT * FROM grades_summary WHERE id = :id LIMIT 1")
    fun observeSummary(id: Int = GradesSummaryEntity.SINGLETON_ID): Flow<GradesSummaryEntity?>

    /** Snapshot aller Leistungen — nicht observed. Für den Diff im Repository. */
    @Query("SELECT * FROM grades")
    suspend fun findAll(): List<GradeEntity>

    /**
     * Alle im Transcript vorkommenden Semester-Labels (z.B. „WiSe 23/24", „SoSe 26").
     * Distinct, damit wir daraus das früheste Semester für den Icon-Unlock-Anker
     * bestimmen können, ohne alle Zeilen zu laden.
     */
    @Query("SELECT DISTINCT semester FROM grades WHERE semester != ''")
    suspend fun findDistinctSemesters(): List<String>

    @Query("SELECT * FROM grades WHERE mergeKey = :mergeKey LIMIT 1")
    suspend fun findByMergeKey(mergeKey: String): GradeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(grade: GradeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(summary: GradesSummaryEntity)

    /**
     * Entfernt Leistungszeilen, deren [GradeEntity.mergeKey] nicht mehr im
     * aktuellen Notenspiegel vorkommt (verschwundene/zurückgezogene Prüfungen).
     */
    @Query("DELETE FROM grades WHERE mergeKey NOT IN (:keep)")
    suspend fun pruneNotIn(keep: List<String>): Int

    /** Vollständiges Leeren — nur für Logout/Reset. */
    @Query("DELETE FROM grades")
    suspend fun clearAll(): Int
}
