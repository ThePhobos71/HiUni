package de.transio.hiuni.core.update

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreUpdatePreferencesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    private fun store(): DataStoreUpdatePreferences {
        val file = tmp.newFile("update.preferences_pb")
        val ds = PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher), produceFile = { file })
        return DataStoreUpdatePreferences(ds)
    }

    @Test
    fun `Defaults Auto-Pruefung an, nichts uebersprungen`() = runTest(dispatcher) {
        val s = store()
        assertEquals(UpdatePrefsSnapshot(), s.snapshot())
        assertEquals(true, s.autoCheck.first())
        assertNull(s.cachedRelease())
    }

    @Test
    fun `Werte werden gespeichert`() = runTest(dispatcher) {
        val s = store()
        s.setAutoCheck(false)
        s.setSkippedVersionCode(10402)
        s.setLastCheckEpochMs(42L)
        s.setLastNotifiedVersionCode(10403)
        s.saveCachedRelease(CachedRelease(etag = "\"abc\"", body = "{}"))
        val snap = s.snapshot()
        assertFalse(snap.autoCheck)
        assertEquals(10402, snap.skippedVersionCode)
        assertEquals(42L, snap.lastCheckEpochMs)
        assertEquals(10403, snap.lastNotifiedVersionCode)
        assertEquals(CachedRelease("\"abc\"", "{}"), s.cachedRelease())

        s.setSkippedVersionCode(null)
        assertNull(s.snapshot().skippedVersionCode)
    }
}
