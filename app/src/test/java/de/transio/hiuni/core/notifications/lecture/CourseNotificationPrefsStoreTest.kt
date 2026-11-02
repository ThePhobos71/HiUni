package de.transio.hiuni.core.notifications.lecture

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.transio.hiuni.feature.courses.data.CourseEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Mitteilungs-Einstellungen je Kurs: Live-Anzeige (Default an) und Erinnerungs-Vorlauf
 * (Default aus), persistiert in einem echten Preferences-DataStore.
 */
class CourseNotificationPrefsStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: DataStoreCourseNotificationPrefsStore

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher),
            produceFile = { tmp.newFile("course_prefs.preferences_pb") }
        )
        store = DataStoreCourseNotificationPrefsStore(dataStore)
    }

    @Test
    fun `ohne Eintrag gilt Live an und Erinnerung aus`() = runTest(dispatcher) {
        val all = store.observeAll().first()
        assertTrue(all.isEmpty())
        val prefs = all.prefsFor("4711")
        assertTrue(prefs.liveEnabled)
        assertNull(prefs.reminderMinutes)
        assertEquals(CourseNotificationPrefs.DEFAULT, prefs)
    }

    @Test
    fun `Live aus und Erinnerung werden je Kurs gespeichert`() = runTest(dispatcher) {
        store.setLiveEnabled("4711", false)
        store.setReminderMinutes("4711", 15)
        store.setReminderMinutes("0815", 5)

        val all = store.current()
        assertEquals(CourseNotificationPrefs(liveEnabled = false, reminderMinutes = 15), all.prefsFor("4711"))
        assertEquals(CourseNotificationPrefs(liveEnabled = true, reminderMinutes = 5), all.prefsFor("0815"))
        assertEquals(CourseNotificationPrefs.DEFAULT, all.prefsFor("9999"))
        assertEquals(all, store.observeAll().first())
    }

    @Test
    fun `zurueck auf Standard entfernt den Eintrag`() = runTest(dispatcher) {
        store.setLiveEnabled("4711", false)
        store.setReminderMinutes("4711", 30)
        store.setLiveEnabled("4711", true)
        store.setReminderMinutes("4711", null)

        assertTrue(store.current().isEmpty())
    }

    @Test
    fun `Sammel-Schalter setzt Live fuer alle angegebenen Kurse`() = runTest(dispatcher) {
        store.setReminderMinutes("b", 10)
        store.setLiveEnabledFor(listOf("a", "b", "c"), false)
        val off = store.current()
        assertFalse(off.prefsFor("a").liveEnabled)
        assertFalse(off.prefsFor("b").liveEnabled)
        assertFalse(off.prefsFor("c").liveEnabled)
        assertEquals(10, off.prefsFor("b").reminderMinutes)

        store.setLiveEnabledFor(listOf("a", "b"), true)
        val on = store.current()
        assertTrue(on.prefsFor("a").liveEnabled)
        assertTrue(on.prefsFor("b").liveEnabled)
        assertFalse(on.prefsFor("c").liveEnabled)
        assertEquals(10, on.prefsFor("b").reminderMinutes)
    }

    @Test
    fun `ungueltiger Vorlauf wird verworfen`() = runTest(dispatcher) {
        store.setReminderMinutes("4711", 0)
        store.setReminderMinutes("0815", -5)
        assertTrue(store.current().isEmpty())
    }

    @Test
    fun `Schluessel ist die LSF-ID, ohne LSF-ID die Kurs-ID`() {
        val lsf = CourseEntity(
            id = "lsf-4711", name = "Analysis", professor = "", credits = 5,
            semester = "Winter 2026/27", lsfId = "4711"
        )
        val own = CourseEntity(id = "abc", name = "Eigener Kurs", professor = "", credits = 0, semester = "Winter 2026/27")
        assertEquals("4711", CourseNotificationPrefs.keyFor(lsf))
        assertEquals("kurs:abc", CourseNotificationPrefs.keyFor(own))
    }

    @Test
    fun `Hilfsfunktionen fuer den Planer liefern die abweichenden Kurse`() {
        val all = mapOf(
            "a" to CourseNotificationPrefs(liveEnabled = false),
            "b" to CourseNotificationPrefs(reminderMinutes = 10),
            "c" to CourseNotificationPrefs(liveEnabled = false, reminderMinutes = 5)
        )
        assertEquals(setOf("a", "c"), all.liveDisabledKeys())
        assertEquals(mapOf("b" to 10, "c" to 5), all.reminderMinutesByKey())
    }
}
