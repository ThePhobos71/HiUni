package de.transio.hiuni.feature.bib.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

/**
 * Persistenz von [BibRoomAlarmStore] auf echtem Preferences-DataStore, inklusive
 * der Standardwerte für Bestandsnutzer und dem Ablauf von „Nur heute".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BibRoomAlarmStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 26)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: BibRoomAlarmStore
    private lateinit var favorites: BibFavoritesStore

    @Before
    fun setUp() {
        val file = tmp.newFile("bib_alarm.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher),
            produceFile = { file }
        )
        store = BibRoomAlarmStore(dataStore)
        favorites = BibFavoritesStore(dataStore)
    }

    /** Bestandsnutzer: Favoriten vom Vor-Release, aber noch kein Alarm-Modus gespeichert. */
    private suspend fun seedLegacyFavorites(vararg ids: Int) {
        dataStore.edit { it[stringSetPreferencesKey("bib_favorite_rooms")] = ids.map(Int::toString).toSet() }
    }

    @Test
    fun `neuer Nutzer ohne Favoriten startet mit Aus und den Standardwerten`() = runTest(dispatcher) {
        val alarm = store.alarm.first()

        assertEquals(BibAlarmMode.OFF, alarm.mode)
        assertEquals(120, alarm.durationMinutes)
        assertEquals(8, alarm.fromHour)
        assertEquals(20, alarm.toHour)
    }

    @Test
    fun `Bestandsnutzer mit Favoriten behaelt den Alarm als Immer`() = runTest(dispatcher) {
        seedLegacyFavorites(102)

        assertEquals(BibAlarmMode.ALWAYS, store.alarm.first().mode)
        assertEquals(BibAlarmMode.ALWAYS, store.current(today).mode)
    }

    @Test
    fun `erster Favorit eines neuen Nutzers schaltet den Alarm nicht ein`() = runTest(dispatcher) {
        favorites.toggle(102)

        assertEquals(BibAlarmMode.OFF, store.alarm.first().mode)
    }

    @Test
    fun `Bestandsnutzer bleibt bei Immer, auch wenn er Favoriten aendert`() = runTest(dispatcher) {
        seedLegacyFavorites(102)

        favorites.toggle(102) // letzter Favorit weg
        favorites.toggle(103) // neuer Favorit

        assertEquals(BibAlarmMode.ALWAYS, store.alarm.first().mode)
    }

    @Test
    fun `Nur heute merkt sich das Datum und ist am Folgetag Aus und zurueckgesetzt`() = runTest(dispatcher) {
        store.setMode(BibAlarmMode.TODAY_ONLY, today)

        val sameDay = store.current(today)
        assertEquals(BibAlarmMode.TODAY_ONLY, sameDay.mode)
        assertEquals(today, sameDay.activatedOn)

        val nextDay = store.current(today.plusDays(1))
        assertEquals(BibAlarmMode.OFF, nextDay.mode)
        // Lazy zurückgesetzt: auch der Flow (z.B. für den Screen) zeigt jetzt Aus.
        assertEquals(BibAlarmMode.OFF, store.alarm.first().mode)
        assertNull(store.alarm.first().activatedOn)
    }

    @Test
    fun `Immer und Aus vergessen das Aktivierungsdatum`() = runTest(dispatcher) {
        store.setMode(BibAlarmMode.TODAY_ONLY, today)
        store.setMode(BibAlarmMode.ALWAYS, today)

        val alarm = store.current(today.plusDays(5))
        assertEquals(BibAlarmMode.ALWAYS, alarm.mode)
        assertNull(alarm.activatedOn)
    }

    @Test
    fun `Dauer und Zeitfenster werden gespeichert`() = runTest(dispatcher) {
        store.setDuration(60)
        store.setFromHour(14)
        store.setToHour(18)

        val alarm = BibRoomAlarmStore(dataStore).alarm.first()
        assertEquals(60, alarm.durationMinutes)
        assertEquals(14, alarm.fromHour)
        assertEquals(18, alarm.toHour)
    }

    @Test
    fun `ungueltige Dauer faellt auf 2 Stunden zurueck`() = runTest(dispatcher) {
        store.setDuration(45)

        assertEquals(120, store.alarm.first().durationMinutes)
    }

    @Test
    fun `Von hinter Bis verschiebt Bis mit`() = runTest(dispatcher) {
        store.setToHour(12)
        store.setFromHour(15)

        val alarm = store.alarm.first()
        assertEquals(15, alarm.fromHour)
        assertEquals(16, alarm.toHour)
    }
}
