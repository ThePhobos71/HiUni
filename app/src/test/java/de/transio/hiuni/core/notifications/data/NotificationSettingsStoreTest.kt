package de.transio.hiuni.core.notifications.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Gate-Tests für [NotificationSettingsStore]: das In-App-Kategorie-Feintuning, das
 * VOR dem NotificationPresenter greift. Fahren einen echten (dateibasierten)
 * Preferences-DataStore auf einem Temp-Ordner.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: NotificationSettingsStore

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher),
            produceFile = { tmp.newFile("notif_settings.preferences_pb") }
        )
        store = NotificationSettingsStore(dataStore)
    }

    @After
    fun tearDown() {
        // Temp-Ordner räumt sich per Rule selbst ab.
    }

    @Test
    fun `default ist jede Kategorie an`() = runTest(dispatcher) {
        for (kind in NotificationKind.entries) {
            assertTrue("Kind $kind sollte per Default aktiv sein", store.isEnabled(kind))
        }
    }

    @Test
    fun `ausgeschaltete Kategorie gated alle zugehoerigen Kinds`() = runTest(dispatcher) {
        store.setEnabled(NotificationCategory.SYSTEM, false)

        // Alle Kinds der SYSTEM-Kategorie sind jetzt aus …
        assertFalse(store.isEnabled(NotificationKind.MENSA))
        assertFalse(store.isEnabled(NotificationKind.SYSTEM))
        assertFalse(store.isEnabled(NotificationKind.SPORT))
        // … andere Kategorien bleiben unberührt.
        assertTrue(store.isEnabled(NotificationKind.GRADE))
        assertTrue(store.isEnabled(NotificationKind.COURSE))
    }

    @Test
    fun `setEnabled ist umkehrbar`() = runTest(dispatcher) {
        store.setEnabled(NotificationCategory.GRADES, false)
        assertFalse(store.isEnabled(NotificationKind.GRADE))
        store.setEnabled(NotificationCategory.GRADES, true)
        assertTrue(store.isEnabled(NotificationKind.GRADE))
    }

    @Test
    fun `observeAll liefert alle Kategorien mit Default an`() = runTest(dispatcher) {
        val all = store.observeAll().first()
        assertEquals(NotificationCategory.entries.size, all.size)
        assertTrue(all.values.all { it })
    }

    @Test
    fun `observeEnabled reflektiert den gesetzten Wert`() = runTest(dispatcher) {
        store.setEnabled(NotificationCategory.COURSES, false)
        assertFalse(store.observeEnabled(NotificationCategory.COURSES).first())
    }
}
