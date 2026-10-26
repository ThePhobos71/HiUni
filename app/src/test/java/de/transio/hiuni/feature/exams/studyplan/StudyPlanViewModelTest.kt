package de.transio.hiuni.feature.exams.studyplan

import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.courses.data.CourseRepository
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.lsf.data.LsfExamsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class StudyPlanViewModelTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val today: LocalDate = LocalDate.of(2026, 11, 2)
    private val now: Instant = LocalDateTime.of(today, LocalTime.of(8, 0)).atZone(zone).toInstant()
    private val clock: Clock = Clock.fixed(now, zone)

    private val dispatcher = StandardTestDispatcher()
    private val examsRepository = mockk<LsfExamsRepository>(relaxed = true)
    private val courseRepository = mockk<CourseRepository>(relaxed = true)
    private val repository = mockk<StudyPlanRepository>(relaxed = true)
    private val settingsStore = mockk<StudyPlanSettingsStore>(relaxed = true)

    private val examsFlow = MutableStateFlow<List<ExamEntity>>(emptyList())
    private val coursesFlow = MutableStateFlow<List<CourseEntity>>(emptyList())
    private val settingsFlow = MutableStateFlow(StudyPlanSettings())
    private val upcomingBlocks = MutableStateFlow<List<CustomEventEntity>>(emptyList())

    private val soonDate = today.plusDays(10)
    private val farDate = today.plusDays(70)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { examsRepository.observeAll() } returns examsFlow
        every { courseRepository.observeAll() } returns coursesFlow
        every { settingsStore.settings } returns settingsFlow
        coEvery { settingsStore.update(any()) } answers {
            val transform = firstArg<(StudyPlanSettings) -> StudyPlanSettings>()
            settingsFlow.value = transform(settingsFlow.value)
        }
        every { repository.observeUpcomingBlocks(any()) } returns upcomingBlocks
        coEvery { repository.busyIntervals(any(), any(), any()) } returns emptyList()
        coEvery { repository.replaceFutureBlocks(any(), any(), any(), any()) } answers {
            firstArg<List<PlannedStudyBlock>>().size
        }
        coEvery { repository.deleteFutureBlocks(any()) } returns 3

        coursesFlow.value = listOf(
            CourseEntity(id = "c1", name = "Datenbanken", professor = "", credits = 5, semester = "WiSe")
        )
        examsFlow.value = listOf(
            exam(1, soonDate, courseId = "c1", title = "Datenbanken"),
            exam(2, farDate, title = "Statistik"),
            exam(3, today.minusDays(5), title = "Vorbei"),
            exam(4, null, title = "Ohne Termin")
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun exam(rowId: Long, date: LocalDate?, courseId: String? = null, title: String) = ExamEntity(
        rowId = rowId,
        veranstaltungsNumber = rowId.toString(),
        pruefungstext = title,
        moduleName = title,
        parentModule = null,
        examDate = date,
        examTime = LocalTime.of(10, 0),
        rooms = emptyList(),
        semester = "WiSe 26/27",
        semesterCode = "20262",
        registrationDate = null,
        cancellationDeadline = null,
        pruefer = null,
        courseId = courseId
    )

    private fun newVm() = StudyPlanViewModel(
        examsRepository = examsRepository,
        courseRepository = courseRepository,
        repository = repository,
        settingsStore = settingsStore,
        clock = clock
    )

    @Test
    fun `oeffnen waehlt Klausuren der naechsten sechs Wochen vor und berechnet eine Vorschau`() = runTest(dispatcher) {
        val vm = newVm()
        vm.open()
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s.isOpen)
        // Nur künftige Klausuren mit Datum, nach Datum sortiert.
        assertEquals(listOf(1L, 2L), s.candidates.map { it.exam.examId })
        assertEquals(listOf(true, false), s.candidates.map { it.selected })
        // LP kommen aus dem verknüpften Kurs.
        assertEquals(5, s.candidates.first().exam.credits)
        assertEquals(today.plusDays(1), s.startDate)
        assertEquals(StudyPlanStart.TOMORROW, s.start)

        assertNotNull(s.preview)
        val preview = s.preview!!
        assertTrue(preview.blocks.isNotEmpty())
        assertTrue(preview.blocks.all { it.examId == 1L })
        assertTrue(preview.blocks.all { it.title == "Datenbanken" })
        assertTrue(s.canApply)
    }

    @Test
    fun `Klausur an- und abwaehlen aktualisiert die Vorschau`() = runTest(dispatcher) {
        val vm = newVm()
        vm.open()
        advanceUntilIdle()
        vm.toggleExam(2)
        advanceUntilIdle()
        assertTrue(vm.state.value.preview!!.blocks.any { it.examId == 2L })

        vm.toggleExam(1)
        vm.toggleExam(2)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(0, s.selectedCount)
        assertEquals(StudyPlanEmptyReason.NO_EXAMS, s.preview!!.emptyReason)
        assertFalse(s.canApply)
    }

    @Test
    fun `belegter Kalender fuehrt zu einer leeren Vorschau mit Grund`() = runTest(dispatcher) {
        coEvery { repository.busyIntervals(any(), any(), any()) } returns listOf(
            BusyInterval(now, now.plus(Duration.ofDays(30)))
        )
        val vm = newVm()
        vm.open()
        advanceUntilIdle()
        assertEquals(StudyPlanEmptyReason.NO_FREE_TIME, vm.state.value.preview!!.emptyReason)
        assertFalse(vm.state.value.canApply)
    }

    @Test
    fun `Uebernehmen schreibt die Vorschau-Bloecke und schliesst das Sheet`() = runTest(dispatcher) {
        settingsFlow.value = StudyPlanSettings(reminderMinutesBefore = 15)
        val vm = newVm()
        vm.open()
        advanceUntilIdle()
        val preview = vm.state.value.preview!!

        vm.apply()
        advanceUntilIdle()

        val written = slot<List<PlannedStudyBlock>>()
        coVerify { repository.replaceFutureBlocks(capture(written), 15, now, zone) }
        assertEquals(preview.blocks, written.captured)
        val s = vm.state.value
        assertFalse(s.isOpen)
        assertEquals("${preview.blocks.size} Lerneinheiten eingetragen", s.message)

        vm.consumeMessage()
        advanceUntilIdle()
        assertNull(vm.state.value.message)
    }

    @Test
    fun `Uebernehmen ohne Bloecke schreibt nichts`() = runTest(dispatcher) {
        val vm = newVm()
        vm.open()
        advanceUntilIdle()
        vm.toggleExam(1)
        advanceUntilIdle()

        vm.apply()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.replaceFutureBlocks(any(), any(), any(), any()) }
        assertTrue(vm.state.value.isOpen)
    }

    @Test
    fun `Lernplan loeschen entfernt die kuenftigen Bloecke`() = runTest(dispatcher) {
        val vm = newVm()
        vm.open()
        advanceUntilIdle()

        vm.deletePlan()
        advanceUntilIdle()

        coVerify { repository.deleteFutureBlocks(now) }
        assertEquals("3 Lerneinheiten gelöscht", vm.state.value.message)
        assertFalse(vm.state.value.isOpen)
    }

    @Test
    fun `Einstellungen werden gespeichert und der Start auf heute gesetzt`() = runTest(dispatcher) {
        val vm = newVm()
        vm.open()
        advanceUntilIdle()

        vm.updateSettings { it.copy(blockMinutes = 60) }
        vm.setStart(StudyPlanStart.TODAY)
        advanceUntilIdle()

        assertEquals(60, settingsFlow.value.blockMinutes)
        assertEquals(0, settingsFlow.value.startOffsetDays)
        val s = vm.state.value
        assertEquals(today, s.startDate)
        assertEquals(StudyPlanStart.TODAY, s.start)
        assertTrue(s.preview!!.blocks.all { it.minutes <= 60 })
    }

    @Test
    fun `eigenes Startdatum wird nicht gespeichert`() = runTest(dispatcher) {
        val vm = newVm()
        vm.open()
        advanceUntilIdle()
        val custom = today.plusDays(4)

        vm.setStart(StudyPlanStart.CUSTOM, custom)
        advanceUntilIdle()

        assertEquals(custom, vm.state.value.startDate)
        assertEquals(StudyPlanStart.CUSTOM, vm.state.value.start)
        assertEquals(1, settingsFlow.value.startOffsetDays)
        assertTrue(vm.state.value.preview!!.blocks.all {
            !it.start.atZone(zone).toLocalDate().isBefore(custom)
        })
    }

    @Test
    fun `aktiver Plan zeigt Anzahl und letztes Datum`() = runTest(dispatcher) {
        val start = now.plus(Duration.ofDays(3))
        upcomingBlocks.value = listOf(
            CustomEventEntity(
                id = 5, title = "Lernen: Datenbanken", startTime = start,
                endTime = start.plus(Duration.ofMinutes(90)),
                sourceKind = CustomEventEntity.SOURCE_STUDY_PLAN
            ),
            CustomEventEntity(
                id = 6, title = "Lernen: Datenbanken", startTime = start.plus(Duration.ofDays(2)),
                endTime = start.plus(Duration.ofDays(2)).plus(Duration.ofMinutes(90)),
                sourceKind = CustomEventEntity.SOURCE_STUDY_PLAN
            )
        )
        val vm = newVm()
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(2, s.activeBlockCount)
        assertEquals(today.plusDays(5), s.activeUntil)
        assertTrue(s.hasActivePlan)
    }

    @Test
    fun `oeffnen fuer eine bestimmte Klausur waehlt nur diese aus`() = runTest(dispatcher) {
        val vm = newVm()
        vm.open(preselectExamId = 2)
        advanceUntilIdle()
        assertEquals(listOf(false, true), vm.state.value.candidates.map { it.selected })
    }
}
