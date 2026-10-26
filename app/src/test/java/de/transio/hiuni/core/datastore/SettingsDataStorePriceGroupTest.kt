package de.transio.hiuni.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Persistenz der Mensa-Preisgruppe im echten (dateibasierten) Preferences-DataStore. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDataStorePriceGroupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SettingsDataStore

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher),
            produceFile = { tmp.newFile("settings.preferences_pb") }
        )
        store = SettingsDataStore(dataStore)
    }

    @Test
    fun `ohne gespeicherte Wahl liefert der Store den Automatik-Key`() = runTest(dispatcher) {
        assertEquals(SettingsDataStore.DEFAULT_MENSA_PRICE_GROUP, store.mensaPriceGroup.first())
        assertEquals("auto", SettingsDataStore.DEFAULT_MENSA_PRICE_GROUP)
    }

    @Test
    fun `gesetzte Preisgruppe wird persistiert`() = runTest(dispatcher) {
        store.setMensaPriceGroup("guest")
        assertEquals("guest", store.mensaPriceGroup.first())
        store.setMensaPriceGroup("auto")
        assertEquals("auto", store.mensaPriceGroup.first())
    }
}
