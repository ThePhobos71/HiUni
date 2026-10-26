package de.transio.hiuni.feature.calendar

import de.transio.hiuni.core.search.PendingSearchSelection
import de.transio.hiuni.core.search.SearchFixtures
import de.transio.hiuni.core.search.SearchSelectionHolder
import org.junit.Assert.assertNull
import java.time.ZoneId
import app.cash.turbine.test
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.core.sync.LsfSyncScheduler
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.courses.data.CourseRepository
import de.transio.hiuni.feature.learnweb.data.LearnwebRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Fokus laut Auftrag: NUR die lsfId-Mapping-/Null-Guard-Regression und Basis-State.
 * Kein tiefes Calendar-View-/Range-/Such-Testing — der Kalender ist bewusst außen vor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val repository = mockk<CalendarRepository>(relaxed = true)
    private val courseRepository = mockk<CourseRepository>(relaxed = true)
    private val learnwebRepository = mockk<LearnwebRepository>(relaxed = true)
    private val scheduler = mockk<NotificationScheduler>(relaxed = true)
    private val lsfSyncScheduler = mockk<LsfSyncScheduler>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)

    private val coursesFlow = MutableStateFlow<List<CourseEntity>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.observeRange(any(), any()) } returns flowOf(emptyList())
        every { courseRepository.observeAll() } returns coursesFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val selectionHolder = SearchSelectionHolder()

    private fun newVm() = CalendarViewModel(
        repository, courseRepository, learnwebRepository, scheduler, lsfSyncScheduler, settings,
        selectionHolder
    )

    private fun lsfCourse(
        lsfId: String?,
        name: String,
        abbreviation: String?
    ) = CourseEntity(
        id = "c-${name.hashCode()}",
        name = name,
        professor = "Prof",
        credits = 5,
        semester = "SoSe 26",
        source = CourseEntity.SOURCE_LSF,
        lsfId = lsfId,
        moduleAbbreviation = abbreviation
    )

    @Test
    fun `Basis-State default ist DAY-View mit leerer Eventliste`() = runTest {
        val vm = newVm()
        vm.state.test {
            val s = awaitItem()
            assertEquals(CalendarViewMode.DAY, s.viewMode)
            assertTrue(s.events.isEmpty())
            assertTrue(s.courseShortNameByLsfId.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `courseShortNameByLsfId mappt lsfId auf Modulkuerzel`() = runTest {
        coursesFlow.value = listOf(
            lsfCourse(lsfId = "3204", name = "3204 Einführung in die Informatik", abbreviation = "IT-EINF1")
        )
        val vm = newVm()
        vm.state.test {
            val s = awaitItem()
            assertEquals(mapOf("3204" to "IT-EINF1"), s.courseShortNameByLsfId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `courseShortNameByLsfId faellt auf Kursnamen zurueck wenn Kuerzel leer`() = runTest {
        coursesFlow.value = listOf(
            lsfCourse(lsfId = "5555", name = "Statistik I", abbreviation = "   "),
            lsfCourse(lsfId = "6666", name = "Analysis", abbreviation = null)
        )
        val vm = newVm()
        vm.state.test {
            val s = awaitItem()
            assertEquals("Statistik I", s.courseShortNameByLsfId["5555"])
            assertEquals("Analysis", s.courseShortNameByLsfId["6666"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * REGRESSION: Ein LSF-Kurs mit lsfId == null darf das Mapping NICHT crashen
     * (früher `!!`-Falle). Der Eintrag wird still übersprungen, andere Kurse
     * bleiben erhalten.
     */
    @Test
    fun `courseShortNameByLsfId ueberspringt LSF-Kurs ohne lsfId ohne Crash`() = runTest {
        coursesFlow.value = listOf(
            lsfCourse(lsfId = null, name = "Kaputter LSF-Kurs ohne ID", abbreviation = "BROKEN"),
            lsfCourse(lsfId = "3204", name = "Gültiger Kurs", abbreviation = "OK")
        )
        val vm = newVm()
        vm.state.test {
            val s = awaitItem()
            assertEquals(1, s.courseShortNameByLsfId.size)
            assertEquals(mapOf("3204" to "OK"), s.courseShortNameByLsfId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `courseShortNameByLsfId ignoriert USER-Kurse`() = runTest {
        coursesFlow.value = listOf(
            CourseEntity(
                id = "u1",
                name = "Selbst angelegter Kurs",
                professor = "",
                credits = 0,
                semester = "SoSe 26",
                source = CourseEntity.SOURCE_USER,
                lsfId = "irrelevant",
                moduleAbbreviation = "USR"
            )
        )
        val vm = newVm()
        vm.state.test {
            val s = awaitItem()
            assertTrue(s.courseShortNameByLsfId.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `displayTitleFor nutzt Kuerzel fuer LSF-Event und Titel fuer User-Event`() = runTest {
        coursesFlow.value = listOf(
            lsfCourse(lsfId = "3204", name = "3204 Einführung", abbreviation = "IT-EINF1")
        )
        val lsfEvent = CustomEventEntity(
            id = 1,
            title = "3204 Einführung in die Informatik",
            startTime = java.time.Instant.now(),
            endTime = java.time.Instant.now().plusSeconds(3600),
            courseLsfId = "3204"
        )
        val userEvent = CustomEventEntity(
            id = 2,
            title = "Zahnarzt",
            startTime = java.time.Instant.now(),
            endTime = java.time.Instant.now().plusSeconds(3600),
            courseLsfId = null
        )
        val vm = newVm()
        vm.state.test {
            val s = awaitItem()
            assertEquals("IT-EINF1", s.displayTitleFor(lsfEvent))
            assertEquals("Zahnarzt", s.displayTitleFor(userEvent))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectViewMode und selectDate aktualisieren die State-Flows`() = runTest {
        val vm = newVm()
        val target = LocalDate.of(2026, 9, 15)
        vm.selectViewMode(CalendarViewMode.MONTH)
        vm.selectDate(target)
        assertEquals(CalendarViewMode.MONTH, vm.viewMode.value)
        assertEquals(target, vm.selectedDate.value)
    }

    @Test
    fun `toggleViewMode zykelt DAY-WEEK-MONTH-DAY`() = runTest {
        val vm = newVm()
        assertEquals(CalendarViewMode.DAY, vm.viewMode.value)
        vm.toggleViewMode()
        assertEquals(CalendarViewMode.WEEK, vm.viewMode.value)
        vm.toggleViewMode()
        assertEquals(CalendarViewMode.MONTH, vm.viewMode.value)
        vm.toggleViewMode()
        assertEquals(CalendarViewMode.DAY, vm.viewMode.value)
    }

    @Test
    fun `openAdd und closeAddOrEdit steuern das Add-Sheet`() = runTest {
        val vm = newVm()
        vm.openAdd()
        vm.state.test {
            assertTrue(awaitItem().isAddSheetOpen)
            cancelAndIgnoreRemainingEvents()
        }
        vm.closeAddOrEdit()
        vm.state.test {
            val s = awaitItem()
            assertFalse(s.isAddSheetOpen)
            assertEquals(null, s.editing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `openEdit setzt editing und oeffnet das Sheet`() = runTest {
        val event = CustomEventEntity(
            id = 7,
            title = "Meeting",
            startTime = java.time.Instant.now(),
            endTime = java.time.Instant.now().plusSeconds(3600)
        )
        val vm = newVm()
        vm.openEdit(event)
        vm.state.test {
            val s = awaitItem()
            assertTrue(s.isAddSheetOpen)
            assertEquals(event, s.editing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Suche - setSearchQuery liefert Treffer und closeSearch leert die Query`() = runTest {
        val vm = newVm()
        vm.openSearch()
        vm.setSearchQuery("mathe")
        vm.state.test {
            val s = awaitItem()
            assertTrue(s.isSearchOpen)
            assertEquals("mathe", s.searchQuery)
            cancelAndIgnoreRemainingEvents()
        }
        vm.closeSearch()
        vm.state.test {
            val s = awaitItem()
            assertFalse(s.isSearchOpen)
            assertEquals("", s.searchQuery)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `iCal-Abgabe ohne Cache-URL faellt auf die Assignment-URL zurueck`() = runTest {
        val uid = "event_4875@learnweb.uni-hildesheim.de"
        io.mockk.coEvery { learnwebRepository.findICalEventUrl(uid) } returns null
        io.mockk.coEvery { learnwebRepository.findAssignmentUrl(4875L) } returns
            "https://learnweb.example/mod/assign/view.php?id=32906"
        val event = CustomEventEntity(
            id = 1L,
            title = "Hausaufgabe 2",
            startTime = java.time.Instant.EPOCH,
            endTime = java.time.Instant.EPOCH,
            sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ICAL,
            sourceReference = uid
        )

        assertEquals(
            "https://learnweb.example/mod/assign/view.php?id=32906",
            newVm().resolveLearnwebICalUrl(event)
        )
    }

    // ── Deep-Link aus der Spotlight-Suche ──────────────────────────────────

    @Test
    fun `vorgemerkter Termin springt in der Tagesansicht auf dessen Datum`() = runTest {
        val event = SearchFixtures.event()
        val expectedDate = event.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
        selectionHolder.stage(PendingSearchSelection.CalendarEvent(event))

        val vm = newVm()

        assertEquals(expectedDate, vm.selectedDate.value)
        assertEquals(CalendarViewMode.DAY, vm.viewMode.value)
        assertNull(selectionHolder.pending.value)
    }

    @Test
    fun `lebender Kalender in der Monatsansicht wechselt auf den Tag des Termins`() = runTest {
        val vm = newVm()
        vm.selectViewMode(CalendarViewMode.MONTH)
        val event = SearchFixtures.event()

        selectionHolder.stage(PendingSearchSelection.CalendarEvent(event))

        assertEquals(event.startTime.atZone(ZoneId.systemDefault()).toLocalDate(), vm.selectedDate.value)
        assertEquals(CalendarViewMode.DAY, vm.viewMode.value)
    }

    @Test
    fun `Termin-Vormerkung wird nur einmal angewendet`() = runTest {
        selectionHolder.stage(PendingSearchSelection.CalendarEvent(SearchFixtures.event()))
        newVm()

        val recreated = newVm()

        assertEquals(LocalDate.now(), recreated.selectedDate.value)
    }

    // ── Pop-in: „Keine Veranstaltungen" erst nach der ersten Room-Emission ──

    @Test
    fun `vor der ersten Room-Emission bleibt isLoading gesetzt`() = runTest {
        every { repository.observeRange(any(), any()) } returns MutableSharedFlow()
        val vm = newVm()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertTrue(vm.state.value.isLoading)
        job.cancel()
    }

    @Test
    fun `sichtbarer Bereich reicht fuer den ersten State, Such-Korpus erst beim Oeffnen`() = runTest {
        val vm = newVm()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertFalse(vm.state.value.isLoading)
        // Nur der sichtbare Tag/Woche/Monat; der 10-Monats-Such-Korpus bremst den Start nicht.
        verify(exactly = 1) { repository.observeRange(any(), any()) }

        vm.openSearch()
        verify(exactly = 2) { repository.observeRange(any(), any()) }
        job.cancel()
    }
}
