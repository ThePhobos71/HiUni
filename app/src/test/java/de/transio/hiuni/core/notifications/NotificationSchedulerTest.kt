package de.transio.hiuni.core.notifications

import android.app.AlarmManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration
import java.time.Instant

/**
 * Absicherung der Idempotenz, auf die sich der [de.transio.hiuni.core.sync.ReminderRestorer]
 * verlässt: dieselbe Reminder-ID mehrfach zu planen ersetzt den vorhandenen Alarm
 * (gleicher PendingIntent-Request-Code + `FLAG_UPDATE_CURRENT`), statt einen
 * zweiten anzulegen. Vergangene Trigger werden gar nicht erst geplant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationSchedulerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val scheduler = NotificationScheduler(context)

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun alarms() = shadowOf(alarmManager).scheduledAlarms

    @Test
    fun `gleiche ID zweimal planen ergibt genau einen Alarm mit dem letzten Trigger`() {
        val first = Instant.now().plus(Duration.ofDays(1))
        val second = first.plus(Duration.ofHours(1))

        scheduler.schedule(eventId = 42L, title = "Termin", triggerAt = first)
        scheduler.schedule(eventId = 42L, title = "Termin", triggerAt = first)
        scheduler.schedule(eventId = 42L, title = "Termin", triggerAt = second)

        assertEquals(1, alarms().size)
        assertEquals(second.toEpochMilli(), alarms().single().triggerAtMs)
    }

    @Test
    fun `verschiedene IDs ergeben getrennte Alarme`() {
        val at = Instant.now().plus(Duration.ofDays(1))

        scheduler.schedule(eventId = 1L, title = "A", triggerAt = at)
        scheduler.schedule(eventId = 2L, title = "B", triggerAt = at)

        assertEquals(2, alarms().size)
    }

    @Test
    fun `vergangener Trigger wird nicht geplant`() {
        scheduler.schedule(eventId = 7L, title = "Vorbei", triggerAt = Instant.now().minusSeconds(60))

        assertTrue(alarms().isEmpty())
    }

    @Test
    fun `cancel entfernt den geplanten Alarm`() {
        scheduler.schedule(eventId = 9L, title = "Termin", triggerAt = Instant.now().plus(Duration.ofDays(1)))

        scheduler.cancel(9L)

        assertTrue(alarms().isEmpty())
    }

    @Test
    fun `Snooze-Alarm liegt neben dem regulaeren Alarm derselben ID`() {
        val next = Instant.now().plus(Duration.ofDays(7))
        val snooze = Instant.now().plus(Duration.ofMinutes(10))

        scheduler.schedule(eventId = 5L, title = "Serie", triggerAt = next)
        scheduler.schedule(eventId = 5L, title = "Serie", triggerAt = snooze, snooze = true)

        assertEquals(2, alarms().size)
        assertEquals(
            setOf(next.toEpochMilli(), snooze.toEpochMilli()),
            alarms().map { it.triggerAtMs }.toSet()
        )
    }

    @Test
    fun `cancel entfernt auch einen laufenden Snooze`() {
        scheduler.schedule(eventId = 5L, title = "Serie", triggerAt = Instant.now().plus(Duration.ofDays(7)))
        scheduler.schedule(
            eventId = 5L,
            title = "Serie",
            triggerAt = Instant.now().plus(Duration.ofMinutes(10)),
            snooze = true
        )

        scheduler.cancel(5L)

        assertTrue(alarms().isEmpty())
    }
}
