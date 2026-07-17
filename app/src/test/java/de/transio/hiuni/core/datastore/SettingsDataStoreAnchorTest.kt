package de.transio.hiuni.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.transio.hiuni.core.common.Semester
import de.transio.hiuni.core.common.Semester.Period
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

/**
 * Tests für den Icon-Unlock-Anker in [SettingsDataStore.anchorFirstSemesterAtLeast]:
 * der Anker darf sich nur nach VORNE (kleinerer ordinal) verschieben, nie nach
 * hinten — sonst würden bereits freigeschaltete Icon-Varianten wieder verschwinden.
 * Fährt einen echten (dateibasierten) Preferences-DataStore auf einem Temp-Ordner.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDataStoreAnchorTest {

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

    private suspend fun currentAnchor(): Semester? =
        store.firstSemesterKey.first().takeIf { it.isNotBlank() }?.let(Semester::fromStorageKey)

    @Test
    fun `setzt Anker initial wenn noch keiner existiert`() = runTest(dispatcher) {
        store.anchorFirstSemesterAtLeast(Semester(Period.WS, 2023))
        assertEquals(Semester(Period.WS, 2023), currentAnchor())
    }

    @Test
    fun `verschiebt Anker nach vorne bei frueherem Semester`() = runTest(dispatcher) {
        store.anchorFirstSemesterAtLeast(Semester(Period.SS, 2026))
        store.anchorFirstSemesterAtLeast(Semester(Period.WS, 2023))
        assertEquals(Semester(Period.WS, 2023), currentAnchor())
    }

    @Test
    fun `ignoriert spaeteres Semester`() = runTest(dispatcher) {
        store.anchorFirstSemesterAtLeast(Semester(Period.WS, 2023))
        store.anchorFirstSemesterAtLeast(Semester(Period.SS, 2026))
        assertEquals(Semester(Period.WS, 2023), currentAnchor())
    }

    @Test
    fun `gleiches Semester laesst Anker unveraendert`() = runTest(dispatcher) {
        store.anchorFirstSemesterAtLeast(Semester(Period.WS, 2023))
        store.anchorFirstSemesterAtLeast(Semester(Period.WS, 2023))
        assertEquals(Semester(Period.WS, 2023), currentAnchor())
    }

    @Test
    fun `zieht Install-Fallback-Anker nach vorne`() = runTest(dispatcher) {
        // Install-Semester (Fallback aus HiUniApplication) …
        store.initFirstSemesterIfMissing(Semester(Period.SS, 2026).storageKey())
        // … dann kommt der echte Transcript-Anker über den Sync.
        store.anchorFirstSemesterAtLeast(Semester(Period.WS, 2023))
        assertEquals(Semester(Period.WS, 2023), currentAnchor())
    }

    @Test
    fun `Verifikation Punkt 4 - WS 23-24 bis SS 26 ergibt 5 Uebergaenge (alle Icons frei)`() = runTest(dispatcher) {
        // Bestandsdaten ab WS 23/24, aktuelles Semester SS 26.
        store.anchorFirstSemesterAtLeast(Semester.parseLabel("WiSe 23/24")!!)
        val first = currentAnchor()!!
        val current = Semester(Period.SS, 2026)
        // unlocksAfterSemesters der 4 Varianten: 0/1/2/3. Bei 5 Übergängen alle frei.
        assertEquals(5, Semester.semestersBetween(first, current))
    }
}
