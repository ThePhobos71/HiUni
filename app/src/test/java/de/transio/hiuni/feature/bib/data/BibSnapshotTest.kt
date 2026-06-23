package de.transio.hiuni.feature.bib.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class BibSnapshotTest {

    private val today = LocalDate.of(2026, 5, 27)

    @Test
    fun `openHoursFor liefert Spanne der offenen Slots`() {
        val snap = snapshot(
            room(101, slot(8, 0, SlotStatus.FREE), slot(8, 30, SlotStatus.BOOKED)),
            room(103, slot(9, 0, SlotStatus.FREE), slot(18, 0, SlotStatus.CLOSED))
        )
        assertEquals(LocalTime.of(8, 0) to LocalTime.of(9, 30), snap.openHoursFor(today))
    }

    @Test
    fun `openHoursFor ignoriert CLOSED-Slots am Tagesende`() {
        // Bib schließt heute schon um 18:00 — Slots danach sind CLOSED und
        // dürfen die Range nicht nach oben ziehen.
        val snap = snapshot(
            room(
                101,
                slot(8, 0, SlotStatus.FREE),
                slot(17, 30, SlotStatus.FREE),
                slot(18, 0, SlotStatus.CLOSED),
                slot(19, 30, SlotStatus.CLOSED)
            )
        )
        assertEquals(LocalTime.of(8, 0) to LocalTime.of(18, 0), snap.openHoursFor(today))
    }

    @Test
    fun `openHoursFor liefert null wenn Tag komplett geschlossen ist`() {
        val snap = snapshot(
            room(101, slot(8, 0, SlotStatus.CLOSED), slot(8, 30, SlotStatus.CLOSED))
        )
        assertNull(snap.openHoursFor(today))
    }

    @Test
    fun `openHoursFor liefert null fuer unbekannten Tag`() {
        val snap = snapshot(room(101, slot(8, 0, SlotStatus.FREE)))
        assertNull(snap.openHoursFor(LocalDate.of(2099, 1, 1)))
    }

    private fun slot(hour: Int, minute: Int, status: SlotStatus): SlotEntry {
        val start = LocalTime.of(hour, minute)
        return SlotEntry(startTime = start, endTime = start.plusMinutes(30), status = status)
    }

    private fun room(id: Int, vararg slots: SlotEntry): RoomDayAvailability =
        RoomDayAvailability(date = today, roomId = id, slots = slots.toList())

    private fun snapshot(vararg rooms: RoomDayAvailability): BibSnapshot {
        val map = rooms.associateBy { it.date to it.roomId }
        return BibSnapshot(
            fetchedAt = Instant.now(),
            today = today,
            roomsToday = rooms.toList(),
            myBookings = emptyList(),
            allDays = map
        )
    }
}
