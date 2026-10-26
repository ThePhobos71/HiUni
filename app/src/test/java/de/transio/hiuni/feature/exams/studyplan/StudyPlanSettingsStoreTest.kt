package de.transio.hiuni.feature.exams.studyplan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.DayOfWeek
import java.time.LocalTime

/** Persistenz-Tests für [StudyPlanSettingsStore] auf einem echten Preferences-DataStore. */
@OptIn(ExperimentalCoroutinesApi::class)
class StudyPlanSettingsStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: StudyPlanSettingsStore

    @Before
    fun setUp() {
        val file = tmp.newFile("study_plan.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher),
            produceFile = { file }
        )
        store = StudyPlanSettingsStore(dataStore)
    }

    @Test
    fun `ohne gespeicherte Werte gelten die Defaults`() = runTest(dispatcher) {
        val s = store.current()
        assertEquals(StudyPlanSettings(), s)
        assertEquals(LocalTime.of(9, 0), s.windowStart)
        assertEquals(LocalTime.of(20, 0), s.windowEnd)
        assertEquals(240, s.maxMinutesPerDay)
        assertEquals(90, s.blockMinutes)
        assertEquals(15, s.bufferMinutes)
        assertEquals(setOf(DayOfWeek.SUNDAY), s.freeDays)
        assertNull(s.reminderMinutesBefore)
        assertEquals(1, s.startOffsetDays)
    }

    @Test
    fun `Aenderungen ueberleben einen neuen Store`() = runTest(dispatcher) {
        store.update {
            it.copy(
                windowStart = LocalTime.of(8, 0),
                windowEnd = LocalTime.of(22, 0),
                maxMinutesPerDay = 180,
                blockMinutes = 60,
                bufferMinutes = 30,
                freeDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                hoursPerCredit = 4,
                reminderMinutesBefore = 15,
                startOffsetDays = 0
            )
        }
        val reread = StudyPlanSettingsStore(dataStore).current()
        assertEquals(LocalTime.of(8, 0), reread.windowStart)
        assertEquals(LocalTime.of(22, 0), reread.windowEnd)
        assertEquals(180, reread.maxMinutesPerDay)
        assertEquals(60, reread.blockMinutes)
        assertEquals(30, reread.bufferMinutes)
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), reread.freeDays)
        assertEquals(4, reread.hoursPerCredit)
        assertEquals(15, reread.reminderMinutesBefore)
        assertEquals(0, reread.startOffsetDays)
    }

    @Test
    fun `Erinnerung laesst sich wieder abschalten und keine freien Tage sind moeglich`() = runTest(dispatcher) {
        store.update { it.copy(reminderMinutesBefore = 30, freeDays = emptySet()) }
        store.update { it.copy(reminderMinutesBefore = null) }
        val s = store.current()
        assertNull(s.reminderMinutesBefore)
        assertEquals(emptySet<DayOfWeek>(), s.freeDays)
    }
}
