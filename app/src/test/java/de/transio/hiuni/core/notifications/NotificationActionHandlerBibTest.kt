package de.transio.hiuni.core.notifications

import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.AuthRequiredException
import de.transio.hiuni.core.notifications.NotificationActionSpec.BookBibRoom
import de.transio.hiuni.feature.bib.data.BibNotificationBooker
import de.transio.hiuni.feature.bib.data.BibRepository
import de.transio.hiuni.feature.bib.data.BibSnapshot
import de.transio.hiuni.feature.bib.data.BibUiData
import de.transio.hiuni.feature.bib.data.MyBooking
import de.transio.hiuni.feature.bib.data.RoomDayAvailability
import de.transio.hiuni.feature.bib.data.SlotEntry
import de.transio.hiuni.feature.bib.data.SlotStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * „Buchen" aus der Frei-Meldung: [NotificationActionHandler] → [BibNotificationBooker]
 * gegen ein Fake-[BibRepository]. Jeder Fehlerpfad endet als
 * [NotificationActionOutcome.Failed] mit eigener deutscher Meldung, nie als Exception.
 */
class NotificationActionHandlerBibTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 10, 26)
    private var clock = Clock.fixed(LocalDateTime.of(today, LocalTime.of(9, 10)).atZone(zone).toInstant(), zone)
    private val spec = BookBibRoom(102, today, LocalTime.of(14, 0), LocalTime.of(16, 0))

    private val casState = MutableStateFlow<CasState>(CasState.Authenticated("tester", Instant.EPOCH))
    private val casSession = mockk<CasSession>(relaxed = true).also { every { it.state } returns casState }
    private val repo = FakeBibRepository()

    private fun handler() = NotificationActionHandler(
        emailRepository = mockk(relaxed = true),
        settings = mockk(relaxed = true),
        todosRepository = mockk(relaxed = true),
        todoReminders = mockk(relaxed = true),
        scheduler = mockk(relaxed = true),
        bibBooker = BibNotificationBooker(repo, casSession, clock),
        clock = clock
    )

    private fun snapshot(
        booked: Set<LocalTime> = emptySet(),
        myBookings: List<MyBooking> = emptyList(),
        fetchedAt: Instant = clock.instant()
    ): BibSnapshot {
        val slots = (0 until 24).map { i ->
            val start = LocalTime.of(8, 0).plusMinutes(30L * i)
            val status = if (start in booked) SlotStatus.BOOKED else SlotStatus.FREE
            SlotEntry(start, start.plusMinutes(30), status, bookable = status == SlotStatus.FREE)
        }
        val day = RoomDayAvailability(today, 102, slots)
        return BibSnapshot(
            fetchedAt = fetchedAt,
            today = today,
            roomsToday = listOf(day),
            myBookings = myBookings,
            allDays = mapOf((today to 102) to day)
        )
    }

    private fun failedMessage(outcome: NotificationActionOutcome): String {
        assertTrue("erwartet Failed, war $outcome", outcome is NotificationActionOutcome.Failed)
        return (outcome as NotificationActionOutcome.Failed).message
    }

    @Test
    fun `Erfolg bucht genau den Bereich und bestaetigt mit Raum und Uhrzeit`() = runBlocking {
        repo.state.value = BibUiData(snapshot = snapshot())

        val outcome = handler().handle(spec)

        assertEquals(NotificationActionOutcome.Confirmed("F102 gebucht 14:00–16:00"), outcome)
        assertEquals(listOf(FakeBibRepository.Booking(today, LocalTime.of(14, 0), LocalTime.of(16, 0), 102)), repo.bookings)
        assertEquals(0, repo.refreshCalls)
    }

    @Test
    fun `veralteter Snapshot wird vor dem Buchen frisch geladen`() = runBlocking {
        repo.state.value = BibUiData(snapshot = snapshot(fetchedAt = clock.instant().minus(Duration.ofMinutes(10))))
        repo.refreshResult = { AppResult.Success(snapshot()) }

        val outcome = handler().handle(spec)

        assertEquals(1, repo.refreshCalls)
        assertTrue(outcome is NotificationActionOutcome.Confirmed)
    }

    @Test
    fun `ohne geladenen Snapshot wird erst geladen`() = runBlocking {
        repo.refreshResult = { AppResult.Success(snapshot()) }

        val outcome = handler().handle(spec)

        assertEquals(1, repo.refreshCalls)
        assertTrue(outcome is NotificationActionOutcome.Confirmed)
    }

    @Test
    fun `anonym geladener Snapshot zaehlt nicht als frisch`() = runBlocking {
        repo.state.value = BibUiData(snapshot = snapshot(), needsLogin = true)
        repo.refreshResult = { AppResult.Success(snapshot()) }

        handler().handle(spec)

        assertEquals(1, repo.refreshCalls)
    }

    @Test
    fun `ohne Login wird nicht gebucht`() = runBlocking {
        casState.value = CasState.NeedsReauth
        repo.state.value = BibUiData(snapshot = snapshot())

        val outcome = handler().handle(spec)

        assertEquals("Bitte in der App neu anmelden", failedMessage(outcome))
        assertTrue(repo.bookings.isEmpty())
    }

    @Test
    fun `eigene Buchung heute verhindert die zweite`() = runBlocking {
        val own = MyBooking("x", today, LocalTime.of(18, 0), LocalTime.of(19, 0), 101, "F101")
        repo.state.value = BibUiData(snapshot = snapshot(myBookings = listOf(own)))

        val outcome = handler().handle(spec)

        assertEquals("Du hast heute schon eine Buchung", failedMessage(outcome))
        assertTrue(repo.bookings.isEmpty())
    }

    @Test
    fun `inzwischen belegter Slot wird nicht gebucht`() = runBlocking {
        repo.state.value = BibUiData(snapshot = snapshot(booked = setOf(LocalTime.of(15, 0))))

        val outcome = handler().handle(spec)

        assertEquals("Raum ist inzwischen belegt", failedMessage(outcome))
        assertTrue(repo.bookings.isEmpty())
    }

    @Test
    fun `begonnener Zeitraum wird nicht mehr gebucht`() = runBlocking {
        clock = Clock.fixed(LocalDateTime.of(today, LocalTime.of(14, 5)).atZone(zone).toInstant(), zone)
        repo.state.value = BibUiData(snapshot = snapshot())

        val outcome = handler().handle(spec)

        assertEquals("Der Zeitraum hat schon begonnen", failedMessage(outcome))
        assertTrue(repo.bookings.isEmpty())
    }

    @Test
    fun `Server meldet Tageslimit`() = runBlocking {
        repo.state.value = BibUiData(snapshot = snapshot())
        repo.bookResult = AppResult.Failure(IllegalStateException("Sie dürfen pro Tag nur 1 Buchung vornehmen."))

        assertEquals("Du hast heute schon eine Buchung", failedMessage(handler().handle(spec)))
    }

    @Test
    fun `Server meldet belegt`() = runBlocking {
        repo.state.value = BibUiData(snapshot = snapshot())
        repo.bookResult = AppResult.Failure(IllegalStateException("Dieser Zeitraum ist bereits belegt."))

        assertEquals("Raum ist inzwischen belegt", failedMessage(handler().handle(spec)))
    }

    @Test
    fun `abgelaufene Anmeldung beim Buchen`() = runBlocking {
        repo.state.value = BibUiData(snapshot = snapshot())
        repo.bookResult = AppResult.Failure(AuthRequiredException("CAS-Login abgelaufen"))

        assertEquals("Bitte in der App neu anmelden", failedMessage(handler().handle(spec)))
    }

    @Test
    fun `Bib-Session laesst sich nicht erneuern`() = runBlocking {
        repo.state.value = BibUiData(snapshot = snapshot())
        repo.bookResult = AppResult.Failure(IllegalStateException("Bib-Login schlägt fehl auch nach Re-Auth"))

        assertEquals("Bitte in der App neu anmelden", failedMessage(handler().handle(spec)))
    }

    @Test
    fun `Netzfehler beim Buchen`() = runBlocking {
        repo.state.value = BibUiData(snapshot = snapshot())
        repo.bookResult = AppResult.Failure(IOException("timeout"))

        assertEquals("Buchung fehlgeschlagen", failedMessage(handler().handle(spec)))
    }

    @Test
    fun `Netzfehler beim Nachladen bucht nicht`() = runBlocking {
        repo.refreshResult = { AppResult.Failure(IOException("offline")) }

        assertEquals("Buchung fehlgeschlagen", failedMessage(handler().handle(spec)))
        assertTrue(repo.bookings.isEmpty())
    }

    @Test
    fun `abgelaufene Anmeldung beim Nachladen`() = runBlocking {
        repo.refreshResult = { AppResult.Failure(AuthRequiredException("Keine CAS-Session")) }

        assertEquals("Bitte in der App neu anmelden", failedMessage(handler().handle(spec)))
    }

    @Test
    fun `Exception im Repository wird zu Failed statt Crash`() = runBlocking {
        repo.state.value = BibUiData(snapshot = snapshot())
        repo.bookThrows = IllegalStateException("kaputt")

        assertEquals("Buchung fehlgeschlagen", failedMessage(handler().handle(spec)))
    }
}

