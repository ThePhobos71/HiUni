package de.transio.hiuni.core.sync

import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationPresenter
import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.feature.lsf.data.ExamEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Tests für die Diff-/Cancel-Logik von [ExamReminderScheduler].
 *
 * Reiner JVM-Test: [NotificationScheduler] (AlarmManager-Wrapper) und
 * [NotificationPresenter] werden gemockt, sodass wir die geplanten und
 * gecancelten Reminder-IDs verifizieren, ohne echten AlarmManager. Fokus liegt
 * auf dem Soll-Set (nur Zukunfts-Slots) vs. dem persistierten Set und dem
 * Canceln verwaister IDs.
 */
class ExamReminderSchedulerTest {

    private val scheduler = mockk<NotificationScheduler>(relaxed = true)
    private val presenter = mockk<NotificationPresenter>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)

    private fun newScheduler() = ExamReminderScheduler(scheduler, presenter, settings)

    private fun exam(
        rowId: Long,
        examDate: LocalDate?,
        veranstaltungsNumber: String = rowId.toString(),
        rooms: List<String> = listOf("SC.A.0.09")
    ) = ExamEntity(
        rowId = rowId,
        veranstaltungsNumber = veranstaltungsNumber,
        pruefungstext = "Prüfung $rowId",
        moduleName = "Modul $rowId",
        parentModule = null,
        examDate = examDate,
        examTime = LocalTime.of(10, 0),
        rooms = rooms,
        semester = "SoSe 26",
        semesterCode = "20261",
        registrationDate = null,
        cancellationDeadline = null,
        pruefer = null,
        courseId = null
    )

    // reminderId-Schema aus der Produktion nachgebaut fürs Assert.
    private fun rid(rowId: Long, slot: Int) =
        ExamReminderScheduler.EXAM_ID_OFFSET + rowId * 10 + slot

    @Test
    fun `plant beide Slots fuer eine weit in der Zukunft liegende Klausur`() = runBlocking {
        every { settings.scheduledExamReminderIds } returns flowOf(emptySet())
        val far = LocalDate.now().plusDays(30)
        val idSlot = mutableListOf<Long>()
        every {
            scheduler.schedule(capture(idSlot), any(), any(), any(), any())
        } returns Unit

        newScheduler().syncReminders(allExams = listOf(exam(5, far)), newlyAdded = emptyList())

        // Slot 7d (0) und Slot 1d (1) für rowId=5.
        assertTrue(idSlot.contains(rid(5, ExamReminderScheduler.SLOT_7_DAYS)))
        assertTrue(idSlot.contains(rid(5, ExamReminderScheduler.SLOT_1_DAY)))
        // Persistiert exakt diese beiden IDs.
        val persisted = slot<Set<Long>>()
        coVerify { settings.setScheduledExamReminderIds(capture(persisted)) }
        assertEquals(
            setOf(rid(5, 0), rid(5, 1)),
            persisted.captured
        )
    }

    @Test
    fun `Klausur ohne Datum wird komplett uebersprungen`() = runBlocking {
        every { settings.scheduledExamReminderIds } returns flowOf(emptySet())

        newScheduler().syncReminders(allExams = listOf(exam(5, null)), newlyAdded = emptyList())

        verify(exactly = 0) { scheduler.schedule(any(), any(), any(), any(), any()) }
        val persisted = slot<Set<Long>>()
        coVerify { settings.setScheduledExamReminderIds(capture(persisted)) }
        assertTrue("kein Slot ohne Datum", persisted.captured.isEmpty())
    }

    @Test
    fun `bereits vergangene Klausur plant keine Slots und wird nicht persistiert`() = runBlocking {
        every { settings.scheduledExamReminderIds } returns flowOf(emptySet())
        val past = LocalDate.now().minusDays(3)

        newScheduler().syncReminders(allExams = listOf(exam(5, past)), newlyAdded = emptyList())

        verify(exactly = 0) { scheduler.schedule(any(), any(), any(), any(), any()) }
        val persisted = slot<Set<Long>>()
        coVerify { settings.setScheduledExamReminderIds(capture(persisted)) }
        assertTrue(persisted.captured.isEmpty())
    }

    @Test
    fun `nur der 1-Tag-Slot bleibt wenn die Klausur in 3 Tagen ist`() = runBlocking {
        every { settings.scheduledExamReminderIds } returns flowOf(emptySet())
        // In 3 Tagen: 7-Tage-Trigger liegt in der Vergangenheit → nur 1d-Slot.
        val soon = LocalDate.now().plusDays(3)
        val captured = mutableListOf<Long>()
        every { scheduler.schedule(capture(captured), any(), any(), any(), any()) } returns Unit

        newScheduler().syncReminders(allExams = listOf(exam(5, soon)), newlyAdded = emptyList())

        assertEquals(listOf(rid(5, ExamReminderScheduler.SLOT_1_DAY)), captured)
    }

    @Test
    fun `verwaiste IDs aus vorherigem Sync werden gecancelt`() = runBlocking {
        // Vorheriger Stand enthält Reminder für rowId 5 UND 99. Diesmal ist nur
        // rowId 5 in der Soll-Liste → die 99er-IDs müssen gecancelt werden.
        val prev = setOf(
            rid(5, 0), rid(5, 1),
            rid(99, 0), rid(99, 1)
        )
        every { settings.scheduledExamReminderIds } returns flowOf(prev)
        val far = LocalDate.now().plusDays(30)
        val canceled = mutableListOf<Long>()
        every { scheduler.cancel(capture(canceled)) } returns Unit

        newScheduler().syncReminders(allExams = listOf(exam(5, far)), newlyAdded = emptyList())

        assertEquals(
            setOf(rid(99, 0), rid(99, 1)),
            canceled.toSet()
        )
        // rowId 5 bleibt aktiv → NICHT gecancelt.
        assertTrue(!canceled.contains(rid(5, 0)))
        assertTrue(!canceled.contains(rid(5, 1)))
    }

    @Test
    fun `Klausur ohne Datum verwaist ihre vorher geplanten IDs und cancelt sie`() = runBlocking {
        // rowId 5 war terminiert und geplant; jetzt hat LSF das Datum entfernt.
        val prev = setOf(rid(5, 0), rid(5, 1))
        every { settings.scheduledExamReminderIds } returns flowOf(prev)
        val canceled = mutableListOf<Long>()
        every { scheduler.cancel(capture(canceled)) } returns Unit

        newScheduler().syncReminders(allExams = listOf(exam(5, null)), newlyAdded = emptyList())

        assertEquals(setOf(rid(5, 0), rid(5, 1)), canceled.toSet())
        val persisted = slot<Set<Long>>()
        coVerify { settings.setScheduledExamReminderIds(capture(persisted)) }
        assertTrue(persisted.captured.isEmpty())
    }

    @Test
    fun `kein Cancel wenn Soll-Set das persistierte Set unveraendert enthaelt`() = runBlocking {
        val far = LocalDate.now().plusDays(30)
        val prev = setOf(rid(5, 0), rid(5, 1))
        every { settings.scheduledExamReminderIds } returns flowOf(prev)

        newScheduler().syncReminders(allExams = listOf(exam(5, far)), newlyAdded = emptyList())

        verify(exactly = 0) { scheduler.cancel(any()) }
    }

    @Test
    fun `leeres persistiertes Set faellt sauber auf keine Cancels zurueck`() = runBlocking {
        // getOrElse-Fallback: Flow wirft → previousIds = emptySet → keine Cancels.
        every { settings.scheduledExamReminderIds } returns
            kotlinx.coroutines.flow.flow { throw RuntimeException("datastore kaputt") }
        val far = LocalDate.now().plusDays(30)

        newScheduler().syncReminders(allExams = listOf(exam(5, far)), newlyAdded = emptyList())

        verify(exactly = 0) { scheduler.cancel(any()) }
        // Schedule läuft trotzdem.
        verify(atLeast = 1) { scheduler.schedule(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `neu erkannte Klausur mit Datum loest Neue-Klausur-Push aus`() = runBlocking {
        every { settings.scheduledExamReminderIds } returns flowOf(emptySet())
        val far = LocalDate.now().plusDays(30)
        val e = exam(5, far)
        coEvery { presenter.present(any(), any(), any(), any(), any()) } returns Unit

        newScheduler().syncReminders(allExams = listOf(e), newlyAdded = listOf(e))

        coVerify(exactly = 1) {
            presenter.present(
                kind = NotificationKind.EXAM,
                title = "Neue Klausur eingetragen",
                body = any(),
                refKey = "exam:5",
                systemId = any()
            )
        }
    }

    @Test
    fun `neu erkannte Klausur OHNE Datum loest keinen Push aus`() = runBlocking {
        every { settings.scheduledExamReminderIds } returns flowOf(emptySet())
        val e = exam(5, null)

        newScheduler().syncReminders(allExams = listOf(e), newlyAdded = listOf(e))

        coVerify(exactly = 0) { presenter.present(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `reminderId-Schema bleibt im positiven Int-Bereich fuer realistische rowIds`() {
        // PendingIntent nutzt eventId.toInt(); rid muss für kleine rowIds positiv bleiben.
        val id = rid(1234, ExamReminderScheduler.SLOT_1_DAY)
        assertTrue(id > ExamReminderScheduler.EXAM_ID_OFFSET)
        assertTrue(id.toInt() > 0)
    }

    @Test
    fun `rowId der den Int-Bereich sprengt wird geskippt statt overflow-kollidiert`() = runBlocking {
        // Guard-Prüfung: idOffset(1e9) + rowId*10 muss unter Int.MAX_VALUE (2.147e9)
        // bleiben. Bei rowId=2e8 wäre die ID ~3e9 → würde nach .toInt() negativ
        // überlaufen und mit fremden IDs kollidieren. Erwartung: kein schedule,
        // nichts persistiert.
        every { settings.scheduledExamReminderIds } returns flowOf(emptySet())
        val far = LocalDate.now().plusDays(30)

        newScheduler().syncReminders(
            allExams = listOf(exam(200_000_000L, far)),
            newlyAdded = emptyList()
        )

        verify(exactly = 0) { scheduler.schedule(any(), any(), any(), any(), any()) }
        val persisted = slot<Set<Long>>()
        coVerify { settings.setScheduledExamReminderIds(capture(persisted)) }
        assertTrue("Overflow-IDs werden nicht persistiert", persisted.captured.isEmpty())
    }
}
