package de.transio.hiuni.feature.bib.data

import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.notifications.NotificationActionSpec
import de.transio.hiuni.core.notifications.NotificationPresenter
import de.transio.hiuni.core.notifications.data.NotificationKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Verdrahtung von [BibFavoriteWatcher]: Favoriten, Baseline und Raum-Alarm aus den
 * Stores, Entscheidung über [BibFreeSlotNotifier], Meldung samt optionalem
 * „Buchen"-Button über den [NotificationPresenter].
 */
class BibFavoriteWatcherTest {

    private val store = mockk<BibFavoritesStore>(relaxed = true)
    private val alarmStore = mockk<BibRoomAlarmStore>(relaxed = true)
    private val presenter = mockk<NotificationPresenter>(relaxed = true)
    private val casState = MutableStateFlow<CasState>(CasState.NeedsLogin)
    private val casSession = mockk<CasSession>(relaxed = true).also {
        every { it.state } returns casState
    }
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 10, 26)
    private val clock = Clock.fixed(LocalDateTime.of(today, LocalTime.of(9, 10)).atZone(zone).toInstant(), zone)

    private val alwaysOneHour = BibRoomAlarm(mode = BibAlarmMode.ALWAYS, durationMinutes = 60)

    private fun watcher() = BibFavoriteWatcher(store, alarmStore, casSession, presenter, clock)

    private fun snapshot(
        booked: Set<LocalTime> = emptySet(),
        myBookings: List<MyBooking> = emptyList()
    ): BibSnapshot {
        val slots = (0 until 24).map { i ->
            val start = LocalTime.of(8, 0).plusMinutes(30L * i)
            val status = if (start in booked) SlotStatus.BOOKED else SlotStatus.FREE
            SlotEntry(start, start.plusMinutes(30), status, bookable = status == SlotStatus.FREE)
        }
        val day = RoomDayAvailability(today, 102, slots)
        return BibSnapshot(
            fetchedAt = Instant.now(clock),
            today = today,
            roomsToday = listOf(day),
            myBookings = myBookings,
            allDays = mapOf((today to 102) to day)
        )
    }

    private fun freedAt1400() {
        coEvery { store.currentFavorites() } returns setOf(102)
        coEvery { store.bookedBaseline() } returns setOf(
            BibFreeSlotNotifier.slotKey(102, today, LocalTime.of(14, 0)),
            BibFreeSlotNotifier.slotKey(102, today, LocalTime.of(14, 30))
        )
    }

    @Test
    fun `ohne Favoriten wird nichts gemeldet und die Baseline geleert`() = runTest {
        coEvery { store.currentFavorites() } returns emptySet()

        watcher().onSnapshot(snapshot())

        coVerify { store.setBookedBaseline(emptySet()) }
        coVerify(exactly = 0) { presenter.present(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `frei gewordener Lieblings-Raum wird nach dem Speichern der Baseline gemeldet`() = runTest {
        freedAt1400()
        coEvery { alarmStore.current(today) } returns alwaysOneHour

        watcher().onSnapshot(snapshot())

        coVerifyOrder {
            store.setBookedBaseline(emptySet())
            presenter.present(
                kind = NotificationKind.BIB,
                title = "F102 ist heute ab 09:30 frei",
                body = "Frei bis 20:00 · Tippen zum Buchen",
                refKey = "bib_free_2026-10-26_102_0930",
                systemId = any(),
                actions = emptyList()
            )
        }
    }

    @Test
    fun `Alarm aus meldet nichts, speichert aber die Baseline`() = runTest {
        freedAt1400()
        coEvery { alarmStore.current(today) } returns BibRoomAlarm(mode = BibAlarmMode.OFF)

        watcher().onSnapshot(snapshot(booked = setOf(LocalTime.of(10, 0))))

        coVerify {
            store.setBookedBaseline(setOf(BibFreeSlotNotifier.slotKey(102, today, LocalTime.of(10, 0))))
        }
        coVerify(exactly = 0) { presenter.present(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `eingeloggt ohne eigene Buchung heute bekommt die Meldung einen Buchen-Button`() = runTest {
        freedAt1400()
        casState.value = CasState.Authenticated("tester", Instant.now(clock))
        coEvery { alarmStore.current(today) } returns alwaysOneHour.copy(durationMinutes = 120)

        watcher().onSnapshot(snapshot())

        coVerify {
            presenter.present(
                kind = NotificationKind.BIB,
                title = "F102 ist heute ab 09:30 frei",
                body = any(),
                refKey = any(),
                systemId = any(),
                actions = listOf(
                    NotificationActionSpec.BookBibRoom(102, today, LocalTime.of(9, 30), LocalTime.of(11, 30))
                )
            )
        }
    }

    @Test
    fun `mit eigener Buchung heute gibt es keinen Buchen-Button`() = runTest {
        freedAt1400()
        casState.value = CasState.Authenticated("tester", Instant.now(clock))
        coEvery { alarmStore.current(today) } returns alwaysOneHour
        val own = MyBooking("x", today, LocalTime.of(18, 0), LocalTime.of(19, 0), 101, "F101")

        watcher().onSnapshot(snapshot(myBookings = listOf(own)))

        coVerify {
            presenter.present(
                kind = NotificationKind.BIB,
                title = any(),
                body = any(),
                refKey = any(),
                systemId = any(),
                actions = emptyList()
            )
        }
    }

    @Test
    fun `Fehler beim Melden kippt den Check nicht`() = runTest {
        freedAt1400()
        coEvery { alarmStore.current(today) } returns alwaysOneHour
        coEvery { presenter.present(any(), any(), any(), any(), any(), any()) } throws IllegalStateException("kaputt")

        // Darf nicht werfen.
        watcher().onSnapshot(snapshot())
    }

    @Test
    fun `Fehler beim Lesen des Alarms meldet nichts, Baseline bleibt aktuell`() = runTest {
        freedAt1400()
        coEvery { alarmStore.current(any()) } throws IllegalStateException("DataStore kaputt")

        watcher().onSnapshot(snapshot())

        coVerify { store.setBookedBaseline(emptySet()) }
        coVerify(exactly = 0) { presenter.present(any(), any(), any(), any(), any(), any()) }
    }
}
