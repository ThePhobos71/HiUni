package de.transio.hiuni.feature.lsf.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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

    /** Snapshot aller Klausuren eines Semesters — nicht observed. Verwendet vom Reminder-Scheduler. */
    @Query("SELECT * FROM exams WHERE semesterCode = :sc")
    suspend fun findAllBySemester(sc: String): List<ExamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exam: ExamEntity)

    /** Löscht alle Einträge im gegebenen Semester, deren `veranstaltungsNumber` NICHT in [keep] steckt. */
    @Query("DELETE FROM exams WHERE semesterCode = :semesterCode AND veranstaltungsNumber NOT IN (:keep)")
    suspend fun pruneSemester(semesterCode: String, keep: List<String>): Int

    @Query("DELETE FROM exams WHERE semesterCode = :semesterCode")
    suspend fun deleteSemester(semesterCode: String): Int
}
