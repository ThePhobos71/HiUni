package de.transio.hiuni.feature.email.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Persistenz der offenen IMAP-Flag-Änderungen ([PendingMailFlagStore]) auf einem
 * echten, dateibasierten Preferences-DataStore.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingMailFlagStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: PendingMailFlagStore

    @Before
    fun setUp() {
        val file = tmp.newFile("mail_flags.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher),
            produceFile = { file }
        )
        store = PendingMailFlagStore(dataStore)
    }

    @Test
    fun `ohne Eintrag ist die Warteschlange leer`() = runTest(dispatcher) {
        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun `eingereihte Aenderung bleibt erhalten`() = runTest(dispatcher) {
        val change = PendingMailFlag(MailFlag.SEEN, EmailEntity.FOLDER_INBOX, 42L, true)
        store.enqueue(change)
        assertEquals(listOf(change), store.pending())
    }

    @Test
    fun `neuer Zustand fuer dieselbe Mail ersetzt den alten`() = runTest(dispatcher) {
        store.enqueue(PendingMailFlag(MailFlag.SEEN, EmailEntity.FOLDER_INBOX, 42L, true))
        store.enqueue(PendingMailFlag(MailFlag.SEEN, EmailEntity.FOLDER_INBOX, 42L, false))
        assertEquals(
            listOf(PendingMailFlag(MailFlag.SEEN, EmailEntity.FOLDER_INBOX, 42L, false)),
            store.pending()
        )
    }

    @Test
    fun `Gelesen und Markiert derselben Mail stehen getrennt in der Warteschlange`() = runTest(dispatcher) {
        store.enqueue(PendingMailFlag(MailFlag.SEEN, EmailEntity.FOLDER_INBOX, 42L, true))
        store.enqueue(PendingMailFlag(MailFlag.FLAGGED, EmailEntity.FOLDER_INBOX, 42L, true))
        assertEquals(2, store.pending().size)
    }

    @Test
    fun `remove entfernt nur den exakt gleichen Zustand`() = runTest(dispatcher) {
        val read = PendingMailFlag(MailFlag.SEEN, EmailEntity.FOLDER_INBOX, 42L, true)
        val unread = read.copy(value = false)
        store.enqueue(unread)
        // Server hat noch den alten Zustand bestätigt: der neuere bleibt offen.
        store.remove(read)
        assertEquals(listOf(unread), store.pending())
        store.remove(unread)
        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun `Kodierung ist umkehrbar und ignoriert kaputte Eintraege`() {
        val change = PendingMailFlag(MailFlag.FLAGGED, EmailEntity.FOLDER_SENT, 7L, false)
        assertEquals(change, PendingMailFlag.decode(change.encode()))
        assertNull(PendingMailFlag.decode("kaputt"))
        assertNull(PendingMailFlag.decode("SEEN|INBOX|abc|1"))
        assertNull(PendingMailFlag.decode("UNBEKANNT|INBOX|1|1"))
    }
}
