package de.transio.hiuni.feature.todos.data

import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.core.notifications.data.NotificationKind
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Tests für [TodoReminderScheduler]: der AlarmManager-Wrapper wird gemockt, die
 * Uhr fest auf Mo 26.10.2026 12:00 (Europe/Berlin) gestellt.
 */
class TodoReminderSchedulerTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val now = ZonedDateTime.of(2026, 10, 26, 12, 0, 0, 0, zone).toInstant()

    private val notificationScheduler = mockk<NotificationScheduler>(relaxed = true)
    private val dao = mockk<TodoDao>(relaxed = true)

    private fun newScheduler() = TodoReminderScheduler(notificationScheduler, dao).apply {
        clock = Clock.fixed(now, zone)
    }

    private fun todo(id: Long, due: LocalDate?, done: Boolean = false) =
        TodoEntity(id = id, title = "Aufgabe $id", dueDate = due, isDone = done)

    @Test
    fun `offene Aufgabe mit künftiger Fälligkeit wird mit Kind TODO geplant`() {
        val t = todo(3, LocalDate.of(2026, 10, 28))

        val scheduled = newScheduler().sync(t)

        assertTrue(scheduled)
        val id = TodoReminderPlanner.reminderId(3)!!
        verify { notificationScheduler.cancel(id) }
        verify {
            notificationScheduler.schedule(
                eventId = id,
                title = "Heute fällig: Aufgabe 3",
                triggerAt = ZonedDateTime.of(2026, 10, 28, 9, 0, 0, 0, zone).toInstant(),
                kind = NotificationKind.TODO,
                body = any()
            )
        }
    }

    @Test
    fun `erledigte Aufgabe cancelt nur und plant nichts`() {
        val scheduled = newScheduler().sync(todo(3, LocalDate.of(2026, 10, 28), done = true))

        assertFalse(scheduled)
        verify { notificationScheduler.cancel(TodoReminderPlanner.reminderId(3)!!) }
        verify(exactly = 0) { notificationScheduler.schedule(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `vergangene Fälligkeit cancelt nur und plant nichts`() {
        val scheduled = newScheduler().sync(todo(4, LocalDate.of(2026, 10, 26)))

        assertFalse(scheduled)
        verify { notificationScheduler.cancel(TodoReminderPlanner.reminderId(4)!!) }
        verify(exactly = 0) { notificationScheduler.schedule(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cancel nutzt den Aufgaben-ID-Raum`() {
        newScheduler().cancel(9)

        verify { notificationScheduler.cancel(-1_000_000_009L) }
    }

    @Test
    fun `rescheduleAll plant alle offenen Aufgaben mit künftigem Reminder`() = runBlocking {
        coEvery { dao.findOpenWithDueDate() } returns listOf(
            todo(1, LocalDate.of(2026, 10, 27)),
            todo(2, LocalDate.of(2026, 10, 20)),
            todo(3, LocalDate.of(2026, 11, 2))
        )

        newScheduler().rescheduleAll()

        verify(exactly = 2) { notificationScheduler.schedule(any(), any(), any(), NotificationKind.TODO, any()) }
        verify { notificationScheduler.schedule(TodoReminderPlanner.reminderId(1)!!, any(), any(), any(), any()) }
        verify { notificationScheduler.schedule(TodoReminderPlanner.reminderId(3)!!, any(), any(), any(), any()) }
    }

    @Test
    fun `rescheduleAll übersteht eine fehlschlagende Query`() = runBlocking {
        coEvery { dao.findOpenWithDueDate() } throws IllegalStateException("DB zu")

        newScheduler().rescheduleAll()

        verify(exactly = 0) { notificationScheduler.schedule(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `ohne Uhr-Override folgt der Trigger der aktuellen System-Zone`() {
        // Szenario TIMEZONE_CHANGED bei laufendem Prozess: Singleton unter Berlin gebaut,
        // danach Wechsel nach New York. 09:00 muss in der neuen Zone gelten.
        val original = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin"))
            val fresh = TodoReminderScheduler(notificationScheduler, dao)
            val newYork = ZoneId.of("America/New_York")
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(newYork))
            val due = LocalDate.now(newYork).plusDays(3)

            assertTrue(fresh.sync(todo(5, due)))

            verify {
                notificationScheduler.schedule(
                    eventId = TodoReminderPlanner.reminderId(5)!!,
                    title = any(),
                    triggerAt = due.atTime(9, 0).atZone(newYork).toInstant(),
                    kind = NotificationKind.TODO,
                    body = any()
                )
            }
        } finally {
            java.util.TimeZone.setDefault(original)
        }
    }
}
