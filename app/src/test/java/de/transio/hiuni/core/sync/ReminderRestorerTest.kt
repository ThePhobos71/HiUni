package de.transio.hiuni.core.sync

import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationPresenter
import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.core.notifications.lecture.LiveLectureScheduler
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.calendar.data.RecurrenceRule
import de.transio.hiuni.feature.learnweb.data.LearnwebAssignment
import de.transio.hiuni.feature.learnweb.data.LearnwebAssignmentDao
import de.transio.hiuni.feature.lsf.data.ExamDao
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.todos.data.TodoDao
import de.transio.hiuni.feature.todos.data.TodoEntity
import de.transio.hiuni.feature.todos.data.TodoReminderPlanner
import de.transio.hiuni.feature.todos.data.TodoReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Tests für den zentralen [ReminderRestorer], der nach Reboot, App-Update,
 * Zeit-/Zonenwechsel und beim App-Start ALLE lokalen Reminder-Arten aus dem
 * DB-Bestand neu plant.
 *
 * Die echten Teil-Scheduler (Recurring, Klausur, Learnweb, Aufgaben) laufen mit,
 * nur der AlarmManager-Wrapper [NotificationScheduler] und die Datenquellen sind
 * gemockt. Der Mock protokolliert jede schedule/cancel-Operation, daraus wird der
 * "aktive" Alarm-Zustand rekonstruiert (gleiche ID überschreibt, wie
 * `FLAG_UPDATE_CURRENT` im echten Scheduler).
 *
 * Alle Termine liegen relativ zu `Instant.now()` in der Zukunft bzw.
 * Vergangenheit, weil Klausur-, Learnweb- und Aufgaben-Scheduler intern die
 * Systemuhr nutzen.
 */
class ReminderRestorerTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    // Auf Millisekunden gekappt: Learnweb-Deadlines liegen als Epoch-Millis in der DB.
    private val now: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    private val notificationScheduler = mockk<NotificationScheduler>(relaxed = true)
    private val presenter = mockk<NotificationPresenter>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val calendarRepository = mockk<CalendarRepository>(relaxed = true)
    private val examDao = mockk<ExamDao>(relaxed = true)
    private val learnwebDao = mockk<LearnwebAssignmentDao>(relaxed = true)
    private val todoDao = mockk<TodoDao>(relaxed = true)
    private val liveLectureScheduler = mockk<LiveLectureScheduler>(relaxed = true)

    /** Aktiver Alarm-Zustand: ID → Trigger (letzte schedule-Operation gewinnt). */
    private val active = linkedMapOf<Long, Instant>()
    private val scheduleCalls = mutableListOf<Pair<Long, Instant>>()

    /** Persistierte Diff-Sets (stateful, damit ein zweiter Lauf gegen den ersten difft). */
    private var persistedExamIds: Set<Long> = emptySet()
    private var persistedLearnwebIds: Set<Long> = emptySet()

    @Before
    fun setUp() {
        every { notificationScheduler.schedule(any(), any(), any(), any(), any()) } answers {
            val id = firstArg<Long>()
            val trigger = arg<Instant>(2)
            scheduleCalls += id to trigger
            active[id] = trigger
        }
        every { notificationScheduler.cancel(any()) } answers {
            active.remove(firstArg<Long>())
        }

        every { settings.scheduledExamReminderIds } returns flow { emit(persistedExamIds) }
        coEvery { settings.setScheduledExamReminderIds(any()) } answers {
            persistedExamIds = firstArg()
        }
        every { settings.scheduledLearnwebReminderIds } returns flow { emit(persistedLearnwebIds) }
        coEvery { settings.setScheduledLearnwebReminderIds(any()) } answers {
            persistedLearnwebIds = firstArg()
        }

        coEvery { calendarRepository.upcomingSingleShotWithReminder(any()) } returns emptyList()
        coEvery { calendarRepository.recurringMastersWithReminder() } returns emptyList()
        coEvery { examDao.findAll() } returns emptyList()
        coEvery { learnwebDao.findUpcoming(any()) } returns emptyList()
        coEvery { todoDao.findOpenWithDueDate() } returns emptyList()
    }

    private fun newRestorer(): ReminderRestorer {
        val recurring = RecurringReminderRescheduler(calendarRepository, notificationScheduler)
        return ReminderRestorer(
            calendarRepository = calendarRepository,
            scheduler = notificationScheduler,
            recurringReminderRescheduler = recurring,
            examDao = examDao,
            examReminderScheduler = ExamReminderScheduler(notificationScheduler, presenter, settings),
            learnwebAssignmentDao = learnwebDao,
            learnwebReminderScheduler = LearnwebAssignmentReminderScheduler(notificationScheduler, settings),
            todoReminderScheduler = TodoReminderScheduler(notificationScheduler, todoDao),
            liveLectureScheduler = liveLectureScheduler
        )
    }

    // --- Fixtures ---------------------------------------------------------------

    private fun singleShot(
        id: Long,
        start: Instant,
        reminderMinutes: Int? = 30,
        sourceKind: String = CustomEventEntity.SOURCE_USER
    ) = CustomEventEntity(
        id = id,
        title = "Termin $id",
        startTime = start,
        endTime = start.plus(Duration.ofHours(1)),
        sourceKind = sourceKind,
        reminderMinutesBefore = reminderMinutes
    )

    private fun weekly(id: Long, start: Instant, reminderMinutes: Int) = CustomEventEntity(
        id = id,
        title = "Wöchentlich $id",
        startTime = start,
        endTime = start.plus(Duration.ofHours(1)),
        reminderMinutesBefore = reminderMinutes,
        recurrenceRule = RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            interval = 1,
            byDays = null,
            until = LocalDate.now().plusYears(1)
        ).toJsonString()
    )

    private fun exam(rowId: Long, date: LocalDate) = ExamEntity(
        rowId = rowId,
        veranstaltungsNumber = rowId.toString(),
        pruefungstext = "Prüfung $rowId",
        moduleName = "Modul $rowId",
        parentModule = null,
        examDate = date,
        examTime = LocalTime.of(10, 0),
        rooms = listOf("SC.A.0.09"),
        semester = "WiSe 26/27",
        semesterCode = "20262",
        registrationDate = null,
        cancellationDeadline = null,
        pruefer = null,
        courseId = null
    )

    private fun assignment(rowId: Long, due: Instant) = LearnwebAssignment(
        rowId = rowId,
        eventId = 1000 + rowId,
        title = "Blatt $rowId ist fällig.",
        dueEpoch = due.toEpochMilli(),
        url = "https://www.uni-muenster.de/LearnWeb/learnweb2/mod/assign/view.php?id=$rowId",
        syncedAt = now.toEpochMilli()
    )

    private fun todo(id: Long, due: LocalDate, done: Boolean = false) =
        TodoEntity(id = id, title = "Aufgabe $id", dueDate = due, isDone = done)

    private fun examId(rowId: Long, slot: Int) = ExamReminderScheduler.EXAM_ID_OFFSET + rowId * 10 + slot
    private fun learnwebId(rowId: Long, slot: Int) =
        LearnwebAssignmentReminderScheduler.LEARNWEB_ID_OFFSET + rowId * 10 + slot

    // --- Tests ------------------------------------------------------------------

    @Test
    fun `restoreAll plant jede Reminder-Art aus dem DB-Bestand neu`() = runBlocking {
        val singleStart = now.plus(Duration.ofDays(2))
        val sportStart = now.plus(Duration.ofDays(3))
        val weeklyStart = now.plus(Duration.ofDays(4))
        coEvery { calendarRepository.upcomingSingleShotWithReminder(any()) } returns listOf(
            singleShot(id = 11, start = singleStart, reminderMinutes = 30),
            singleShot(
                id = 12,
                start = sportStart,
                reminderMinutes = 15,
                sourceKind = CustomEventEntity.SOURCE_SPORT_PIN
            )
        )
        coEvery { calendarRepository.recurringMastersWithReminder() } returns listOf(
            weekly(id = 21, start = weeklyStart, reminderMinutes = 10)
        )
        val examDate = LocalDate.now(zone).plusDays(30)
        coEvery { examDao.findAll() } returns listOf(exam(rowId = 5, date = examDate))
        val due = now.plus(Duration.ofDays(5))
        coEvery { learnwebDao.findUpcoming(any()) } returns listOf(assignment(rowId = 7, due = due))
        val todoDue = LocalDate.now(zone).plusDays(10)
        coEvery { todoDao.findOpenWithDueDate() } returns listOf(todo(id = 9, due = todoDue))

        newRestorer().restoreAll(now)

        // Single-shot: Start minus Reminder-Minuten.
        assertEquals(singleStart.minus(Duration.ofMinutes(30)), active[11L])
        assertEquals(sportStart.minus(Duration.ofMinutes(15)), active[12L])
        // Recurring: nächste Occurrence (hier der Master selbst) minus Reminder.
        assertEquals(weeklyStart.minus(Duration.ofMinutes(10)), active[21L])
        // Klausur: beide Slots.
        assertEquals(
            examDate.minusDays(7).atTime(12, 0).atZone(zone).toInstant(),
            active[examId(5, ExamReminderScheduler.SLOT_7_DAYS)]
        )
        assertEquals(
            examDate.minusDays(1).atTime(18, 0).atZone(zone).toInstant(),
            active[examId(5, ExamReminderScheduler.SLOT_1_DAY)]
        )
        // Learnweb: 2h-Slot liegt sicher in der Zukunft.
        assertEquals(
            due.minus(Duration.ofHours(2)),
            active[learnwebId(7, LearnwebAssignmentReminderScheduler.SLOT_2_HOURS)]
        )
        // Aufgabe: 09:00 am Fälligkeitstag.
        assertEquals(
            todoDue.atTime(9, 0).atZone(zone).toInstant(),
            active[TodoReminderPlanner.reminderId(9)!!]
        )
    }

    @Test
    fun `Lernplan-Bloecke mit Erinnerung werden wie Einzeltermine neu geplant`() = runBlocking {
        val blockStart = now.plus(Duration.ofDays(2))
        coEvery { calendarRepository.upcomingSingleShotWithReminder(any()) } returns listOf(
            singleShot(
                id = 41,
                start = blockStart,
                reminderMinutes = 15,
                sourceKind = CustomEventEntity.SOURCE_STUDY_PLAN
            )
        )

        newRestorer().restoreAll(now)

        assertEquals(blockStart.minus(Duration.ofMinutes(15)), active[41L])
    }

    @Test
    fun `restoreAll plant keine vergangenen Reminder`() = runBlocking {
        coEvery { calendarRepository.upcomingSingleShotWithReminder(any()) } returns listOf(
            // Termin schon vorbei.
            singleShot(id = 31, start = now.minus(Duration.ofHours(3))),
            // Termin noch nicht vorbei, aber der Reminder-Zeitpunkt (30 min vorher) schon.
            singleShot(id = 32, start = now.plus(Duration.ofMinutes(10)), reminderMinutes = 30),
            // Termin ohne Reminder.
            singleShot(id = 33, start = now.plus(Duration.ofDays(1)), reminderMinutes = null)
        )
        coEvery { examDao.findAll() } returns listOf(exam(rowId = 6, date = LocalDate.now(zone).minusDays(3)))
        coEvery { learnwebDao.findUpcoming(any()) } returns listOf(
            assignment(rowId = 8, due = now.minus(Duration.ofHours(1)))
        )
        coEvery { todoDao.findOpenWithDueDate() } returns listOf(
            todo(id = 4, due = LocalDate.now(zone).minusDays(1)),
            todo(id = 5, due = LocalDate.now(zone).plusDays(2), done = true)
        )

        newRestorer().restoreAll(now)

        assertTrue("Keine Alarme erwartet, war: $scheduleCalls", scheduleCalls.isEmpty())
        assertTrue(active.isEmpty())
    }

    @Test
    fun `Learnweb-Spiegelungen im Kalender bekommen keinen eigenen Termin-Reminder`() = runBlocking {
        // Die Kalender-Spiegelungen tragen zwar reminderMinutesBefore, wurden aber nie
        // über den NotificationScheduler geplant; die Deadline-Erinnerungen laufen über
        // den LearnwebAssignmentReminderScheduler. Ein Restore darf daraus keine
        // zusätzlichen (doppelten) Alarme machen.
        val start = now.plus(Duration.ofDays(2))
        coEvery { calendarRepository.upcomingSingleShotWithReminder(any()) } returns listOf(
            singleShot(id = 41, start = start, sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT),
            singleShot(id = 42, start = start, sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ICAL),
            singleShot(id = 43, start = start, sourceKind = CustomEventEntity.SOURCE_USER)
        )

        newRestorer().restoreAll(now)

        assertFalse(active.containsKey(41L))
        assertFalse(active.containsKey(42L))
        assertTrue(active.containsKey(43L))
    }

    @Test
    fun `wiederkehrender Master in der Single-shot-Liste wird nicht als Einzeltermin geplant`() = runBlocking {
        // Wiederkehrende Master gehören dem Recurring-Pfad (nächste Occurrence statt
        // Master-Start). Taucht einer in der Single-shot-Liste auf, darf der
        // Single-shot-Pfad ihn nicht zusätzlich planen. Recurring-Liste ist hier leer.
        val master = weekly(id = 51, start = now.plus(Duration.ofDays(2)), reminderMinutes = 5)
        coEvery { calendarRepository.upcomingSingleShotWithReminder(any()) } returns listOf(master)

        newRestorer().restoreAll(now)

        assertEquals(0, scheduleCalls.count { it.first == 51L })
    }

    @Test
    fun `zweiter Lauf ist harmlos und erzeugt denselben Alarm-Zustand`() = runBlocking {
        coEvery { calendarRepository.upcomingSingleShotWithReminder(any()) } returns listOf(
            singleShot(id = 11, start = now.plus(Duration.ofDays(2)))
        )
        coEvery { calendarRepository.recurringMastersWithReminder() } returns listOf(
            weekly(id = 21, start = now.plus(Duration.ofDays(4)), reminderMinutes = 10)
        )
        coEvery { examDao.findAll() } returns listOf(exam(rowId = 5, date = LocalDate.now(zone).plusDays(30)))
        coEvery { learnwebDao.findUpcoming(any()) } returns listOf(
            assignment(rowId = 7, due = now.plus(Duration.ofDays(5)))
        )
        coEvery { todoDao.findOpenWithDueDate() } returns listOf(todo(id = 9, due = LocalDate.now(zone).plusDays(10)))
        val restorer = newRestorer()

        restorer.restoreAll(now)
        val afterFirst = LinkedHashMap(active)
        val callsFirst = scheduleCalls.size
        restorer.restoreAll(now)

        assertEquals(afterFirst, active)
        // Jede ID wurde im zweiten Lauf genau einmal erneut geplant, mit identischem Trigger
        // (überschreibt dank gleicher Request-ID den vorhandenen Alarm statt zu duplizieren).
        val secondRun = scheduleCalls.drop(callsFirst)
        assertEquals(scheduleCalls.take(callsFirst).toSet(), secondRun.toSet())
        assertEquals(secondRun.size, secondRun.map { it.first }.toSet().size)
    }

    @Test
    fun `Fehler in einer Quelle bricht die anderen Reminder-Arten nicht ab`() = runBlocking {
        coEvery { examDao.findAll() } throws IllegalStateException("DB kaputt")
        coEvery { calendarRepository.upcomingSingleShotWithReminder(any()) } returns listOf(
            singleShot(id = 11, start = now.plus(Duration.ofDays(2)))
        )
        coEvery { todoDao.findOpenWithDueDate() } returns listOf(todo(id = 9, due = LocalDate.now(zone).plusDays(10)))
        coEvery { learnwebDao.findUpcoming(any()) } returns listOf(
            assignment(rowId = 7, due = now.plus(Duration.ofDays(5)))
        )

        newRestorer().restoreAll(now)

        assertTrue(active.containsKey(11L))
        assertTrue(active.containsKey(TodoReminderPlanner.reminderId(9)!!))
        assertTrue(active.containsKey(learnwebId(7, LearnwebAssignmentReminderScheduler.SLOT_2_HOURS)))
    }

    @Test
    fun `restoreAll plant die Laufende-Vorlesung-Mitteilung mit derselben Uhrzeit neu`() = runBlocking {
        newRestorer().restoreAll(now)

        // Nach Reboot mitten in der Vorlesung erscheint die Mitteilung sofort wieder,
        // der Weck-Alarm für den nächsten Vorlesungsbeginn steht wieder.
        coVerify(exactly = 1) { liveLectureScheduler.replan(now) }
    }

    @Test
    fun `Fehler beim Vorlesungs-Planen bricht die anderen Reminder nicht ab`() = runBlocking {
        coEvery { liveLectureScheduler.replan(any()) } throws IllegalStateException("kaputt")
        coEvery { calendarRepository.upcomingSingleShotWithReminder(any()) } returns listOf(
            singleShot(id = 11, start = now.plus(Duration.ofDays(2)))
        )
        coEvery { todoDao.findOpenWithDueDate() } returns listOf(todo(id = 9, due = LocalDate.now(zone).plusDays(10)))

        newRestorer().restoreAll(now)

        assertTrue(active.containsKey(11L))
        assertTrue(active.containsKey(TodoReminderPlanner.reminderId(9)!!))
    }
}
