package de.transio.hiuni.feature.email.data

import android.content.Context
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationActionSpec
import de.transio.hiuni.core.notifications.NotificationPresenter
import de.transio.hiuni.core.notifications.data.NotificationKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant

/**
 * Neue ungelesene Mails müssen eine echte System-Benachrichtigung auslösen
 * (über den [NotificationPresenter]), nicht nur einen stillen Eintrag im
 * Mitteilungs-Center. Der Erst-Sync nach Login bleibt still.
 */
class EmailRepositoryNotificationTest {

    private val dao = mockk<EmailDao>(relaxed = true)
    private val imap = mockk<ImapClient>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val presenter = mockk<NotificationPresenter>(relaxed = true)

    private fun repo() = EmailRepositoryImpl(
        dao = dao,
        imap = imap,
        smtp = mockk(relaxed = true),
        settings = settings,
        presenter = presenter,
        context = mockk<Context>(relaxed = true),
        io = Dispatchers.Unconfined,
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    )

    private fun header(uid: Long, read: Boolean = false) = ImapHeaders(
        uid = uid, fromAddress = "kjell@example.org", fromName = "Kjell", subject = "Test $uid",
        snippet = "", receivedAt = Instant.EPOCH, isRead = read, isStarred = false,
        toAddresses = emptyList(), ccAddresses = emptyList(), bccAddresses = emptyList(),
        hasAttachments = false, hasCalendarInvite = false, messageId = null, referencesHeader = null
    )

    init {
        every { settings.lastEmailSyncEpoch } returns flowOf(0L)
        every { settings.mailRequiresBiometric } returns flowOf(false)
    }

    @Test
    fun `neue ungelesene Mail zeigt eine System-Benachrichtigung`() = runBlocking {
        coEvery { dao.knownUids(EmailEntity.FOLDER_INBOX) } returns listOf(1L)
        coEvery { imap.fetchHeaders(any(), any(), any(), "INBOX") } returns listOf(header(1), header(2))

        repo().refresh(force = true)

        coVerify(exactly = 1) {
            presenter.present(kind = NotificationKind.MAIL, title = "Neue E-Mail", body = any(), refKey = any(), systemId = any(), actions = any())
        }
    }

    @Test
    fun `Erst-Sync nach Login bleibt still`() = runBlocking {
        coEvery { dao.knownUids(EmailEntity.FOLDER_INBOX) } returns emptyList()
        coEvery { imap.fetchHeaders(any(), any(), any(), "INBOX") } returns listOf(header(1), header(2))

        repo().refresh(force = true)

        coVerify(exactly = 0) { presenter.present(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `bereits gelesene neue Mail bleibt still`() = runBlocking {
        coEvery { dao.knownUids(EmailEntity.FOLDER_INBOX) } returns listOf(1L)
        coEvery { imap.fetchHeaders(any(), any(), any(), "INBOX") } returns listOf(header(1), header(2, read = true))

        repo().refresh(force = true)

        coVerify(exactly = 0) { presenter.present(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `eine neue Mail bekommt Als gelesen und Archivieren fuer ihre UID`() = runBlocking {
        coEvery { dao.knownUids(EmailEntity.FOLDER_INBOX) } returns listOf(1L)
        coEvery { imap.fetchHeaders(any(), any(), any(), "INBOX") } returns listOf(header(1), header(2))

        repo().refresh(force = true)

        coVerify(exactly = 1) {
            presenter.present(
                kind = NotificationKind.MAIL,
                title = "Neue E-Mail",
                body = any(),
                refKey = any(),
                systemId = any(),
                actions = listOf(
                    NotificationActionSpec.MarkMailsRead(listOf(2L)),
                    NotificationActionSpec.ArchiveMail(2L)
                )
            )
        }
    }

    @Test
    fun `mehrere neue Mails bekommen Alle als gelesen fuer genau diese UIDs`() = runBlocking {
        coEvery { dao.knownUids(EmailEntity.FOLDER_INBOX) } returns listOf(1L)
        coEvery { imap.fetchHeaders(any(), any(), any(), "INBOX") } returns
            listOf(header(1), header(2), header(3), header(4, read = true))

        repo().refresh(force = true)

        coVerify(exactly = 1) {
            presenter.present(
                kind = NotificationKind.MAIL,
                title = "2 neue E-Mails",
                body = any(),
                refKey = any(),
                systemId = any(),
                actions = listOf(NotificationActionSpec.MarkMailsRead(listOf(2L, 3L)))
            )
        }
    }

    @Test
    fun `mit aktiver Mail-Sperre kommt die Meldung ohne Aktionen`() = runBlocking {
        every { settings.mailRequiresBiometric } returns flowOf(true)
        coEvery { dao.knownUids(EmailEntity.FOLDER_INBOX) } returns listOf(1L)
        coEvery { imap.fetchHeaders(any(), any(), any(), "INBOX") } returns listOf(header(1), header(2))

        repo().refresh(force = true)

        coVerify(exactly = 1) {
            presenter.present(
                kind = NotificationKind.MAIL,
                title = "Neue E-Mail",
                body = any(),
                refKey = any(),
                systemId = any(),
                actions = emptyList()
            )
        }
    }
}
