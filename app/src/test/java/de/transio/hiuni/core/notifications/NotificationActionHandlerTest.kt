package de.transio.hiuni.core.notifications

import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationActionSpec.ArchiveMail
import de.transio.hiuni.core.notifications.NotificationActionSpec.CompleteTodo
import de.transio.hiuni.core.notifications.NotificationActionSpec.MarkMailsRead
import de.transio.hiuni.core.notifications.NotificationActionSpec.SnoozeEvent
import de.transio.hiuni.core.notifications.NotificationActionSpec.SnoozeTodo
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.feature.email.data.EmailEntity
import de.transio.hiuni.feature.email.data.EmailRepository
import de.transio.hiuni.feature.todos.data.TodoReminderScheduler
import de.transio.hiuni.feature.todos.data.TodosRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Dispatch der Notification-Aktionen auf die bestehenden Repository-Pfade. Fehler
 * werden zu [NotificationActionOutcome.Failed], nie zu einer Exception.
 */
class NotificationActionHandlerTest {

    private val now = Instant.parse("2026-10-26T08:00:00Z")
    private val emails = mockk<EmailRepository>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val todos = mockk<TodosRepository>(relaxed = true)
    private val todoReminders = mockk<TodoReminderScheduler>(relaxed = true)
    private val scheduler = mockk<NotificationScheduler>(relaxed = true)

    private val handler = NotificationActionHandler(
        emailRepository = emails,
        settings = settings,
        todosRepository = todos,
        todoReminders = todoReminders,
        scheduler = scheduler,
        bibBooker = mockk(relaxed = true),
        clock = Clock.fixed(now, ZoneOffset.UTC)
    )

    init {
        every { settings.mailRequiresBiometric } returns flowOf(false)
    }

    private fun mail(uid: Long, rowId: Long) = EmailEntity(
        rowId = rowId, uid = uid, folder = EmailEntity.FOLDER_INBOX,
        fromAddress = "a@b.de", fromName = null, subject = "S", snippet = "",
        bodyPlain = null, bodyHtml = null, attachmentsJson = null,
        receivedAt = Instant.EPOCH
    )

    @Test
    fun `Als gelesen markiert jede noch vorhandene Mail ueber ihre rowId`() = runBlocking {
        coEvery { emails.findInboxMail(10L) } returns mail(10L, rowId = 100L)
        coEvery { emails.findInboxMail(11L) } returns null
        coEvery { emails.findInboxMail(12L) } returns mail(12L, rowId = 120L)

        val outcome = handler.handle(MarkMailsRead(listOf(10L, 11L, 12L)))

        assertEquals(NotificationActionOutcome.Done, outcome)
        coVerify { emails.markRead(100L, true) }
        coVerify { emails.markRead(120L, true) }
        coVerify(exactly = 2) { emails.markRead(any(), any()) }
    }

    @Test
    fun `Archivieren nutzt archiveEmail des Repositorys`() = runBlocking {
        val m = mail(10L, rowId = 100L)
        coEvery { emails.findInboxMail(10L) } returns m
        coEvery { emails.archiveEmail(m) } returns AppResult.Success(Unit)

        val outcome = handler.handle(ArchiveMail(10L))

        assertEquals(NotificationActionOutcome.Done, outcome)
        coVerify(exactly = 1) { emails.archiveEmail(m) }
    }

    @Test
    fun `Archivieren meldet Server-Fehler als Failed`() = runBlocking {
        val m = mail(10L, rowId = 100L)
        coEvery { emails.findInboxMail(10L) } returns m
        coEvery { emails.archiveEmail(m) } returns AppResult.Failure(IllegalStateException("kein Archiv"))

        val outcome = handler.handle(ArchiveMail(10L))

        assertTrue(outcome is NotificationActionOutcome.Failed)
    }

    @Test
    fun `Archivieren einer nicht mehr vorhandenen Mail ist ein No-op`() = runBlocking {
        coEvery { emails.findInboxMail(10L) } returns null

        val outcome = handler.handle(ArchiveMail(10L))

        assertEquals(NotificationActionOutcome.Done, outcome)
        coVerify(exactly = 0) { emails.archiveEmail(any()) }
    }

    @Test
    fun `aktive Mail-Sperre blockt Mail-Aktionen ohne Schreibzugriff`() = runBlocking {
        every { settings.mailRequiresBiometric } returns flowOf(true)

        val read = handler.handle(MarkMailsRead(listOf(10L)))
        val archive = handler.handle(ArchiveMail(10L))

        assertTrue(read is NotificationActionOutcome.Failed)
        assertTrue(archive is NotificationActionOutcome.Failed)
        coVerify(exactly = 0) { emails.markRead(any(), any()) }
        coVerify(exactly = 0) { emails.archiveEmail(any()) }
    }

    @Test
    fun `Exceptions im Repository werden zu Failed statt Crash`() = runBlocking {
        coEvery { emails.findInboxMail(any()) } throws IllegalStateException("DB zu")

        val outcome = handler.handle(MarkMailsRead(listOf(1L)))

        assertTrue(outcome is NotificationActionOutcome.Failed)
    }

    @Test
    fun `Erledigt hakt die Aufgabe ueber das TodosRepository ab`() = runBlocking {
        val outcome = handler.handle(CompleteTodo(7L))

        assertEquals(NotificationActionOutcome.Done, outcome)
        coVerify(exactly = 1) { todos.setDone(7L, true, now) }
    }

    @Test
    fun `Spaeter bei Aufgabe plant die Erinnerung eine Stunde spaeter`() = runBlocking {
        coEvery { todoReminders.snooze(7L, any()) } returns true

        val outcome = handler.handle(SnoozeTodo(7L))

        assertEquals(NotificationActionOutcome.Done, outcome)
        coVerify(exactly = 1) { todoReminders.snooze(7L, now.plus(Duration.ofHours(1))) }
    }

    @Test
    fun `Spaeter bei Termin plant einen Snooze-Alarm in zehn Minuten`() = runBlocking {
        val outcome = handler.handle(SnoozeEvent(12L, "Mathe 2", "Gleich"))

        assertEquals(NotificationActionOutcome.Done, outcome)
        verify(exactly = 1) {
            scheduler.schedule(
                eventId = 12L,
                title = "Mathe 2",
                triggerAt = now.plus(Duration.ofMinutes(10)),
                kind = NotificationKind.EVENT,
                body = "Gleich",
                snooze = true
            )
        }
    }
}
