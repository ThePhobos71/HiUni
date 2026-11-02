package de.transio.hiuni.feature.notifications

import app.cash.turbine.test
import de.transio.hiuni.core.notifications.data.NotificationCategory
import de.transio.hiuni.core.notifications.data.NotificationSettingsStore
import de.transio.hiuni.core.notifications.lecture.CourseNotificationPrefs
import de.transio.hiuni.core.notifications.lecture.FakeCourseNotificationPrefsStore
import de.transio.hiuni.core.notifications.lecture.prefsFor
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.courses.data.CourseRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Übersicht „Vorlesungen" in den Mitteilungs-Einstellungen: Kurse des aktuellen
 * Semesters mit ihren Einstellungen, Zusammenfassung und Sammel-Schalter.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LectureNotificationsViewModelTest {

    private class FakeCourseRepository(initial: List<CourseEntity>) : CourseRepository {
        val coursesFlow = MutableStateFlow(initial)
        override fun observeAll(): Flow<List<CourseEntity>> = coursesFlow
        override suspend fun findById(id: String): CourseEntity? = coursesFlow.value.firstOrNull { it.id == id }
        override suspend fun findByLsfId(lsfId: String): CourseEntity? = coursesFlow.value.firstOrNull { it.lsfId == lsfId }
        override suspend fun upsert(course: CourseEntity) = Unit
        override suspend fun deleteById(id: String) = Unit
    }

    private val dispatcher = StandardTestDispatcher()
    private val prefs = FakeCourseNotificationPrefsStore()
    private val liveCategory = MutableStateFlow(true)
    private val settings = mockk<NotificationSettingsStore> {
        every { observeEnabled(NotificationCategory.LIVE_LECTURE) } returns liveCategory
    }

    private fun course(lsfId: String?, name: String, semester: String, id: String = "lsf-$lsfId", type: String? = null) =
        CourseEntity(
            id = id,
            name = name,
            professor = "Prof",
            credits = 5,
            semester = semester,
            source = if (lsfId != null) CourseEntity.SOURCE_LSF else CourseEntity.SOURCE_USER,
            lsfId = lsfId,
            courseType = type
        )

    private val repo = FakeCourseRepository(
        listOf(
            course("1", "Lineare Algebra", "Winter 2026/27", type = "Vorlesung"),
            course("2", "Analysis", "Winter 2026/27", type = "Vorlesung mit Übung"),
            course(null, "Eigener Kurs", "Winter 2026/27", id = "abc"),
            course("9", "Altes Fach", "Sommer 2026")
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = LectureNotificationsViewModel(repo, prefs, settings)

    @Test
    fun `listet nur Kurse des aktuellen Semesters mit ihren Einstellungen`() = runTest {
        prefs.flow.value = mapOf(
            "1" to CourseNotificationPrefs(liveEnabled = false),
            "2" to CourseNotificationPrefs(reminderMinutes = 15)
        )
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals("Winter 2026/27", s.semester)
            assertEquals(listOf("Analysis", "Eigener Kurs", "Lineare Algebra"), s.rows.map { it.name })
            assertEquals(listOf("2", "kurs:abc", "1"), s.rows.map { it.key })
            val analysis = s.rows.first()
            assertTrue(analysis.liveEnabled)
            assertEquals(15, analysis.reminderMinutes)
            assertEquals("Vorlesung mit Übung", analysis.detail)
            assertFalse(s.rows.last().liveEnabled)
            assertEquals(3, s.total)
            assertEquals(2, s.liveCount)
            assertEquals(1, s.reminderCount)
            assertEquals("Live-Anzeige für 2 von 3 Kursen, Erinnerung für 1", s.summary)
            assertTrue(s.liveCategoryEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `einzelne Schalter schreiben in den Store`() = runTest {
        val vm = newVm()
        vm.setLiveEnabled("1", false)
        vm.setReminderMinutes("kurs:abc", 30)
        advanceUntilIdle()

        assertFalse(prefs.flow.value.prefsFor("1").liveEnabled)
        assertEquals(30, prefs.flow.value.prefsFor("kurs:abc").reminderMinutes)
    }

    @Test
    fun `Alle aus und Alle an betreffen nur die Kurse des aktuellen Semesters`() = runTest {
        prefs.flow.value = mapOf("9" to CourseNotificationPrefs(liveEnabled = false))
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            expectMostRecentItem()

            vm.setAllLive(false)
            advanceUntilIdle()
            val off = expectMostRecentItem()
            assertEquals(0, off.liveCount)
            assertEquals("Live-Anzeige für 0 von 3 Kursen, Erinnerung für 0", off.summary)

            vm.setAllLive(true)
            advanceUntilIdle()
            assertEquals(3, expectMostRecentItem().liveCount)
            cancelAndIgnoreRemainingEvents()
        }
        // Kurs aus einem alten Semester bleibt unangetastet.
        assertFalse(prefs.flow.value.prefsFor("9").liveEnabled)
    }

    @Test
    fun `globaler Schalter fuer die Live-Anzeige wird durchgereicht`() = runTest {
        liveCategory.value = false
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().liveCategoryEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ohne Kurse eigene Zusammenfassung`() = runTest {
        repo.coursesFlow.value = emptyList()
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals(0, s.total)
            assertEquals("Noch keine Kurse im aktuellen Semester", s.summary)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
