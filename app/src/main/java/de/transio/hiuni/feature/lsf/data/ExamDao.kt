package de.transio.hiuni.feature.lsf.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {

    /**
     * Beobachtet anstehende Klausuren ab `today` aufwärts, sortiert nach Datum.
     * Einträge ohne `examDate` (Termin noch offen) erscheinen ganz hinten — wir
     * sortieren `examDate` ASC NULLS LAST über `examDate IS NULL`.
     */
    @Query(
        """
        SELECT * FROM exams
        WHERE examDate IS NULL OR examDate >= :today
        ORDER BY (examDate IS NULL), examDate ASC
        LIMIT :limit
        """
    )
    fun observeUpcoming(today: Long, limit: Int): Flow<List<ExamEntity>>

    /**
     * Beobachtet ALLE Klausuren (alle Semester, auch vergangene), sortiert nach
     * Datum ASC mit NULLS LAST — fürs Klausurplan-Feature, das einen vollständigen
     * Timeline-Überblick rendert.
     */
    @Query(
        """
        SELECT * FROM exams
        ORDER BY (examDate IS NULL), examDate ASC
        """
    )
    fun observeAll(): Flow<List<ExamEntity>>

    @Query(
        """
        SELECT * FROM exams
        WHERE courseId = :courseId AND (examDate IS NULL OR examDate >= :today)
        ORDER BY (examDate IS NULL), examDate ASC
        """
    )
    fun observeUpcomingForCourse(courseId: String, today: Long): Flow<List<ExamEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM exams
        WHERE examDate IS NULL OR examDate >= :today
        """
    )
    fun observeUpcomingCount(today: Long): Flow<Int>

    @Query("SELECT * FROM exams WHERE veranstaltungsNumber = :vn AND semesterCode = :sc LIMIT 1")
    suspend fun findByNumberAndSemester(vn: String, sc: String): ExamEntity?

    @Query("SELECT * FROM exams WHERE rowId = :rowId LIMIT 1")
    suspend fun findByRowId(rowId: Long): ExamEntity?

    /** Snapshot aller Klausuren eines Semesters — nicht observed. Verwendet vom Reminder-Scheduler. */
    @Query("SELECT * FROM exams WHERE semesterCode = :sc")
    suspend fun findAllBySemester(sc: String): List<ExamEntity>

    /** Snapshot ALLER Klausuren (alle Semester + Quellen) — für den Reminder-Sync nach manuellen Edits. */
    @Query("SELECT * FROM exams")
    suspend fun findAll(): List<ExamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exam: ExamEntity)

    /**
     * Löscht LSF-Einträge im gegebenen Semester, deren `veranstaltungsNumber`
     * NICHT in [keep] steckt. KRITISCH: Der `source = 'LSF'`-Filter stellt sicher,
     * dass manuell erfasste Klausuren (`source = 'MANUAL'`) niemals vom LSF-Sync
     * weggeräumt werden.
     */
    @Query(
        "DELETE FROM exams WHERE semesterCode = :semesterCode " +
            "AND source = 'LSF' AND veranstaltungsNumber NOT IN (:keep)"
    )
    suspend fun pruneSemester(semesterCode: String, keep: List<String>): Int

    /** Löscht alle LSF-Einträge des Semesters — manuelle Einträge bleiben erhalten. */
    @Query("DELETE FROM exams WHERE semesterCode = :semesterCode AND source = 'LSF'")
    suspend fun deleteSemester(semesterCode: String): Int

    /** Löscht einen einzelnen Eintrag anhand seiner rowId — nur für manuelle Einträge aufgerufen. */
    @Query("DELETE FROM exams WHERE rowId = :rowId")
    suspend fun deleteByRowId(rowId: Long): Int
}
