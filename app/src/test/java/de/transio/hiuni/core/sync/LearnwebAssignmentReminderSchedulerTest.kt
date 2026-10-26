package de.transio.hiuni.core.sync

import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.feature.learnweb.data.LearnwebAssignment
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Tests für [LearnwebAssignmentReminderScheduler]. Reiner JVM-Test:
 * [NotificationScheduler] (AlarmManager-Wrapper) wird gemockt, sodass die
 * geplanten Reminder inklusive [NotificationKind] verifiziert werden können.
 */
class LearnwebAssignmentReminderSchedulerTest {

    private val scheduler = mockk<NotificationScheduler>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)

    private fun newScheduler() = LearnwebAssignmentReminderScheduler(scheduler, settings)

    private fun assignment(
        rowId: Long,
        dueEpoch: Long,
        submissionStatus: String = LearnwebAssignment.STATUS_NOT_SUBMITTED
    ) = LearnwebAssignment(
        rowId = rowId,
        eventId = rowId,
        title = "Aufgabe $rowId ist fällig.",
        dueEpoch = dueEpoch,
        url = "https://learnweb.example/mod/assign/$rowId",
        syncedAt = Instant.now().toEpochMilli(),
        submissionStatus = submissionStatus
    )

    @Test
    fun `Learnweb-Reminder werden mit Kind LEARNWEB geplant`() = runBlocking {
        every { settings.scheduledLearnwebReminderIds } returns flowOf(emptySet())
        // Fällig in 5 Tagen → 3d-Slot und 1d-Slot liegen in der Zukunft.
        val due = Instant.now().plusSeconds(5L * 24 * 60 * 60).toEpochMilli()
        val kinds = mutableListOf<NotificationKind>()
        every {
            scheduler.schedule(any(), any(), any(), capture(kinds), any())
        } returns Unit

        newScheduler().syncReminders(listOf(assignment(1, due)))

        assertTrue("mindestens ein Reminder geplant", kinds.isNotEmpty())
        // Ausschließlich LEARNWEB — keine Kalender-EVENT-Reminder mehr.
        assertTrue(
            "alle Learnweb-Reminder tragen Kind LEARNWEB",
            kinds.all { it == NotificationKind.LEARNWEB }
        )
    }

    /**
     * Regressionsschutz zur Kalender-Dedup (Scraper vs. iCal-Feed): Reminder
     * hängen ausschließlich an `learnweb_assignments` (ein Eintrag pro Moodle-
     * Event-ID), nicht an den `custom_events`-Spiegelungen. Eine Abgabe ergibt
     * also pro Slot genau einen Alarm, unabhängig davon, ob sie im Kalender
     * als Scraper- oder Feed-Eintrag steht.
     */
    @Test
    fun `eine Abgabe plant pro Slot genau einen Reminder`() = runBlocking {
        every { settings.scheduledLearnwebReminderIds } returns flowOf(emptySet())
        val due = Instant.now().plusSeconds(5L * 24 * 60 * 60).toEpochMilli()
        val ids = mutableListOf<Long>()
        every {
            scheduler.schedule(capture(ids), any(), any(), any(), any())
        } returns Unit

        newScheduler().syncReminders(listOf(assignment(1, due)))

        assertEquals("3 Slots (3 Tage, 1 Tag, 2 Stunden)", 3, ids.size)
        assertEquals("keine doppelt geplante Reminder-ID", ids.size, ids.toSet().size)
    }

    @Test
    fun `bereits abgegebene Aufgabe plant keine Reminder`() = runBlocking {
        every { settings.scheduledLearnwebReminderIds } returns flowOf(emptySet())
        val due = Instant.now().plusSeconds(5L * 24 * 60 * 60).toEpochMilli()

        newScheduler().syncReminders(
            listOf(assignment(1, due, submissionStatus = LearnwebAssignment.STATUS_SUBMITTED))
        )

        verify(exactly = 0) { scheduler.schedule(any(), any(), any(), any(), any()) }
        val persisted = slot<Set<Long>>()
        io.mockk.coVerify { settings.setScheduledLearnwebReminderIds(capture(persisted)) }
        assertEquals(emptySet<Long>(), persisted.captured)
    }
}
