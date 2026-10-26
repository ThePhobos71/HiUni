package de.transio.hiuni.feature.bib.data

import de.transio.hiuni.core.notifications.NotificationPresenter
import de.transio.hiuni.core.notifications.data.NotificationKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Verdrahtung von [BibFavoriteWatcher]: Favoriten und Baseline aus dem Store,
 * Entscheidung über [BibFreeSlotNotifier], Meldung über den [NotificationPresenter].
 */
class BibFavoriteWatcherTest {

    private val store = mockk<BibFavoritesStore>(relaxed = true)
    private val presenter = mockk<NotificationPresenter>(relaxed = true)
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 10, 26)
    private val clock = Clock.fixed(LocalDateTime.of(today, LocalTime.of(9, 10)).atZone(zone).toInstant(), zone)

    private fun snapshot(booked: Set<LocalTime> = emptySet()): BibSnapshot {
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
            myBookings = emptyList(),
            allDays = mapOf((today to 102) to day)
        )
    }

    @Test
    fun `ohne Favoriten wird nichts gemeldet und die Baseline geleert`() = runTest {
        coEvery { store.currentFavorites() } returns emptySet()

        BibFavoriteWatcher(store, presenter, clock).onSnapshot(snapshot())

        coVerify { store.setBookedBaseline(emptySet()) }
        coVerify(exactly = 0) { presenter.present(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `frei gewordener Lieblings-Raum wird nach dem Speichern der Baseline gemeldet`() = runTest {
        coEvery { store.currentFavorites() } returns setOf(102)
        coEvery { store.bookedBaseline() } returns setOf(
            BibFreeSlotNotifier.slotKey(102, today, LocalTime.of(14, 0)),
            BibFreeSlotNotifier.slotKey(102, today, LocalTime.of(14, 30))
        )

        BibFavoriteWatcher(store, presenter, clock).onSnapshot(snapshot())

        coVerifyOrder {
            store.setBookedBaseline(emptySet())
            presenter.present(
                kind = NotificationKind.BIB,
                title = "F102 ist heute ab 09:30 frei",
                body = "Frei bis 20:00 · Tippen zum Buchen",
                refKey = "bib_free_2026-10-26_102_0930",
                systemId = any()
            )
        }
    }

    @Test
    fun `Fehler beim Melden kippt den Check nicht`() = runTest {
        coEvery { store.currentFavorites() } returns setOf(102)
        coEvery { store.bookedBaseline() } returns setOf(
            BibFreeSlotNotifier.slotKey(102, today, LocalTime.of(14, 0)),
            BibFreeSlotNotifier.slotKey(102, today, LocalTime.of(14, 30))
        )
        coEvery { presenter.present(any(), any(), any(), any(), any()) } throws IllegalStateException("kaputt")

        // Darf nicht werfen.
        BibFavoriteWatcher(store, presenter, clock).onSnapshot(snapshot())
    }
}
