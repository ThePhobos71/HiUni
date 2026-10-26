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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import de.transio.hiuni.core.common.VirtualClock
import de.transio.hiuni.feature.bib.data.BibSnapshot
import de.transio.hiuni.feature.bib.data.MyBooking
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.mensa.data.MealEntity
import de.transio.hiuni.feature.mensa.data.OpeningHourBlock
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

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
    private val openingHoursFlow = MutableStateFlow<List<OpeningHourBlock>>(emptyList())

    private val mainDispatcher = UnconfinedTestDispatcher()

    // Standard-Uhr für die Bestandstests: echte Startzeit, läuft mit der virtuellen
    // Testzeit weiter. Rollover-Tests setzen eine eigene Startzeit via [clockAt].
    private var clock: Clock = VirtualClock(mainDispatcher.scheduler, Instant.now(), ZoneId.systemDefault())

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)

        every { calendarRepository.observeRange(any(), any()) } returns flowOf(emptyList())
        every { mensaRepository.observeForDate(any()) } returns mealsFlow
        every { mensaRepository.observeOpeningHours() } returns openingHoursFlow
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
        sportRepository, examsRepository, learnwebRepository, casSession, settings, clock
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

    // ---------------------------------------------------------------------
    // Tageswechsel: Home muss um Mitternacht auf den neuen Tag umschalten,
    // auch wenn das ViewModel (und die Subscription) die ganze Zeit lebt.
    // ---------------------------------------------------------------------

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val day: LocalDate = LocalDate.of(2026, 10, 27)

    private fun clockAt(dateTime: LocalDateTime) {
        clock = VirtualClock(
            mainDispatcher.scheduler,
            dateTime.atZone(berlin).toInstant().minusMillis(mainDispatcher.scheduler.currentTime),
            berlin
        )
    }

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant =
        date.atTime(hour, minute).atZone(berlin).toInstant()

    private fun event(title: String, start: Instant) = CustomEventEntity(
        id = title.hashCode().toLong(),
        title = title,
        startTime = start,
        endTime = start.plusSeconds(90L * 60)
    )

    /** Kalender-Fake: liefert wie das echte Repo alle Events, deren Start im Fenster liegt. */
    private fun calendarWith(vararg events: CustomEventEntity) {
        every { calendarRepository.observeRange(any(), any()) } answers {
            val from = firstArg<Instant>()
            val to = secondArg<Instant>()
            flowOf(events.filter { !it.startTime.isBefore(from) && it.startTime.isBefore(to) }
                .sortedBy { it.startTime })
        }
    }

    private fun meal(date: LocalDate) = MealEntity(
        sourceId = "m-$date",
        locationId = 150,
        date = date,
        category = "Hauptgericht",
        name = "Essen am $date",
        description = null,
        priceStudentCents = 300,
        priceEmployeeCents = null,
        priceGuestCents = null,
        tags = ""
    )

    /** Startet eine dauerhafte Subscription (wie die sichtbare Home-UI) und liefert sie zurück. */
    private fun TestScope.subscribe(vm: HomeViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        testScheduler.runCurrent()
    }

    private fun TestScope.advanceMinutes(minutes: Long) {
        testScheduler.advanceTimeBy(minutes * 60_000)
        testScheduler.runCurrent()
    }

    @Test
    fun `Heute-Sektion und Datum rollen um Mitternacht auf den neuen Tag`() = runTest {
        clockAt(day.atTime(23, 59))
        val eventToday = event("Heute", at(day, 10))
        val eventTomorrow = event("Morgen", at(day.plusDays(1), 10))
        calendarWith(eventToday, eventTomorrow)
        val vm = newVm()
        subscribe(vm)

        assertEquals(day, vm.state.value.today)
        assertEquals(listOf(eventToday), vm.state.value.todayEvents)

        advanceMinutes(2) // 00:01 am Folgetag

        assertEquals(day.plusDays(1), vm.state.value.today)
        assertEquals(listOf(eventTomorrow), vm.state.value.todayEvents)
    }

    @Test
    fun `Mensa-Speiseplan rollt um Mitternacht auf den neuen Tag`() = runTest {
        clockAt(day.atTime(23, 59))
        every { mensaRepository.observeForDate(any()) } answers {
            flowOf(listOf(meal(firstArg())))
        }
        val vm = newVm()
        subscribe(vm)

        assertEquals(listOf(day), vm.state.value.todaysMeals.map { it.date })

        advanceMinutes(2)

        assertEquals(listOf(day.plusDays(1)), vm.state.value.todaysMeals.map { it.date })
    }

    @Test
    fun `Klausur von gestern faellt nach Mitternacht aus den anstehenden Klausuren`() = runTest {
        clockAt(day.atTime(23, 59))
        val examToday = exam("1", day)
        val examLater = exam("2", day.plusDays(5))
        // Wie das echte Repo: "heute" wird beim Aufruf von observeUpcoming eingefroren.
        every { examsRepository.observeUpcoming(any()) } answers {
            val today = LocalDate.now(clock)
            flowOf(listOf(examToday, examLater).filter { !it.examDate!!.isBefore(today) })
        }
        val vm = newVm()
        subscribe(vm)

        assertEquals(listOf(examToday, examLater), vm.state.value.upcomingExams)

        advanceMinutes(2)

        assertEquals(listOf(examLater), vm.state.value.upcomingExams)
    }

    @Test
    fun `Naechster Termin springt weiter sobald der aktuelle begonnen hat`() = runTest {
        clockAt(day.atTime(9, 0))
        val first = event("Erster", at(day, 9, 30))
        val second = event("Zweiter", at(day, 11, 0))
        calendarWith(first, second)
        val vm = newVm()
        subscribe(vm)

        assertEquals(first, vm.state.value.nextEvent)

        advanceMinutes(31) // 09:31, "Erster" läuft bereits

        assertEquals(second, vm.state.value.nextEvent)
    }

    @Test
    fun `Fenster fuer den naechsten Termin wandert mit dem Datum mit`() = runTest {
        clockAt(day.atTime(9, 0))
        // Liegt außerhalb des Fensters ab Erstellung, nach dem Tageswechsel aber innerhalb.
        val farAway = event("Später", at(day.plusDays(15), 8))
        calendarWith(farAway)
        val vm = newVm()
        subscribe(vm)

        assertEquals(null, vm.state.value.nextEvent)

        advanceMinutes(15L * 60 + 1) // 00:01 am Folgetag

        assertEquals(farAway, vm.state.value.nextEvent)
    }

    @Test
    fun `Naechste Bib-Buchung verschwindet sobald sie vorbei ist`() = runTest {
        clockAt(day.atTime(9, 50))
        val current = MyBooking("a", day, LocalTime.of(9, 0), LocalTime.of(10, 0), 1, "F101")
        val tomorrow = MyBooking("b", day.plusDays(1), LocalTime.of(12, 0), LocalTime.of(14, 0), 2, "F102")
        every { bibRepository.state } returns MutableStateFlow(
            BibUiData(
                snapshot = BibSnapshot(
                    fetchedAt = at(day, 8),
                    today = day,
                    roomsToday = emptyList(),
                    myBookings = listOf(current, tomorrow)
                )
            )
        )
        val vm = newVm()
        subscribe(vm)

        assertEquals(current, vm.state.value.nextBibBooking)

        advanceMinutes(11) // 10:01, Buchung abgelaufen

        assertEquals(tomorrow, vm.state.value.nextBibBooking)
    }

    // ── Mensa-Status aus den persistierten Öffnungszeiten ──

    /**
     * Location ohne Eintrag im globalen In-Memory-Cache von MensaHours. Andere Tests
     * (z. B. MensaRepositoryImplTest) wärmen den Cache für 150 an, und der Cache lebt
     * über Testklassen hinweg in derselben JVM.
     */
    private val uncachedLocation = 987_654

    /** Mo bis Fr mittags 11:00 bis 14:15, weicht bewusst von den Fallback-Slots (11:30 bis 14:30) ab. */
    private val persistedNoon = listOf(
        OpeningHourBlock(
            time = "noon",
            startDay = 1,
            endDay = 5,
            startTime = LocalTime.of(11, 0),
            endTime = LocalTime.of(14, 15)
        )
    )

    @Test
    fun `Mensa-Status kommt aus den persistierten Oeffnungszeiten ohne vorher geoeffnete Mensa`() = runTest {
        every { settings.mensaLocationId } returns flowOf(uncachedLocation)
        openingHoursFlow.value = persistedNoon
        clockAt(day.atTime(11, 10)) // Fallback (ab 11:30) hielte die Mensa noch für geschlossen
        val vm = newVm()
        subscribe(vm)

        assertTrue(vm.state.value.isMensaOpen)

        advanceMinutes(186) // 14:16, Fallback (bis 14:30) hielte sie noch für offen

        assertFalse(vm.state.value.isMensaOpen)
    }

    @Test
    fun `Mensa-Status folgt neu gespeicherten Oeffnungszeiten`() = runTest {
        every { settings.mensaLocationId } returns flowOf(uncachedLocation)
        clockAt(day.atTime(11, 10))
        val vm = newVm()
        subscribe(vm)

        assertFalse(vm.state.value.isMensaOpen) // noch nichts persistiert: Fallback-Slots

        openingHoursFlow.value = persistedNoon // z. B. nach einem Refresh
        testScheduler.runCurrent()

        assertTrue(vm.state.value.isMensaOpen)
    }

    // ── Live-Countdown im Hero ──

    @Test
    fun `Hero-Meta zaehlt mit dem Minutentakt herunter`() = runTest {
        clockAt(day.atTime(9, 0))
        calendarWith(event("Vorlesung", at(day, 9, 30)))
        val vm = newVm()
        subscribe(vm)

        fun meta() = nextEventMeta(vm.state.value.nextEvent, vm.state.value.now, berlin)
        assertEquals("In 30 Min · 09:30", meta())

        advanceMinutes(1)

        assertEquals("In 29 Min · 09:30", meta())
    }

    // ── Pop-in: Sections erst zeigen, wenn alle Quellen einmal geliefert haben ──

    @Test
    fun `solange eine Quelle noch nicht geliefert hat, gilt Home als nicht geladen`() = runTest {
        every { examsRepository.observeUpcoming(any()) } returns kotlinx.coroutines.flow.MutableSharedFlow()
        val vm = newVm()

        val job = launch(mainDispatcher) { vm.state.collect { } }
        assertFalse(vm.state.value.isContentLoaded)
        job.cancel()
    }

    @Test
    fun `nach der ersten kombinierten Emission ist Home geladen`() = runTest {
        val vm = newVm()
        vm.state.test {
            assertTrue(expectMostRecentItem().isContentLoaded)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
