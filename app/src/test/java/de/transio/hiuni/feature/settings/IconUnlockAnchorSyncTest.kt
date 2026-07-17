package de.transio.hiuni.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.transio.hiuni.core.common.Semester
import de.transio.hiuni.core.common.Semester.Period
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.feature.courses.data.CourseDao
import de.transio.hiuni.feature.grades.data.GradeDao
import io.mockk.coEvery
import io.mockk.mockk
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
 * Integrationstest für den Anker-Update-Hook, den GradesRepository/
 * LsfMyCoursesRepository nach erfolgreichem Sync ausführen:
 *
 *   earliestOf(gradeSemesters + courseSemesters) -> anchorFirstSemesterAtLeast(...)
 *
 * Wir spielen exakt diese Logik gegen einen echten (dateibasierten) DataStore und
 * gemockte DAOs durch — ohne HTTP/Emulator. Damit ist verifiziert, dass ein
 * Student mit Bestandsdaten ab WS 23/24 nach dem Sync den Anker auf WS 23/24
 * gezogen bekommt (statt auf das spätere Install-Semester).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IconUnlockAnchorSyncTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var settings: SettingsDataStore
    private val gradeDao = mockk<GradeDao>(relaxed = true)
    private val courseDao = mockk<CourseDao>(relaxed = true)

    @Before
    fun setUp() {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher),
            produceFile = { tmp.newFile("settings.preferences_pb") }
        )
        settings = SettingsDataStore(dataStore)
    }

    /** Spiegelt die private updateIconUnlockAnchor()-Logik der Repositories. */
    private suspend fun runAnchorHook() {
        val labels = gradeDao.findDistinctSemesters() + courseDao.findDistinctSemesters()
        val earliest = Semester.earliestOf(labels) ?: return
        settings.anchorFirstSemesterAtLeast(earliest)
    }

    private suspend fun currentAnchor(): Semester? =
        settings.firstSemesterKey.first().takeIf { it.isNotBlank() }?.let(Semester::fromStorageKey)

    @Test
    fun `Sync-Hook zieht Anker auf fruehestes Transcript-Semester`() = runTest(dispatcher) {
        // Install-Fallback wie beim Erststart im höheren Semester.
        settings.initFirstSemesterIfMissing(Semester(Period.SS, 2026).storageKey())
        coEvery { gradeDao.findDistinctSemesters() } returns
            listOf("SoSe 26", "WiSe 23/24", "SoSe 24")
        coEvery { courseDao.findDistinctSemesters() } returns
            listOf("Sommer 2026", "Winter 2025/26")

        runAnchorHook()

        assertEquals(Semester(Period.WS, 2023), currentAnchor())
    }

    @Test
    fun `Sync-Hook mit leerem Transcript laesst Install-Anker stehen`() = runTest(dispatcher) {
        settings.initFirstSemesterIfMissing(Semester(Period.SS, 2026).storageKey())
        coEvery { gradeDao.findDistinctSemesters() } returns emptyList()
        coEvery { courseDao.findDistinctSemesters() } returns emptyList()

        runAnchorHook()

        assertEquals(Semester(Period.SS, 2026), currentAnchor())
    }
}
