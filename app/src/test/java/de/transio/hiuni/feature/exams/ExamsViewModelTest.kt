package de.transio.hiuni.feature.exams

import app.cash.turbine.test
import de.transio.hiuni.core.sync.LsfSyncScheduler
import de.transio.hiuni.feature.courses.data.CourseRepository
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.lsf.data.LsfExamsRepository
import de.transio.hiuni.core.common.AppResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ExamsViewModelTest {

    private val repository = mockk<LsfExamsRepository>(relaxed = true)
    private val scheduler = mockk<LsfSyncScheduler>(relaxed = true)
    private val courseRepository = mockk<CourseRepository>(relaxed = true)
    private val examsFlow = MutableStateFlow<List<ExamEntity>>(emptyList())
    private val coursesFlow =
        MutableStateFlow<List<de.transio.hiuni.feature.courses.data.CourseEntity>>(emptyList())

    // Wir brauchen einen StandardTestDispatcher (statt Unconfined), weil refresh()
    // ein delay(3000) fährt und wir die virtuelle Zeit kontrollieren wollen.
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.observeAll() } returns examsFlow
        every { courseRepository.observeAll() } returns coursesFlow
        coEvery { repository.saveManual(any()) } returns AppResult.Success(Unit)
        coEvery { repository.deleteManual(any()) } returns AppResult.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = ExamsViewModel(repository, scheduler, courseRepository)

    private fun exam(
        number: String,
        date: LocalDate?,
        rowId: Long = number.hashCode().toLong()
    ) = ExamEntity(
        rowId = rowId,
        veranstaltungsNumber = number,
        pruefungstext = "Klausur $number",
        moduleName = "Modul $number",
        parentModule = null,
        examDate = date,
        examTime = null,
        rooms = emptyList(),
        semester = "SoSe 26",
        semesterCode = "20261",
        registrationDate = null,
        cancellationDeadline = null,
        pruefer = null,
        courseId = null
    )

    @Test
    fun `initialer State ist isLoading true bevor Daten eintreffen`() = runTest {
        val vm = newVm()
        // stateIn liefert vor der ersten Kollektion den Initialwert.
        assertEquals(ExamsUiState(isLoading = true), vm.state.value)
    }

    @Test
    fun `nach erster Emission ist isLoading false`() = runTest {
        val vm = newVm()
        vm.state.test {
            // Initialwert (isLoading=true) wird von der ersten echten Emission überholt.
            advanceUntilIdle()
            val loaded = expectMostRecentItem()
            assertFalse(loaded.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Klausuren werden mit Datum aufsteigend und ohne Datum ans Ende sortiert`() = runTest {
        examsFlow.value = listOf(
            exam("3", date = null, rowId = 3),
            exam("1", date = LocalDate.of(2026, 8, 20), rowId = 1),
            exam("2", date = LocalDate.of(2026, 7, 1), rowId = 2)
        )
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals(
                listOf("2", "1", "3"),
                s.exams.map { it.veranstaltungsNumber }
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nextExam ist die naechste zukuenftige Klausur mit Datum`() = runTest {
        val today = LocalDate.now()
        examsFlow.value = listOf(
            exam("past", date = today.minusDays(5), rowId = 10),
            exam("soon", date = today.plusDays(2), rowId = 11),
            exam("later", date = today.plusDays(30), rowId = 12),
            exam("none", date = null, rowId = 13)
        )
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals("soon", s.nextExam?.veranstaltungsNumber)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nextExam ignoriert Klausur die heute noch faellt nicht - Grenzfall heute`() = runTest {
        val today = LocalDate.now()
        examsFlow.value = listOf(exam("today", date = today, rowId = 20))
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            // !isBefore(today) schließt heute mit ein
            assertEquals("today", s.nextExam?.veranstaltungsNumber)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nextExam ist null wenn nur vergangene oder undatierte Klausuren`() = runTest {
        val today = LocalDate.now()
        examsFlow.value = listOf(
            exam("past", date = today.minusDays(1), rowId = 30),
            exam("none", date = null, rowId = 31)
        )
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertNull(s.nextExam)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `timelineExams laesst das Hero-Item aus`() = runTest {
        val today = LocalDate.now()
        examsFlow.value = listOf(
            exam("soon", date = today.plusDays(2), rowId = 40),
            exam("later", date = today.plusDays(20), rowId = 41)
        )
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals("soon", s.nextExam?.veranstaltungsNumber)
            assertEquals(listOf("later"), s.timelineExams.map { it.veranstaltungsNumber })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `timelineExams liefert alle Klausuren wenn kein Hero existiert`() = runTest {
        val today = LocalDate.now()
        examsFlow.value = listOf(
            exam("past", date = today.minusDays(3), rowId = 50),
            exam("none", date = null, rowId = 51)
        )
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertNull(s.nextExam)
            assertEquals(2, s.timelineExams.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh setzt isRefreshing und triggert LSF-Sync`() = runTest {
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            expectMostRecentItem()
            vm.refresh()
            advanceTimeBy(100)
            runCurrent()
            assertTrue("Indicator muss direkt nach refresh() sichtbar sein", expectMostRecentItem().isRefreshing)
            // Der Worker wurde angestoßen
            verify { scheduler.triggerNow() }
            // Nach 3 s klappt der Indicator wieder zu
            advanceTimeBy(3000)
            runCurrent()
            assertFalse(expectMostRecentItem().isRefreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh ist reentrant-sicher - zweiter Aufruf waehrend Refresh triggert nicht erneut`() = runTest {
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            expectMostRecentItem()
            vm.refresh()
            advanceTimeBy(500)
            runCurrent()
            vm.refresh() // läuft noch → no-op
            advanceTimeBy(200)
            runCurrent()
            // triggerNow darf nur EINMAL gelaufen sein
            verify(exactly = 1) { scheduler.triggerNow() }
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Manuelles Klausur-Eintragen -------------------------------------------

    @Test
    fun `startAdd oeffnet ein Editor-Sheet mit leerem manuellen Entwurf`() = runTest {
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            expectMostRecentItem()
            vm.startAdd()
            runCurrent()
            val s = expectMostRecentItem()
            val editing = s.editing
            assertTrue("Sheet muss offen sein", editing != null)
            assertEquals(0L, editing!!.rowId)
            assertTrue("Entwurf ist manuell", editing.isManual)
            assertEquals("", editing.moduleName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startEdit ignoriert LSF-Eintraege und oeffnet nur manuelle`() = runTest {
        val vm = newVm()
        val lsf = exam("100", date = null, rowId = 100)
        val manual = exam("man", date = null, rowId = 101)
            .copy(source = ExamEntity.SOURCE_MANUAL)
        vm.state.test {
            advanceUntilIdle()
            expectMostRecentItem()

            // startEdit(LSF) ist ein No-op → es gibt KEINE neue Emission.
            // Deshalb den State-Wert direkt prüfen statt auf ein Turbine-Item
            // zu warten, das nie kommt.
            vm.startEdit(lsf)
            runCurrent()
            assertNull("LSF-Eintrag darf kein Sheet öffnen", vm.state.value.editing)

            vm.startEdit(manual)
            runCurrent()
            assertEquals(101L, expectMostRecentItem().editing?.rowId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save reicht Eintrag an Repository durch und schliesst das Sheet`() = runTest {
        val vm = newVm()
        val draft = exam("man", date = LocalDate.of(2026, 9, 1), rowId = 0)
            .copy(source = ExamEntity.SOURCE_MANUAL, moduleName = "Statistik")
        vm.state.test {
            advanceUntilIdle()
            vm.startAdd()
            runCurrent()
            expectMostRecentItem()

            val captured = slot<ExamEntity>()
            vm.save(draft)
            advanceUntilIdle()

            coVerify { repository.saveManual(capture(captured)) }
            assertEquals("Statistik", captured.captured.moduleName)
            assertNull("Sheet muss nach Save zu sein", expectMostRecentItem().editing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete loescht nur manuelle Eintraege ueber das Repository`() = runTest {
        val vm = newVm()
        val lsf = exam("100", date = null, rowId = 100)
        val manual = exam("man", date = null, rowId = 101)
            .copy(source = ExamEntity.SOURCE_MANUAL)

        vm.delete(lsf)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.deleteManual(any()) }

        vm.delete(manual)
        advanceUntilIdle()
        coVerify { repository.deleteManual(101L) }
    }

    @Test
    fun `courses aus dem CourseRepository landen im State`() = runTest {
        coursesFlow.value = listOf(
            de.transio.hiuni.feature.courses.data.CourseEntity(
                id = "c1", name = "DBS", professor = "P", credits = 5, semester = "SoSe 26"
            )
        )
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals(listOf("DBS"), s.courses.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `leeres Repository liefert leere aber nicht-ladende Liste`() = runTest {
        examsFlow.value = emptyList()
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertFalse(s.isLoading)
            assertTrue(s.exams.isEmpty())
            assertNull(s.nextExam)
            assertTrue(s.timelineExams.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
