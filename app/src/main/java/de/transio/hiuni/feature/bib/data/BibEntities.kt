package de.transio.hiuni.feature.bib.data

import java.time.LocalDate
import java.time.LocalTime

/** Status einer einzelnen 30-Min-Zelle im Belegungs-Grid.
 *
 * Farbreferenz aus dem Backend:
 *   #92CD00 → FREE (frei)
 *   #DF2E3B → BOOKED (von anderen belegt)
 *   #999999 → OWN_BOOKING (eigene Buchung, sichtbar nach Login)
 *   #e8e3e3 + title="geschlossen" → CLOSED (Bib zu)
 */
enum class SlotStatus { FREE, BOOKED, CLOSED, OWN_BOOKING }

/**
 * Aggregierte Belegung eines Raums an einem Tag. [slots] sind paarweise
 * sortiert nach Startzeit, lückenlos für den geöffneten Bereich des Tages.
 */
data class RoomDayAvailability(
    val date: LocalDate,
    val roomId: Int,
    val slots: List<SlotEntry>
) {
    val freeCount: Int get() = slots.count { it.status == SlotStatus.FREE }
    val bookedCount: Int get() = slots.count {
        it.status == SlotStatus.BOOKED || it.status == SlotStatus.OWN_BOOKING
    }
    val openCount: Int get() = slots.count { it.status != SlotStatus.CLOSED }

    /** 0..1, wie voll der Raum heute ist (alles außer FREE & CLOSED). */
    val utilization: Float
        get() = if (openCount == 0) 0f else bookedCount.toFloat() / openCount

    /** Nächster freier Block ab [from] (oder null). */
    fun nextFreeBlock(from: LocalTime): SlotEntry? =
        slots.firstOrNull { it.status == SlotStatus.FREE && !it.startTime.isBefore(from) }
}

/** Eine 30-Min-Zelle. `bookable` ist `true` nur wenn das Backend einen
 *  `getBookingForm(...)`-Click bereitstellt — vergangene Slots im aktuellen Tag
 *  sind weiterhin sichtbar (mit Farbe), aber nicht klickbar. */
data class SlotEntry(
    val startTime: LocalTime,
    val endTime: LocalTime,
    val status: SlotStatus,
    val bookable: Boolean = false
)

/** Eine eigene Buchung des aktuellen Users. */
data class MyBooking(
    val id: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val roomId: Int,
    val roomLabel: String
)

/** Komplette Schnappschuss-Sicht für UI: Belegung heute pro Raum + meine Buchungen.
 *  [allDays] enthält die volle 28-Tage-Matrix (Date × RoomId → Verfügbarkeit),
 *  damit der Buchungs-Dialog auch zukünftige Tage zeigen kann ohne erneuten Fetch. */
data class BibSnapshot(
    val fetchedAt: java.time.Instant,
    val today: LocalDate,
    val roomsToday: List<RoomDayAvailability>,
    val myBookings: List<MyBooking>,
    val allDays: Map<Pair<LocalDate, Int>, RoomDayAvailability> = emptyMap()
) {
    fun availableDates(): List<LocalDate> = allDays.keys
        .asSequence()
        .map { it.first }
        .distinct()
        .filter { !it.isBefore(today) }
        .sorted()
        .toList()

    fun forRoomDay(date: LocalDate, roomId: Int): RoomDayAvailability? = allDays[date to roomId]

    /**
     * Tagesöffnungszeiten aus dem Belegungs-Grid: frühester nicht-CLOSED Slot
     * bis letzter nicht-CLOSED Slot — über alle Räume hinweg, weil die Bib
     * nur als Ganzes öffnet/schließt. `null` wenn der Tag komplett zu ist
     * oder noch keine Daten vorliegen.
     */
    fun openHoursFor(date: LocalDate): Pair<LocalTime, LocalTime>? {
        val openSlots = allDays.entries
            .asSequence()
            .filter { it.key.first == date }
            .flatMap { it.value.slots.asSequence() }
            .filter { it.status != SlotStatus.CLOSED }
            .toList()
        if (openSlots.isEmpty()) return null
        val start = openSlots.minOf { it.startTime }
        val end = openSlots.maxOf { it.endTime }
        return start to end
    }
}
