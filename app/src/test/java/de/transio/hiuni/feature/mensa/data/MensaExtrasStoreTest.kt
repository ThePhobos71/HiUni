package de.transio.hiuni.feature.mensa.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.LocalTime

/**
 * [MensaExtrasStore] hält Hinweise und Öffnungszeiten über Prozess-Neustarts hinweg,
 * damit der Mensa-Screen sie ab dem ersten Frame zeigt statt nach dem Netz-Refresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MensaExtrasStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: MensaExtrasStore

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher),
            produceFile = { tmp.newFile("mensa_extras.preferences_pb") }
        )
        store = MensaExtrasStore(dataStore)
    }

    @Test
    fun `ohne gespeicherte Daten kommen leere Listen`() = runTest(dispatcher) {
        assertTrue(store.observeAnnouncements(150).first().isEmpty())
        assertTrue(store.observeOpeningHours(150).first().isEmpty())
    }

    @Test
    fun `Hinweise ueberleben den Roundtrip pro Location`() = runTest(dispatcher) {
        val announcement = Announcement(
            date = LocalDate.of(2026, 10, 26),
            text = "Vom 26.10.2026 bis 30.10.2026 geschlossen",
            time = AnnouncementTime.NOON,
            lane = "Essen 1"
        )
        store.saveAnnouncements(150, listOf(announcement))

        assertEquals(listOf(announcement), store.observeAnnouncements(150).first())
        // Andere Location sieht die Hinweise nicht.
        assertTrue(store.observeAnnouncements(151).first().isEmpty())
    }

    @Test
    fun `neue Hinweise ersetzen die alten der Location`() = runTest(dispatcher) {
        val old = Announcement(LocalDate.of(2026, 10, 26), "Alt", AnnouncementTime.NOON, null)
        val new = Announcement(LocalDate.of(2026, 10, 27), "Neu", AnnouncementTime.EVENING, null)
        store.saveAnnouncements(150, listOf(old))
        store.saveAnnouncements(150, listOf(new))

        assertEquals(listOf(new), store.observeAnnouncements(150).first())
    }

    @Test
    fun `Oeffnungszeiten ueberleben den Roundtrip`() = runTest(dispatcher) {
        val blocks = listOf(
            OpeningHourBlock("noon", 1, 4, LocalTime.of(11, 30), LocalTime.of(14, 15)),
            OpeningHourBlock("noon", 5, 5, LocalTime.of(11, 30), LocalTime.of(14, 0))
        )
        store.saveOpeningHours(150, blocks)

        assertEquals(blocks, store.observeOpeningHours(150).first())
        assertTrue(store.observeOpeningHours(151).first().isEmpty())
    }

    @Test
    fun `leere Oeffnungszeiten ueberschreiben vorhandene nicht`() = runTest(dispatcher) {
        val blocks = listOf(
            OpeningHourBlock("noon", 1, 5, LocalTime.of(11, 30), LocalTime.of(14, 15))
        )
        store.saveOpeningHours(150, blocks)
        store.saveOpeningHours(150, emptyList())

        assertEquals(blocks, store.observeOpeningHours(150).first())
    }
}
