package de.transio.hiuni.feature.bib.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.LocalTime

/**
 * Persistenz-Tests für [BibFavoritesStore] auf einem echten, dateibasierten
 * Preferences-DataStore (Muster wie NotificationSettingsStoreTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BibFavoritesStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var file: java.io.File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: BibFavoritesStore

    @Before
    fun setUp() {
        file = tmp.newFile("bib_favorites.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher),
            produceFile = { file }
        )
        store = BibFavoritesStore(dataStore)
    }

    @Test
    fun `ohne Eintrag gibt es keine Favoriten`() = runTest(dispatcher) {
        assertTrue(store.favorites.first().isEmpty())
        assertTrue(store.currentFavorites().isEmpty())
    }

    @Test
    fun `toggle markiert und entfernt einen Raum`() = runTest(dispatcher) {
        assertTrue(store.toggle(102))
        assertEquals(setOf(102), store.favorites.first())

        assertTrue(store.toggle(105))
        assertEquals(setOf(102, 105), store.currentFavorites())

        assertFalse(store.toggle(102))
        assertEquals(setOf(105), store.currentFavorites())
    }

    @Test
    fun `Favoriten ueberleben einen neuen Store auf derselben Datei`() = runTest(dispatcher) {
        store.toggle(101)
        store.toggle(103)

        // Gleicher DataStore = gleiche Datei; ein neuer Store darauf sieht den Stand.
        val reopened = BibFavoritesStore(dataStore)

        assertEquals(setOf(101, 103), reopened.currentFavorites())
    }

    @Test
    fun `Baseline wird gespeichert und gelesen`() = runTest(dispatcher) {
        val keys = setOf(
            BibFreeSlotNotifier.slotKey(102, LocalDate.of(2026, 10, 26), LocalTime.of(14, 0)),
            BibFreeSlotNotifier.slotKey(102, LocalDate.of(2026, 10, 26), LocalTime.of(14, 30))
        )
        assertTrue(store.bookedBaseline().isEmpty())

        store.setBookedBaseline(keys)

        assertEquals(keys, store.bookedBaseline())
    }

    @Test
    fun `Entfernen eines Favoriten verwirft dessen Baseline`() = runTest(dispatcher) {
        val date = LocalDate.of(2026, 10, 26)
        store.toggle(101)
        store.toggle(102)
        store.setBookedBaseline(
            setOf(
                BibFreeSlotNotifier.slotKey(101, date, LocalTime.of(10, 0)),
                BibFreeSlotNotifier.slotKey(102, date, LocalTime.of(14, 0))
            )
        )

        store.toggle(102)

        assertEquals(
            setOf(BibFreeSlotNotifier.slotKey(101, date, LocalTime.of(10, 0))),
            store.bookedBaseline()
        )
    }
}
