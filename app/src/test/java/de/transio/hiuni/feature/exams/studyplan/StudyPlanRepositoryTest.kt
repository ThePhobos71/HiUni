package de.transio.hiuni.feature.exams.studyplan

import de.transio.hiuni.core.database.AppDatabase
import de.transio.hiuni.core.database.newInMemoryDatabase
import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.feature.calendar.data.CalendarRepositoryImpl
import de.transio.hiuni.feature.calendar.data.CustomEventDao
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Persistenz-Semantik des Lernplans gegen eine echte In-Memory-Room-DB:
 * Neu-Generieren und Löschen treffen nur künftige Lernblöcke, vergangene und
 * laufende Blöcke sowie fremde Termine bleiben.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class StudyPlanRepositoryTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val day: LocalDate = LocalDate.of(2026, 11, 10)
    private val now: Instant = at(day, 12, 0)

    private lateinit var db: AppDatabase
    private lateinit var dao: CustomEventDao
    private val scheduler = mockk<NotificationScheduler>(relaxed = true)
    private lateinit var repository: StudyPlanRepository

    private fun at(date: LocalDate, hour: Int, minute: Int): Instant =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun studyEvent(start: Instant, ref: String) = CustomEventEntity(
        title = "Lernen: Alt",
        startTime = start,
        endTime = start.plus(Duration.ofMinutes(90)),
        sourceKind = CustomEventEntity.SOURCE_STUDY_PLAN,
        sourceReference = ref
    )

    private fun block(examId: Long, start: Instant, minutes: Long = 90) = PlannedStudyBlock(
        examId = examId,
        title = "Datenbanken",
        examDate = LocalDate.of(2026, 11, 20),
        start = start,
        end = start.plus(Duration.ofMinutes(minutes))
    )

    @Before
    fun setUp() {
        db = newInMemoryDatabase()
        dao = db.customEventDao()
        repository = StudyPlanRepository(dao, CalendarRepositoryImpl(dao), scheduler)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `Neu-Generieren ersetzt nur kuenftige Bloecke und behaelt vergangene`() = runTest {
        val pastId = dao.insert(studyEvent(at(day.minusDays(1), 9, 0), "studyplan:1:past"))
        val runningId = dao.insert(studyEvent(at(day, 11, 0), "studyplan:1:running"))
        val futureId = dao.insert(studyEvent(at(day.plusDays(1), 9, 0), "studyplan:1:future"))
        val userId = dao.insert(
            CustomEventEntity(
                title = "Zahnarzt",
                startTime = at(day.plusDays(1), 15, 0),
                endTime = at(day.plusDays(1), 16, 0)
            )
        )

        val written = repository.replaceFutureBlocks(
            blocks = listOf(
                block(7, at(day.plusDays(2), 9, 0)),
                block(7, at(day.plusDays(3), 13, 30))
            ),
            reminderMinutesBefore = null,
            now = now,
            zone = zone
        )

        assertEquals(2, written)
        val all = dao.observeAll().first()
        val ids = all.map { it.id }
        assertTrue(pastId in ids)
        assertTrue(runningId in ids)
        assertTrue(userId in ids)
        assertTrue(futureId !in ids)
        verify { scheduler.cancel(futureId) }

        val fresh = all.filter { it.sourceKind == CustomEventEntity.SOURCE_STUDY_PLAN && it.startTime > now }
        assertEquals(2, fresh.size)
        assertEquals("Lernen: Datenbanken", fresh.first().title)
        assertEquals("studyplan:7:2026-11-12T09:00", fresh.first().sourceReference)
        assertEquals("studyplan:7:2026-11-13T13:30", fresh.last().sourceReference)
        assertNull(fresh.first().reminderMinutesBefore)
    }

    @Test
    fun `Bloecke in der Vergangenheit werden nicht geschrieben`() = runTest {
        val written = repository.replaceFutureBlocks(
            blocks = listOf(block(7, at(day, 9, 0)), block(7, at(day, 14, 0))),
            reminderMinutesBefore = null,
            now = now,
            zone = zone
        )
        assertEquals(1, written)
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun `Erinnerungen werden fuer jeden neuen Block geplant`() = runTest {
        val start = at(day.plusDays(2), 9, 0)
        repository.replaceFutureBlocks(
            blocks = listOf(block(7, start)),
            reminderMinutesBefore = 15,
            now = now,
            zone = zone
        )
        val saved = dao.observeAll().first().single()
        assertEquals(15, saved.reminderMinutesBefore)
        verify { scheduler.schedule(saved.id, "Lernen: Datenbanken", start.minus(Duration.ofMinutes(15))) }
    }

    @Test
    fun `Lernplan loeschen entfernt nur kuenftige Lernbloecke`() = runTest {
        val pastId = dao.insert(studyEvent(at(day.minusDays(1), 9, 0), "studyplan:1:past"))
        val futureA = dao.insert(studyEvent(at(day.plusDays(1), 9, 0), "studyplan:1:a"))
        val futureB = dao.insert(studyEvent(at(day.plusDays(2), 9, 0), "studyplan:1:b"))
        // Im Kalender bearbeiteter Block: jetzt ein normaler eigener Termin.
        val editedId = dao.insert(
            CustomEventEntity(
                title = "Lernen: Datenbanken (verschoben)",
                startTime = at(day.plusDays(1), 18, 0),
                endTime = at(day.plusDays(1), 19, 30)
            )
        )

        val removed = repository.deleteFutureBlocks(now)

        assertEquals(2, removed)
        val ids = dao.observeAll().first().map { it.id }
        assertEquals(setOf(pastId, editedId), ids.toSet())
        verify { scheduler.cancel(futureA) }
        verify { scheduler.cancel(futureB) }
    }

    @Test
    fun `belegte Zeit ignoriert kuenftige Lernbloecke, die ersetzt werden`() = runTest {
        dao.insert(studyEvent(at(day.plusDays(1), 9, 0), "studyplan:1:future"))
        dao.insert(
            CustomEventEntity(
                title = "Vorlesung",
                startTime = at(day.plusDays(1), 10, 0),
                endTime = at(day.plusDays(1), 12, 0)
            )
        )

        val busy = repository.busyIntervals(
            from = day.atStartOfDay(zone).toInstant(),
            to = day.plusDays(5).atStartOfDay(zone).toInstant(),
            now = now
        )

        assertEquals(
            listOf(BusyInterval(at(day.plusDays(1), 10, 0), at(day.plusDays(1), 12, 0))),
            busy
        )
    }

    @Test
    fun `beobachtete kuenftige Bloecke zaehlen nur nicht gestartete Lernbloecke`() = runTest {
        dao.insert(studyEvent(at(day.minusDays(1), 9, 0), "studyplan:1:past"))
        dao.insert(studyEvent(at(day.plusDays(1), 9, 0), "studyplan:1:a"))
        assertEquals(1, repository.observeUpcomingBlocks(now).first().size)
    }
}
