package de.transio.hiuni.feature.movies

import androidx.lifecycle.SavedStateHandle
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.movies.data.MovieEntity
import de.transio.hiuni.feature.movies.data.MoviesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModelTest {

    private val repo = mockk<MoviesRepository>()
    private val handle = SavedStateHandle(mapOf("filmId" to "10", "sessionId" to "a"))
    private val calendar = FakeCalendarRepository()
    private val scheduler = mockk<NotificationScheduler>(relaxed = true)
    private val settings = mockk<SettingsDataStore> {
        every { notificationMinutesBefore } returns flowOf(30)
    }
    private val berlin = ZoneId.of("Europe/Berlin")

    // "Jetzt" ist der 26.10.2026, 12:00 in Münster.
    private val clock = Clock.fixed(
        LocalDateTime.of(2026, 10, 26, 12, 0).atZone(berlin).toInstant(),
        berlin
    )

    private fun viewModel() = MovieDetailViewModel(handle, repo, calendar, scheduler, settings, clock)

    private fun showing(
        title: String = "Dune: Part Two",
        subtitle: String? = null,
        date: LocalDate? = LocalDate.of(2026, 10, 28),
        time: LocalTime? = LocalTime.of(20, 30),
        durationMinutes: Int? = 166,
        isPast: Boolean = false
    ) = MovieEntity(
        filmId = "10",
        sessionId = "a",
        title = title,
        subtitle = subtitle,
        date = date,
        time = time,
        durationMinutes = durationMinutes,
        location = "Schloss, Hörsaal S1",
        isPast = isPast
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `TMDB-Details kommen aus Room, ohne eigene Netzwerk-Abfrage`() {
        coEvery { repo.findById("10", "a") } returns MovieEntity(
            filmId = "10",
            sessionId = "a",
            title = "Dune: Part Two",
            posterUrl = "https://image.tmdb.org/t/p/w780/p.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w1280/b.jpg",
            tmdbRating = 8.2,
            tmdbVoteCount = 5400,
            director = "Denis Villeneuve",
            castNames = listOf("Timothée Chalamet", "Zendaya")
        )

        val state = viewModel().state.value

        assertFalse(state.isLoading)
        assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg", state.backdropUrl)
        assertEquals(8.2, state.rating!!, 0.0001)
        assertEquals(5400, state.voteCount)
        assertEquals("Denis Villeneuve", state.crewDirector)
        assertEquals(listOf("Timothée Chalamet", "Zendaya"), state.cast)
    }

    @Test
    fun `Film ohne TMDB-Treffer zeigt leere Details`() {
        coEvery { repo.findById("10", "a") } returns MovieEntity(
            filmId = "10",
            sessionId = "a",
            title = "Kurzfilmnacht"
        )

        val state = viewModel().state.value

        assertNull(state.backdropUrl)
        assertNull(state.rating)
        assertEquals(emptyList<String>(), state.cast)
    }

    @Test
    fun `unbekannter Film beendet das Laden ohne Film`() {
        coEvery { repo.findById("10", "a") } returns null

        val state = viewModel().state.value

        assertFalse(state.isLoading)
        assertNull(state.movie)
    }

    @Test
    fun `Pin legt genau einen Kalendereintrag mit Filmdaten an`() {
        coEvery { repo.findById("10", "a") } returns showing()
        val vm = viewModel()
        assertTrue(vm.state.value.canPin)
        assertFalse(vm.state.value.isPinned)

        vm.pinToCalendar()

        val events = calendar.events.value
        assertEquals(1, events.size)
        val event = events.single()
        val start = LocalDateTime.of(2026, 10, 28, 20, 30).atZone(berlin).toInstant()
        assertEquals("Dune: Part Two", event.title)
        assertEquals(start, event.startTime)
        assertEquals(start.plusSeconds(166L * 60), event.endTime)
        assertEquals("Schloss, Hörsaal S1", event.location)
        assertEquals(CustomEventEntity.SOURCE_MOVIE_PIN, event.sourceKind)
        assertEquals("movie:10:a", event.sourceReference)
        assertEquals(30, event.reminderMinutesBefore)
        assertTrue(vm.state.value.isPinned)
        verify { scheduler.schedule(event.id, "Dune: Part Two", start.minusSeconds(30L * 60)) }
    }

    @Test
    fun `Ohne Laufzeit dauert der Eintrag zwei Stunden`() {
        coEvery { repo.findById("10", "a") } returns showing(durationMinutes = null)
        val vm = viewModel()

        vm.pinToCalendar()

        val event = calendar.events.value.single()
        assertEquals(event.startTime.plusSeconds(2L * 60 * 60), event.endTime)
    }

    @Test
    fun `Überraschungsfilm bekommt neutralen Titel`() {
        coEvery { repo.findById("10", "a") } returns showing(title = "Filmabend")
        val vm = viewModel()

        vm.pinToCalendar()

        assertEquals("Überraschungsfilm im Uni-Kino", calendar.events.value.single().title)
    }

    @Test
    fun `doppeltes Pinnen behält einen Eintrag`() {
        coEvery { repo.findById("10", "a") } returns showing()
        val vm = viewModel()

        vm.pinToCalendar()
        vm.pinToCalendar()

        assertEquals(1, calendar.events.value.size)
    }

    @Test
    fun `bestehender Pin wird beim Öffnen erkannt`() {
        coEvery { repo.findById("10", "a") } returns showing()
        viewModel().pinToCalendar()

        val reopened = viewModel()

        assertTrue(reopened.state.value.isPinned)
    }

    @Test
    fun `Unpin entfernt den Eintrag und den Reminder`() {
        coEvery { repo.findById("10", "a") } returns showing()
        val vm = viewModel()
        vm.pinToCalendar()
        val id = calendar.events.value.single().id

        vm.unpin()

        assertTrue(calendar.events.value.isEmpty())
        assertFalse(vm.state.value.isPinned)
        verify { scheduler.cancel(id) }
    }

    @Test
    fun `ohne Uhrzeit ist die Vorstellung nicht pinnbar`() {
        coEvery { repo.findById("10", "a") } returns showing(time = null)
        val vm = viewModel()

        assertFalse(vm.state.value.canPin)
        vm.pinToCalendar()

        assertTrue(calendar.events.value.isEmpty())
    }

    @Test
    fun `ohne Datum ist die Vorstellung nicht pinnbar`() {
        coEvery { repo.findById("10", "a") } returns showing(date = null)

        assertFalse(viewModel().state.value.canPin)
    }

    @Test
    fun `vergangene Vorstellung ist nicht pinnbar`() {
        coEvery { repo.findById("10", "a") } returns showing(date = LocalDate.of(2026, 10, 25))

        assertFalse(viewModel().state.value.canPin)
    }

    @Test
    fun `ohne Standard-Reminder wird kein Alarm geplant`() {
        every { settings.notificationMinutesBefore } returns kotlinx.coroutines.flow.flow {
            throw IllegalStateException("DataStore kaputt")
        }
        coEvery { repo.findById("10", "a") } returns showing()
        val vm = viewModel()

        vm.pinToCalendar()

        assertNull(calendar.events.value.single().reminderMinutesBefore)
        verify(exactly = 0) { scheduler.schedule(any(), any(), any(), any(), any()) }
    }
}

/** In-Memory-Kalender, gerade genug für Pin/Unpin. */
private class FakeCalendarRepository : CalendarRepository {
    val events = MutableStateFlow<List<CustomEventEntity>>(emptyList())
    private var nextId = 1L

    override fun observeRange(from: Instant, to: Instant): Flow<List<CustomEventEntity>> =
        events.map { list -> list.filter { it.startTime >= from && it.startTime < to } }

    override fun observeAll(): Flow<List<CustomEventEntity>> = events

    override suspend fun upsert(event: CustomEventEntity): Long {
        if (event.id == 0L) {
            val stored = event.copy(id = nextId++)
            events.value = events.value + stored
            return stored.id
        }
        events.value = events.value.map { if (it.id == event.id) event else it }
        return event.id
    }

    override suspend fun delete(event: CustomEventEntity) {
        events.value = events.value.filterNot { it.id == event.id }
    }

    override suspend fun findNextEvent(): CustomEventEntity? = null

    override suspend fun findBySourceReference(kind: String, ref: String): CustomEventEntity? =
        events.value.firstOrNull { it.sourceKind == kind && it.sourceReference == ref }

    override suspend fun findById(id: Long): CustomEventEntity? = events.value.firstOrNull { it.id == id }

    override suspend fun recurringMastersWithReminder(): List<CustomEventEntity> = emptyList()

    override suspend fun upcomingSingleShotWithReminder(now: Instant): List<CustomEventEntity> = emptyList()
}
