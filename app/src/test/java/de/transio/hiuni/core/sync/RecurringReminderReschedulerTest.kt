package de.transio.hiuni.core.sync

import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.calendar.data.RecurrenceRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Tests für [RecurringReminderRescheduler]. Wir fixieren die Zone auf Europe/Berlin
 * (CI läuft in UTC) und nutzen weit-in-der-Zukunft-liegende Daten (2099), damit die
 * Vergangenheits-Guards des Schedulers unabhängig von der Wall-Clock greifen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecurringReminderReschedulerTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val repository = mockk<CalendarRepository>(relaxed = true)
    private val scheduler = mockk<NotificationScheduler>(relaxed = true)

    private lateinit var rescheduler: RecurringReminderRescheduler

    @Before
    fun setUp() {
        rescheduler = RecurringReminderRescheduler(repository, scheduler)
        rescheduler.zone = berlin
    }

    private fun instant(date: LocalDate, time: LocalTime): Instant =
        LocalDateTime.of(date, time).atZone(berlin).toInstant()

    private fun weeklyEvent(
        id: Long = 42L,
        start: Instant,
        reminderMinutes: Int?,
        until: LocalDate? = LocalDate.of(2099, 12, 31)
    ): CustomEventEntity = CustomEventEntity(
        id = id,
        title = "Mathe-VL",
        startTime = start,
        endTime = start.plus(Duration.ofHours(1)),
        sourceKind = CustomEventEntity.SOURCE_USER,
        reminderMinutesBefore = reminderMinutes,
        recurrenceRule = RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            interval = 1,
            byDays = null,
            until = until
        ).toJsonString()
    )

    @Test
    fun `rescheduleAfterFire plant Folge-Occurrence eine Woche spaeter`() = runTest {
        // Master: Mi 07.01.2099 10:00 wöchentlich, Reminder 30 min vorher.
        val masterStart = instant(LocalDate.of(2099, 1, 7), LocalTime.of(10, 0))
        val event = weeklyEvent(start = masterStart, reminderMinutes = 30)
        coEvery { repository.findById(42L) } returns event

        // Der Reminder für den 07.01-Termin feuerte 30 min vorher (09:30).
        val firedAt = instant(LocalDate.of(2099, 1, 7), LocalTime.of(9, 30))
        rescheduler.rescheduleAfterFire(eventId = 42L, firedAt = firedAt)

        // Erwartet: Reminder für Mi 14.01.2099 10:00 minus 30 min = 09:30.
        val triggerSlot = slot<Instant>()
        verify(exactly = 1) {
            scheduler.schedule(eq(42L), eq("Mathe-VL"), capture(triggerSlot), any(), any())
        }
        assertEquals(instant(LocalDate.of(2099, 1, 14), LocalTime.of(9, 30)), triggerSlot.captured)
    }

    @Test
    fun `rescheduleAfterFire ist No-op fuer Single-shot-Event`() = runTest {
        val single = CustomEventEntity(
            id = 5L,
            title = "Zahnarzt",
            startTime = instant(LocalDate.of(2099, 1, 7), LocalTime.of(10, 0)),
            endTime = instant(LocalDate.of(2099, 1, 7), LocalTime.of(11, 0)),
            reminderMinutesBefore = 15,
            recurrenceRule = null
        )
        coEvery { repository.findById(5L) } returns single

        rescheduler.rescheduleAfterFire(eventId = 5L, firedAt = Instant.now())

        verify(exactly = 0) { scheduler.schedule(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `rescheduleAfterFire ohne Reminder-Minuten plant nichts`() = runTest {
        val event = weeklyEvent(
            id = 7L,
            start = instant(LocalDate.of(2099, 1, 7), LocalTime.of(10, 0)),
            reminderMinutes = null
        )
        coEvery { repository.findById(7L) } returns event

        rescheduler.rescheduleAfterFire(eventId = 7L, firedAt = Instant.now())

        verify(exactly = 0) { scheduler.schedule(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `rescheduleAfterFire plant nichts wenn Event geloescht wurde`() = runTest {
        coEvery { repository.findById(99L) } returns null

        rescheduler.rescheduleAfterFire(eventId = 99L, firedAt = Instant.now())

        verify(exactly = 0) { scheduler.schedule(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `rescheduleAfterFire plant nichts wenn until erreicht ist`() = runTest {
        // Letzter Termin: Mi 07.01.2099 (until exklusiv am 08.01 → 07.01 ist letzte).
        val masterStart = instant(LocalDate.of(2099, 1, 7), LocalTime.of(10, 0))
        val event = weeklyEvent(
            start = masterStart,
            reminderMinutes = 30,
            until = LocalDate.of(2099, 1, 8)
        )
        coEvery { repository.findById(42L) } returns event

        val firedAt = instant(LocalDate.of(2099, 1, 7), LocalTime.of(9, 30))
        rescheduler.rescheduleAfterFire(eventId = 42L, firedAt = firedAt)

        verify(exactly = 0) { scheduler.schedule(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `rescheduleAll plant fuer jeden Recurring-Master mit Reminder den naechsten`() = runTest {
        val a = weeklyEvent(
            id = 1L,
            start = instant(LocalDate.of(2099, 1, 7), LocalTime.of(10, 0)),
            reminderMinutes = 30
        )
        val b = weeklyEvent(
            id = 2L,
            start = instant(LocalDate.of(2099, 1, 9), LocalTime.of(14, 0)),
            reminderMinutes = 60
        )
        coEvery { repository.recurringMastersWithReminder() } returns listOf(a, b)

        // now = Mo 05.01.2099 00:00 → nächste Occurrence a = Mi 07.01 10:00, b = Fr 09.01 14:00.
        val now = instant(LocalDate.of(2099, 1, 5), LocalTime.MIDNIGHT)
        rescheduler.rescheduleAll(now)

        verify(exactly = 1) {
            scheduler.schedule(
                eq(1L), any(),
                eq(instant(LocalDate.of(2099, 1, 7), LocalTime.of(9, 30))), any(), any()
            )
        }
        verify(exactly = 1) {
            scheduler.schedule(
                eq(2L), any(),
                eq(instant(LocalDate.of(2099, 1, 9), LocalTime.of(13, 0))), any(), any()
            )
        }
    }

    @Test
    fun `rescheduleAll ist tolerant gegen Repository-Fehler`() = runTest {
        coEvery { repository.recurringMastersWithReminder() } throws RuntimeException("db down")

        rescheduler.rescheduleAll(Instant.now())

        verify(exactly = 0) { scheduler.schedule(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { repository.recurringMastersWithReminder() }
    }
}
