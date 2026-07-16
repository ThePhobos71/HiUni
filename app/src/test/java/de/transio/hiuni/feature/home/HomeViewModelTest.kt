package de.transio.hiuni.feature.home

import app.cash.turbine.test
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.UserProfile
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.data.NotificationLogRepository
import de.transio.hiuni.feature.bib.data.BibRepository
import de.transio.hiuni.feature.bib.data.BibUiData
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.courses.data.CourseRepository
import de.transio.hiuni.feature.email.data.EmailRepository
import de.transio.hiuni.feature.learnweb.data.LearnwebRepository
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.lsf.data.LsfExamsRepository
import de.transio.hiuni.feature.mensa.data.MensaRepository
import de.transio.hiuni.feature.movies.data.MoviesRepository
import de.transio.hiuni.feature.sport.data.SportRepository
import de.transio.hiuni.feature.todos.data.TodoEntity
import de.transio.hiuni.feature.todos.data.TodosRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val calendarRepository = mockk<CalendarRepository>(relaxed = true)
    private val mensaRepository = mockk<MensaRepository>(relaxed = true)
    private val moviesRepository = mockk<MoviesRepository>(relaxed = true)
    private val bibRepository = mockk<BibRepository>(relaxed = true)
    private val emailRepository = mockk<EmailRepository>(relaxed = true)
    private val courseRepository = mockk<CourseRepository>(relaxed = true)
    private val todosRepository = mockk<TodosRepository>(relaxed = true)
    private val notificationLogRepository = mockk<NotificationLogRepository>(relaxed = true)
    private val sportRepository = mockk<SportRepository>(relaxed = true)
    private val examsRepository = mockk<LsfExamsRepository>(relaxed = true)
    private val learnwebRepository = mockk<LearnwebRepository>(relaxed = true)
    private val casSession = mockk<CasSession>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)

    // Konfigurierbare Quellen für die relevanten Sektionen.
    private val coursesFlow = MutableStateFlow<List<CourseEntity>>(emptyList())
    private val examsFlow = MutableStateFlow<List<ExamEntity>>(emptyList())
    private val mealsFlow = MutableStateFlow(emptyList<de.transio.hiuni.feature.mensa.data.MealEntity>())
    private val emailFlow = MutableStateFlow(emptyList<de.transio.hiuni.feature.email.data.EmailEntity>())
    private val openTodosFlow = MutableStateFlow<List<TodoEntity>>(emptyList())
    private val profileFlow = MutableStateFlow(UserProfile.EMPTY)
    private val displayNameModeFlow = MutableStateFlow(SettingsDataStore.DISPLAY_NAME_MODE_FIRST)
    private val customDisplayNameFlow = MutableStateFlow("")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        every { calendarRepository.observeRange(any(), any()) } returns flowOf(emptyList())
        every { mensaRepository.observeForDate(any()) } returns mealsFlow
        every { moviesRepository.observeUpcoming() } returns flowOf(emptyList())
        every { bibRepository.state } returns MutableStateFlow(BibUiData())
        every { emailRepository.observeInbox() } returns emailFlow
        every { courseRepository.observeAll() } returns coursesFlow
        every { todosRepository.observeOpen(any()) } returns openTodosFlow
        every { todosRepository.observeOpenCount() } returns flowOf(0)
        every { notificationLogRepository.observeUnreadCount() } returns flowOf(0)
        every { sportRepository.countUpcoming() } returns flowOf(0)
        every { examsRepository.observeUpcoming(any()) } returns examsFlow
        every { learnwebRepository.observeCourses() } returns flowOf(emptyList())
        every { learnwebRepository.observeUpcomingAssignments() } returns flowOf(emptyList())
        every { casSession.profile } returns profileFlow
        every { settings.displayNameMode } returns displayNameModeFlow
        every { settings.customDisplayName } returns customDisplayNameFlow
        every { settings.mensaLocationId } returns flowOf(150)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = HomeViewModel(
        calendarRepository, mensaRepository, moviesRepository, bibRepository,
        emailRepository, courseRepository, todosRepository, notificationLogRepository,
        sportRepository, examsRepository, learnwebRepository, casSession, settings
    )

    private fun lsfCourse(lsfId: String?, name: String, abbreviation: String?) = CourseEntity(
        id = "c-${name.hashCode()}",
        name = name,
        professor = "Prof",
        credits = 5,
        semester = "SoSe 26",
        source = CourseEntity.SOURCE_LSF,
        lsfId = lsfId,
        moduleAbbreviation = abbreviation
    )

    private fun exam(number: String, date: LocalDate?) = ExamEntity(
        rowId = number.hashCode().toLong(),
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
    fun `courseShortNameByLsfId mappt LSF-Kurse auf Modulkuerzel`() = runTest {
        coursesFlow.value = listOf(
            lsfCourse(lsfId = "3204", name = "3204 Einführung", abbreviation = "IT-EINF1")
        )
        val vm = newVm()
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(mapOf("3204" to "IT-EINF1"), s.courseShortNameByLsfId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * REGRESSION: früher `!!`-Crashfalle. Ein LSF-Kurs OHNE lsfId darf das
     * Aggregieren des Home-States nicht crashen — der Eintrag wird still
     * übersprungen, valide Kurse bleiben im Mapping.
     */
    @Test
    fun `courseShortNameByLsfId ueberspringt LSF-Kurs ohne lsfId ohne Crash`() = runTest {
        coursesFlow.value = listOf(
            lsfCourse(lsfId = null, name = "Kaputt", abbreviation = "BROKEN"),
            lsfCourse(lsfId = "3204", name = "Gültig", abbreviation = "OK")
        )
        val vm = newVm()
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(mapOf("3204" to "OK"), s.courseShortNameByLsfId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `courseShortNameByLsfId faellt bei leerem Kuerzel auf Kursnamen zurueck`() = runTest {
        coursesFlow.value = listOf(lsfCourse(lsfId = "9", name = "Analysis", abbreviation = null))
        val vm = newVm()
        vm.state.test {
            assertEquals("Analysis", expectMostRecentItem().courseShortNameByLsfId["9"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Sektionen aus mehreren Repos werden im State aggregiert`() = runTest {
        val today = LocalDate.now()
        examsFlow.value = listOf(exam("1", today.plusDays(5)))
        emailFlow.value = listOf(
            de.transio.hiuni.feature.email.data.EmailEntity(
                uid = 1L,
                folder = "INBOX",
                fromAddress = "a@b.de",
                fromName = "A",
                subject = "Hallo",
                snippet = "…",
                bodyPlain = "Body",
                receivedAt = java.time.Instant.now(),
                isRead = false
            )
        )
        coursesFlow.value = listOf(lsfCourse(lsfId = "3204", name = "K", abbreviation = "OK"))
        val vm = newVm()
        vm.state.test {
            val s = expectMostRecentItem()
            // Verschiedene Repos landen gleichzeitig sichtbar im aggregierten State.
            assertEquals(1, s.upcomingExams.size)
            assertEquals(1, s.unreadEmails)
            assertEquals(mapOf("3204" to "OK"), s.courseShortNameByLsfId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Greeting nutzt Vornamen im FIRST-Modus`() = runTest {
        profileFlow.value = UserProfile.EMPTY.copy(
            vorname = "Kjell Heinrich", nachname = "Karstens", uid = "karstens"
        )
        displayNameModeFlow.value = SettingsDataStore.DISPLAY_NAME_MODE_FIRST
        val vm = newVm()
        vm.state.test {
            assertEquals("Kjell", expectMostRecentItem().greetingName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Greeting nutzt vollen Vornamen im ALL-Modus`() = runTest {
        profileFlow.value = UserProfile.EMPTY.copy(vorname = "Kjell Heinrich", uid = "karstens")
        displayNameModeFlow.value = SettingsDataStore.DISPLAY_NAME_MODE_ALL
        val vm = newVm()
        vm.state.test {
            assertEquals("Kjell Heinrich", expectMostRecentItem().greetingName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Greeting nutzt Custom-Namen im CUSTOM-Modus`() = runTest {
        displayNameModeFlow.value = SettingsDataStore.DISPLAY_NAME_MODE_CUSTOM
        customDisplayNameFlow.value = "Chef"
        val vm = newVm()
        vm.state.test {
            assertEquals("Chef", expectMostRecentItem().greetingName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Greeting faellt auf uid zurueck wenn Custom leer ist`() = runTest {
        profileFlow.value = UserProfile.EMPTY.copy(uid = "karstens")
        displayNameModeFlow.value = SettingsDataStore.DISPLAY_NAME_MODE_CUSTOM
        customDisplayNameFlow.value = "   "
        val vm = newVm()
        vm.state.test {
            assertEquals("karstens", expectMostRecentItem().greetingName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Greeting faellt auf Studi zurueck wenn kein Name verfuegbar`() = runTest {
        profileFlow.value = UserProfile.EMPTY
        displayNameModeFlow.value = SettingsDataStore.DISPLAY_NAME_MODE_FIRST
        val vm = newVm()
        vm.state.test {
            assertEquals("Studi", expectMostRecentItem().greetingName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Fehlt Kurs-Daten in EINEM Repo (leere Kursliste), dürfen andere Sektionen
     * (Klausuren, E-Mails) trotzdem vollständig befüllt werden. Das aggregierte
     * `combine` verliert bei leerem Teil-Flow nicht die übrigen Slots.
     */
    @Test
    fun `leeres Kurs-Repo leert nicht die anderen Sektionen`() = runTest {
        coursesFlow.value = emptyList()
        examsFlow.value = listOf(exam("42", LocalDate.now().plusDays(3)))
        val vm = newVm()
        vm.state.test {
            val s = expectMostRecentItem()
            assertTrue(s.courseShortNameByLsfId.isEmpty())
            assertEquals(1, s.upcomingExams.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleTodoDone delegiert an das TodosRepository mit invertiertem Status`() = runTest {
        val todo = TodoEntity(id = 5L, title = "Aufgabe", isDone = false)
        val vm = newVm()
        vm.toggleTodoDone(todo)
        coVerify { todosRepository.setDone(5L, true, any()) }
    }
}