/** Minimales In-Memory-[BibRepository] für die Buchungs-Aktion. */
private class FakeBibRepository : BibRepository {

    data class Booking(val date: LocalDate, val start: LocalTime, val end: LocalTime, val roomId: Int)

    override val state = MutableStateFlow(BibUiData())
    var refreshResult: () -> AppResult<BibSnapshot> = { AppResult.Failure(IOException("kein Netz im Test")) }
    var refreshCalls = 0
    var bookResult: AppResult<Unit> = AppResult.Success(Unit)
    var bookThrows: Throwable? = null
    val bookings = mutableListOf<Booking>()

    override suspend fun refresh(): AppResult<BibSnapshot> {
        refreshCalls++
        val result = refreshResult()
        if (result is AppResult.Success) state.value = BibUiData(snapshot = result.data)
        return result
    }

    override suspend fun refreshIfStale(ttlMillis: Long): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun warmUpFromCache() = Unit

    override suspend fun book(date: LocalDate, start: LocalTime, end: LocalTime, roomId: Int): AppResult<Unit> {
        bookThrows?.let { throw it }
        if (bookResult is AppResult.Success) bookings += Booking(date, start, end, roomId)
        return bookResult
    }

    override suspend fun cancel(booking: MyBooking): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun fetchEndTimes(date: LocalDate, start: LocalTime, roomId: Int): AppResult<List<LocalTime>> =
        AppResult.Success(emptyList())
}
