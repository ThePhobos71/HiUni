package de.transio.hiuni.feature.email.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.transio.hiuni.core.datastore.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.Instant

/**
 * Gelesen/Ungelesen und Markiert müssen auch auf dem IMAP-Server landen, nicht nur
 * in Room. Lokal wird sofort (optimistisch) aktualisiert; scheitert der Server, bleibt
 * die Änderung in [PendingMailFlagStore] und wird beim nächsten erfolgreichen
 * `refresh()` nachgereicht.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmailRepositoryFlagSyncTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val dao = mockk<EmailDao>(relaxed = true)
    private val imap = mockk<ImapClient>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private lateinit var flagStore: PendingMailFlagStore
    private lateinit var repo: EmailRepositoryImpl

    private val inboxMail = EmailEntity(
        rowId = 7L, uid = 42L, folder = EmailEntity.FOLDER_INBOX,
        fromAddress = "kjell@example.org", fromName = "Kjell", subject = "Test",
        snippet = "", bodyPlain = null, receivedAt = Instant.EPOCH
    )

    @Before
    fun setUp() {
        val file = tmp.newFile("mail_flags.preferences_pb")
        flagStore = PendingMailFlagStore(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher), produceFile = { file })
        )
        every { settings.lastEmailSyncEpoch } returns flowOf(0L)
        every { settings.mailRequiresBiometric } returns flowOf(false)
        coEvery { dao.findByRowId(7L) } returns inboxMail
        repo = EmailRepositoryImpl(
            dao = dao,
            imap = imap,
            smtp = mockk(relaxed = true),
            settings = settings,
            presenter = mockk(relaxed = true),
            flagStore = flagStore,
            context = mockk<Context>(relaxed = true),
            io = dispatcher,
            appScope = CoroutineScope(SupervisorJob() + dispatcher)
        )
    }

    private fun header(uid: Long, read: Boolean) = ImapHeaders(
        uid = uid, fromAddress = "kjell@example.org", fromName = "Kjell", subject = "Test $uid",
        snippet = "", receivedAt = Instant.EPOCH, isRead = read, isStarred = false,
        toAddresses = emptyList(), ccAddresses = emptyList(), bccAddresses = emptyList(),
        hasAttachments = false, hasCalendarInvite = false, messageId = null, referencesHeader = null
    )

    private fun stubServer(flag: MailFlag, value: Boolean, answer: () -> Boolean) {
        coEvery { imap.setFlag(42L, flag, value, "INBOX", any(), any()) } answers { answer() }
    }

    @Test
    fun `als gelesen markieren setzt erst lokal, dann auf dem Server Seen`() = runTest(dispatcher) {
        stubServer(MailFlag.SEEN, true) { true }

        repo.markRead(7L, true)
        advanceUntilIdle()

        coVerifyOrder {
            dao.setRead(7L, true)
            imap.setFlag(42L, MailFlag.SEEN, true, "INBOX", any(), any())
        }
        assertTrue(flagStore.pending().isEmpty())
    }

    @Test
    fun `als ungelesen markieren nimmt Seen auf dem Server zurueck`() = runTest(dispatcher) {
        stubServer(MailFlag.SEEN, false) { true }

        repo.markRead(7L, false)
        advanceUntilIdle()

        coVerify(exactly = 1) { dao.setRead(7L, false) }
        coVerify(exactly = 1) { imap.setFlag(42L, MailFlag.SEEN, false, "INBOX", any(), any()) }
        assertTrue(flagStore.pending().isEmpty())
    }

    @Test
    fun `Serverfehler behaelt lokalen Zustand und reiht die Aenderung ein`() = runTest(dispatcher) {
        stubServer(MailFlag.SEEN, true) { throw IOException("offline") }

        repo.markRead(7L, true)
        advanceUntilIdle()

        coVerify(exactly = 1) { dao.setRead(7L, true) }
        coVerify(exactly = 0) { dao.setRead(7L, false) }
        assertEquals(
            listOf(PendingMailFlag(MailFlag.SEEN, EmailEntity.FOLDER_INBOX, 42L, true)),
            flagStore.pending()
        )
    }

    @Test
    fun `naechster erfolgreicher refresh reicht die Aenderung nach und leert die Warteschlange`() =
        runTest(dispatcher) {
            var online = false
            stubServer(MailFlag.SEEN, true) { if (online) true else throw IOException("offline") }
            repo.markRead(7L, true)
            advanceUntilIdle()
            assertEquals(1, flagStore.pending().size)

            online = true
            coEvery { dao.knownUids(EmailEntity.FOLDER_INBOX) } returns listOf(42L)
            coEvery { imap.fetchHeaders(any(), any(), any(), "INBOX") } returns listOf(header(42L, read = false))
            repo.refresh(force = true)
            advanceUntilIdle()

            coVerify(exactly = 2) { imap.setFlag(42L, MailFlag.SEEN, true, "INBOX", any(), any()) }
            assertTrue(flagStore.pending().isEmpty())
        }

    @Test
    fun `refresh ueberschreibt den lokalen Lesestatus nicht mit dem alten Serverstand`() =
        runTest(dispatcher) {
            stubServer(MailFlag.SEEN, true) { throw IOException("offline") }
            repo.markRead(7L, true)
            advanceUntilIdle()

            // Server kennt die Mail noch als ungelesen und ist für Flags weiter nicht
            // erreichbar: die bestehende Zeile darf nicht neu eingefügt werden.
            coEvery { dao.knownUids(EmailEntity.FOLDER_INBOX) } returns listOf(42L)
            coEvery { imap.fetchHeaders(any(), any(), any(), "INBOX") } returns listOf(header(42L, read = false))
            repo.refresh(force = true)
            advanceUntilIdle()

            coVerify(exactly = 0) { dao.upsert(any()) }
            coVerify(exactly = 0) { dao.setRead(any(), false) }
            assertEquals(1, flagStore.pending().size)
        }

    @Test
    fun `fehlgeschlagener refresh laesst die Warteschlange stehen`() = runTest(dispatcher) {
        stubServer(MailFlag.SEEN, true) { throw IOException("offline") }
        repo.markRead(7L, true)
        advanceUntilIdle()

        coEvery { imap.fetchHeaders(any(), any(), any(), "INBOX") } throws IOException("offline")
        repo.refresh(force = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { imap.setFlag(any(), any(), any(), any(), any(), any()) }
        assertEquals(1, flagStore.pending().size)
    }

    @Test
    fun `offline erst gelesen, dann ungelesen, nur der letzte Zustand geht an den Server`() =
        runTest(dispatcher) {
            var online = false
            coEvery { imap.setFlag(42L, MailFlag.SEEN, any(), "INBOX", any(), any()) } answers {
                if (online) true else throw IOException("offline")
            }
            repo.markRead(7L, true)
            advanceUntilIdle()
            repo.markRead(7L, false)
            advanceUntilIdle()
            assertEquals(
                listOf(PendingMailFlag(MailFlag.SEEN, EmailEntity.FOLDER_INBOX, 42L, false)),
                flagStore.pending()
            )

            online = true
            coEvery { dao.knownUids(EmailEntity.FOLDER_INBOX) } returns listOf(42L)
            coEvery { imap.fetchHeaders(any(), any(), any(), "INBOX") } returns listOf(header(42L, read = false))
            repo.refresh(force = true)
            advanceUntilIdle()

            // Je ein gescheiterter Offline-Versuch pro Klick, danach nur noch „ungelesen".
            coVerify(exactly = 1) { imap.setFlag(42L, MailFlag.SEEN, true, "INBOX", any(), any()) }
            coVerify(exactly = 2) { imap.setFlag(42L, MailFlag.SEEN, false, "INBOX", any(), any()) }
            assertTrue(flagStore.pending().isEmpty())
        }

    @Test
    fun `Mail nicht mehr auf dem Server wird aus der Warteschlange verworfen`() = runTest(dispatcher) {
        stubServer(MailFlag.SEEN, true) { false }

        repo.markRead(7L, true)
        advanceUntilIdle()

        assertTrue(flagStore.pending().isEmpty())
    }

    @Test
    fun `Stern setzt Flagged auf dem Server`() = runTest(dispatcher) {
        stubServer(MailFlag.FLAGGED, true) { true }

        repo.toggleStar(inboxMail)
        advanceUntilIdle()

        coVerifyOrder {
            dao.setStarred(7L, true)
            imap.setFlag(42L, MailFlag.FLAGGED, true, "INBOX", any(), any())
        }
        assertTrue(flagStore.pending().isEmpty())
    }

    @Test
    fun `Stern entfernen bei Serverfehler wird eingereiht`() = runTest(dispatcher) {
        stubServer(MailFlag.FLAGGED, false) { throw IOException("offline") }

        repo.toggleStar(inboxMail.copy(isStarred = true))
        advanceUntilIdle()

        coVerify(exactly = 1) { dao.setStarred(7L, false) }
        assertEquals(
            listOf(PendingMailFlag(MailFlag.FLAGGED, EmailEntity.FOLDER_INBOX, 42L, false)),
            flagStore.pending()
        )
    }

    @Test
    fun `archivierte Mail wird nur lokal markiert`() = runTest(dispatcher) {
        // Nach dem IMAP-MOVE hat die Mail im Archiv eine neue UID; die gespeicherte
        // stammt aus der INBOX und könnte im Archiv eine fremde Mail treffen.
        coEvery { dao.findByRowId(7L) } returns inboxMail.copy(folder = EmailEntity.FOLDER_ARCHIVE)

        repo.markRead(7L, true)
        advanceUntilIdle()

        coVerify(exactly = 1) { dao.setRead(7L, true) }
        coVerify(exactly = 0) { imap.setFlag(any(), any(), any(), any(), any(), any()) }
        assertTrue(flagStore.pending().isEmpty())
    }
}
