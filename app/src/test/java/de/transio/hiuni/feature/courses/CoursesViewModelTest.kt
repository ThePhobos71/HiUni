package de.transio.hiuni.feature.courses

import de.transio.hiuni.core.search.PendingSearchSelection
import de.transio.hiuni.core.search.SearchFixtures
import de.transio.hiuni.core.search.SearchSelectionHolder
import org.junit.Assert.assertNull
import app.cash.turbine.test
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.sync.LsfSyncScheduler
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.courses.data.CourseRepository
import de.transio.hiuni.feature.grades.data.GradeEntity
import de.transio.hiuni.feature.grades.data.GradeStatus
import de.transio.hiuni.feature.grades.data.GradesRepository
import de.transio.hiuni.feature.grades.data.GradesSummaryEntity
import de.transio.hiuni.feature.grades.data.GradesSyncResult
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests für die im [CoursesViewModel] abgeleitete effektive Note (Kurse ×
 * Notenspiegel-Leistungen). Die Matching-Logik selbst ist in
 * [CourseGradeMatcherTest] separat abgedeckt; hier geht es um das korrekte
 * Zusammenspiel der beiden Flows im UI-State.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoursesViewModelTest {

    private class FakeCourseRepository : CourseRepository {
        val coursesFlow = MutableStateFlow<List<CourseEntity>>(emptyList())
        override fun observeAll(): Flow<List<CourseEntity>> = coursesFlow
        override suspend fun findById(id: String): CourseEntity? = coursesFlow.value.firstOrNull { it.id == id }
        override suspend fun findByLsfId(lsfId: String): CourseEntity? = coursesFlow.value.firstOrNull { it.lsfId == lsfId }
        override suspend fun upsert(course: CourseEntity) {
            coursesFlow.value = coursesFlow.value.filterNot { it.id == course.id } + course
        }
        override suspend fun deleteById(id: String) {
            coursesFlow.value = coursesFlow.value.filterNot { it.id == id }
        }
    }

    private class FakeGradesRepository : GradesRepository {
        val gradesFlow = MutableStateFlow<List<GradeEntity>>(emptyList())
        override fun observeAll(): Flow<List<GradeEntity>> = gradesFlow
        override fun observeSummary(): Flow<GradesSummaryEntity?> = MutableStateFlow(null)
        override suspend fun refresh(force: Boolean): AppResult<GradesSyncResult> =
            AppResult.Success(GradesSyncResult(0, 0, 0, 0))
    }

    private val courseRepo = FakeCourseRepository()
    private val gradesRepo = FakeGradesRepository()
    private val scheduler = mockk<LsfSyncScheduler>(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val selectionHolder = SearchSelectionHolder()

    private fun newVm() = CoursesViewModel(courseRepo, gradesRepo, scheduler, selectionHolder)

    private fun course(id: String, name: String, semester: String, lsfCode: String?, grade: String? = null) =
        CourseEntity(
            id = id,
            name = name,
            professor = "Prof",
            credits = 6,
            semester = semester,
            source = CourseEntity.SOURCE_LSF,
            lsfCode = lsfCode,
            grade = grade
        )

    private fun grade(titel: String, veranstaltungsNr: String?, semester: String, note: Double?, status: GradeStatus, rowId: Long) =
        GradeEntity(
            rowId = rowId,
            mergeKey = "l:$rowId",
            labnr = rowId,
            pruefungsNr = rowId.toString(),
            titel = titel,
            veranstaltungsNr = veranstaltungsNr,
            kontoNr = null,
            kontoName = null,
            semester = semester,
            note = note,
            status = status,
            bonusLp = 6,
            vermerk = "",
            versuch = 1,
            pruefungsDatum = null,
            fetchedAt = 0L
        )

    @Test
    fun `effektive Note wird pro Kurs aus dem Notenspiegel abgeleitet`() = runTest {
        courseRepo.coursesFlow.value = listOf(
            course("c1", "Betriebliche Informationssysteme", "WiSe 24/25", lsfCode = "3202")
        )
        gradesRepo.gradesFlow.value = listOf(
            grade("Betriebliche Informationssysteme", "3202", "WiSe 24/25", 2.7, GradeStatus.PASSED, 1)
        )
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            val eff = s.effectiveGrade(s.courses.single())
            assertEquals(GradeSource.NOTENSPIEGEL, eff.source)
            assertEquals("2,7", eff.label)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `manuelle Note hat Vorrang vor Notenspiegel im State`() = runTest {
        courseRepo.coursesFlow.value = listOf(
            course("c1", "BIS", "WiSe 24/25", lsfCode = "3202", grade = "1,0")
        )
        gradesRepo.gradesFlow.value = listOf(
            grade("BIS", "3202", "WiSe 24/25", 3.7, GradeStatus.PASSED, 1)
        )
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            val eff = s.effectiveGrade(s.courses.single())
            assertEquals(GradeSource.MANUAL, eff.source)
            assertEquals("1,0", eff.label)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ohne passende Note bleibt der Status offen`() = runTest {
        courseRepo.coursesFlow.value = listOf(
            course("c1", "Kurs ohne Note", "WiSe 24/25", lsfCode = "0001")
        )
        gradesRepo.gradesFlow.value = listOf(
            grade("Anderes Fach", "9999", "WiSe 24/25", 1.0, GradeStatus.PASSED, 1)
        )
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            val eff = s.effectiveGrade(s.courses.single())
            assertEquals(GradeSource.NONE, eff.source)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Deep-Link aus der Spotlight-Suche ──────────────────────────────────

    @Test
    fun `vorgemerkter Kurs oeffnet dessen Detail-Ansicht im richtigen Semester`() = runTest {
        val target = SearchFixtures.course(id = "c2", semester = "SoSe 25")
        courseRepo.coursesFlow.value = listOf(
            course("c1", "Neuer Kurs", "WiSe 25/26", lsfCode = null),
            target
        )
        selectionHolder.stage(PendingSearchSelection.Course(target))

        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals("c2", s.selectedCourseId)
            assertEquals("SoSe 25", s.selectedSemester)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(selectionHolder.pending.value)
    }

    @Test
    fun `nach Zurueck bleibt die Kursliste offen, auch nach Neuaufbau`() = runTest {
        val target = SearchFixtures.course(id = "c2")
        courseRepo.coursesFlow.value = listOf(target)
        selectionHolder.stage(PendingSearchSelection.Course(target))
        val vm = newVm()
        advanceUntilIdle()

        vm.select(null)
        val recreated = newVm()

        vm.state.test {
            advanceUntilIdle()
            assertNull(expectMostRecentItem().selectedCourseId)
            cancelAndIgnoreRemainingEvents()
        }
        recreated.state.test {
            advanceUntilIdle()
            assertNull(expectMostRecentItem().selectedCourseId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Pop-in: geladen vs. noch nicht geladen ────────────────────────────

    @Test
    fun `vor der ersten Room-Emission ist die Kursliste nicht geladen`() = runTest(dispatcher) {
        val vm = newVm()
        val job = launch { vm.state.collect { } }
        // Noch kein advanceUntilIdle: der Collector lief nicht, es gilt der Initialwert.
        assertEquals(false, vm.state.value.isContentLoaded)
        advanceUntilIdle()
        assertEquals(true, vm.state.value.isContentLoaded)
        job.cancel()
    }
}
