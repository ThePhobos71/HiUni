package de.transio.hiuni.feature.mensa.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

/**
 * Persistenz-Tests für [FavoriteMealStore] auf einem echten, dateibasierten
 * Preferences-DataStore (Muster wie BibFavoritesStoreTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteMealStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: FavoriteMealStore

    @Before
    fun setUp() {
        val file = tmp.newFile("favorite_meals.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher),
            produceFile = { file }
        )
        store = FavoriteMealStore(dataStore)
    }

    @Test
    fun `hinzufuegen trimmt und behaelt Reihenfolge`() = runTest(dispatcher) {
        assertTrue(store.addKeyword("  Currywurst "))
        assertTrue(store.addKeyword("Lasagne"))
        assertEquals(listOf("Currywurst", "Lasagne"), store.currentKeywords())
        assertEquals(listOf("Currywurst", "Lasagne"), store.observeKeywords().first())
    }

    @Test
    fun `mehrfache Leerzeichen werden zu einem`() = runTest(dispatcher) {
        store.addKeyword("Lasagne    Bolognese")
        assertEquals(listOf("Lasagne Bolognese"), store.currentKeywords())
    }

    @Test
    fun `zu kurz, Duplikat und zu lang werden abgelehnt`() = runTest(dispatcher) {
        assertFalse(store.addKeyword("ei"))
        store.addKeyword("Currywurst")
        assertFalse(store.addKeyword("currywurst"))
        assertFalse(store.addKeyword("x".repeat(41)))
        assertEquals(listOf("Currywurst"), store.currentKeywords())
    }

    @Test
    fun `mehr als 20 Stichwoerter werden abgelehnt`() = runTest(dispatcher) {
        repeat(20) { assertTrue(store.addKeyword("Gericht $it")) }
        assertFalse(store.addKeyword("Noch eins"))
    }

    @Test
    fun `entfernen loescht ohne Beachtung der Grossschreibung`() = runTest(dispatcher) {
        store.addKeyword("Currywurst")
        store.removeKeyword("CURRYWURST")
        assertTrue(store.currentKeywords().isEmpty())
    }

    @Test
    fun `kaputtes JSON gilt als leere Liste`() = runTest(dispatcher) {
        dataStore.edit { it[stringPreferencesKey("mensa_favorite_keywords")] = "{kaputt" }
        assertTrue(store.currentKeywords().isEmpty())
        assertTrue(store.addKeyword("Currywurst"))
        assertEquals(listOf("Currywurst"), store.currentKeywords())
    }

    @Test
    fun `gemeldete Schluessel werden ersetzt`() = runTest(dispatcher) {
        store.setNotifiedKeys(setOf("a"))
        store.setNotifiedKeys(setOf("b"))
        assertEquals(setOf("b"), store.notifiedKeys())
    }
}
