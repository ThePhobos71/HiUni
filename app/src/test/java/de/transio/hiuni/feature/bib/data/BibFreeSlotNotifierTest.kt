package de.transio.hiuni.feature.bib.data

import de.transio.hiuni.core.notifications.data.NotificationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Reine Diff-Tests für [BibFreeSlotNotifier]: wann wird ein Lieblings-Raum als
 * „wieder frei" gemeldet, und wie sieht die neue Baseline aus.
 *
 * Grid-Helfer: [day] baut einen Tag 8:00 bis 20:00 in 30-Min-Slots, alle FREE,
 * außer den explizit übergebenen Startzeiten.
 */
class BibFreeSlotNotifierTest {

    private val today = LocalDate.of(2026, 10, 26)
    private val tomorrow = today.plusDays(1)
    private val morning = LocalDateTime.of(today, LocalTime.of(9, 10))

    private fun t(hhmm: String): LocalTime = LocalTime.parse(hhmm)

    private fun day(
        date: LocalDate,
        roomId: Int,
        booked: List<String> = emptyList(),
        own: List<String> = emptyList()
    ): Pair<Pair<LocalDate, Int>, RoomDayAvailability> {
        val slots = (0 until 24).map { i ->
            val start = LocalTime.of(8, 0).plusMinutes(30L * i)
            val key = "%02d:%02d".format(start.hour, start.minute)
            val status = when (key) {
                in booked -> SlotStatus.BOOKED
                in own -> SlotStatus.OWN_BOOKING
                else -> SlotStatus.FREE
            }
            SlotEntry(start, start.plusMinutes(30), status, bookable = status == SlotStatus.FREE)
        }
        return (date to roomId) to RoomDayAvailability(date, roomId, slots)
    }

    private fun keys(roomId: Int, date: LocalDate, vararg starts: String): Set<String> =
        starts.map { BibFreeSlotNotifier.slotKey(roomId, date, t(it)) }.toSet()

    @Test
    fun `leere Baseline meldet nichts, merkt sich aber die belegten Slots`() {
        val days = mapOf(day(today, 102, booked = listOf("14:00", "14:30")))

        val decision = BibFreeSlotNotifier.decide(days, setOf(102), emptySet(), morning)

        assertTrue(decision.pushes.isEmpty())
        assertEquals(keys(102, today, "14:00", "14:30"), decision.bookedBaseline)
    }

    @Test
    fun `stornierte Buchung im Lieblings-Raum wird gemeldet`() {
        // 13:30 ist weiterhin belegt, damit der frei gewordene Block genau die
        // stornierte Buchung ist. Ohne diese Grenze liefe der Block ab 09:30
        // (siehe `Block reicht ueber die ehemals belegten Slots hinaus`).
        val previous = keys(102, today, "13:30", "14:00", "14:30", "15:00", "15:30")
        val days = mapOf(day(today, 102, booked = listOf("13:30", "16:00", "16:30")))

        val decision = BibFreeSlotNotifier.decide(days, setOf(102), previous, morning)

        assertEquals(1, decision.pushes.size)
        val push = decision.pushes.single()
        assertEquals(102, push.roomId)
        assertEquals("F102 ist heute ab 14:00 frei", push.title)
        assertEquals("Frei bis 16:00 · Tippen zum Buchen", push.body)
        assertEquals(NotificationKind.BIB, push.kind)
        assertEquals("bib_free_2026-10-26_102_1400", push.refKey)
    }

    @Test
    fun `Block reicht ueber die ehemals belegten Slots hinaus`() {
        // 14:00 bis 15:00 war belegt, davor und danach war schon frei: der Block
        // läuft vom ersten freien Slot nach jetzt bis zur nächsten Belegung.
        val previous = keys(102, today, "14:00", "14:30")
        val days = mapOf(day(today, 102, booked = listOf("12:00", "16:00")))

        val push = BibFreeSlotNotifier.decide(days, setOf(102), previous, morning).pushes.single()

        assertEquals("F102 ist heute ab 12:30 frei", push.title)
        assertEquals("Frei bis 16:00 · Tippen zum Buchen", push.body)
    }

    @Test
    fun `weiterhin freier Slot wird nicht erneut gemeldet`() {
        // Vorheriger Check: frei (also nicht in der Belegt-Baseline). Jetzt: frei.
        val days = mapOf(day(today, 102, booked = listOf("10:00")))
        val first = BibFreeSlotNotifier.decide(days, setOf(102), emptySet(), morning)

        val second = BibFreeSlotNotifier.decide(days, setOf(102), first.bookedBaseline, morning)

        assertTrue(second.pushes.isEmpty())
        assertEquals(first.bookedBaseline, second.bookedBaseline)
    }

    @Test
    fun `gemeldeter Slot wird beim naechsten Check nicht nochmal gemeldet`() {
        val previous = keys(102, today, "14:00", "14:30")
        val days = mapOf(day(today, 102))
        val first = BibFreeSlotNotifier.decide(days, setOf(102), previous, morning)
        assertEquals(1, first.pushes.size)

        val second = BibFreeSlotNotifier.decide(days, setOf(102), first.bookedBaseline, morning)

        assertTrue(second.pushes.isEmpty())
    }

    @Test
    fun `frei gewordener Slot der wieder belegt wird meldet nichts und landet in der Baseline`() {
        val days = mapOf(day(today, 102, booked = listOf("14:00", "14:30")))

        val decision = BibFreeSlotNotifier.decide(days, setOf(102), emptySet(), morning)

        assertTrue(decision.pushes.isEmpty())
        assertEquals(keys(102, today, "14:00", "14:30"), decision.bookedBaseline)

        // Wird er danach erneut storniert, ist das eine neue Meldung wert.
        val freedAgain = BibFreeSlotNotifier.decide(
            mapOf(day(today, 102)), setOf(102), decision.bookedBaseline, morning
        )
        assertEquals(1, freedAgain.pushes.size)
    }

    @Test
    fun `mehrere Lieblings-Raeume bekommen je eine Meldung, andere Raeume keine`() {
        val previous = keys(101, today, "10:00", "10:30") +
            keys(103, today, "17:00", "17:30") +
            keys(105, today, "12:00", "12:30")
        val days = mapOf(day(today, 101), day(today, 103), day(today, 105))

        val decision = BibFreeSlotNotifier.decide(days, setOf(101, 103), previous, morning)

        assertEquals(listOf(101, 103), decision.pushes.map { it.roomId })
        assertEquals("F101 ist heute ab 09:30 frei", decision.pushes[0].title)
        assertEquals("F103 ist heute ab 09:30 frei", decision.pushes[1].title)
    }

    @Test
    fun `mehrere frei gewordene Bloecke im selben Raum ergeben eine Meldung fuer den fruehesten`() {
        val previous = keys(102, today, "11:00", "11:30", "15:00", "15:30")
        val days = mapOf(day(today, 102, booked = listOf("10:00", "12:00", "14:00", "16:00")))

        val decision = BibFreeSlotNotifier.decide(days, setOf(102), previous, morning)

        val push = decision.pushes.single()
        assertEquals("F102 ist heute ab 10:30 frei", push.title)
        assertEquals("Frei bis 12:00 · Tippen zum Buchen", push.body)
    }

    @Test
    fun `vergangene Slots werden ignoriert`() {
        val previous = keys(102, today, "09:00", "09:30", "10:00")
        val days = mapOf(day(today, 102, booked = listOf("10:30")))
        val noon = LocalDateTime.of(today, LocalTime.of(10, 5))

        // Frei geworden sind 9:00 bis 10:30, ab jetzt (10:05) bleibt davon nichts
        // Buchbares übrig: 10:00 hat schon begonnen.
        val decision = BibFreeSlotNotifier.decide(days, setOf(102), previous, noon)

        assertTrue(decision.pushes.isEmpty())
    }

    @Test
    fun `Block wird auf die Zeit ab jetzt gekuerzt`() {
        val previous = keys(102, today, "13:00", "13:30", "14:00", "14:30")
        val days = mapOf(day(today, 102, booked = listOf("12:30", "15:00")))
        val now = LocalDateTime.of(today, LocalTime.of(13, 40))

        val push = BibFreeSlotNotifier.decide(days, setOf(102), previous, now).pushes.single()

        assertEquals("F102 ist heute ab 14:00 frei", push.title)
        assertEquals("Frei bis 15:00 · Tippen zum Buchen", push.body)
    }

    @Test
    fun `weniger als eine Stunde frei reicht nicht fuer eine Meldung`() {
        val previous = keys(102, today, "14:00")
        val days = mapOf(day(today, 102, booked = listOf("13:30", "14:30")))

        val decision = BibFreeSlotNotifier.decide(days, setOf(102), previous, morning)

        assertTrue(decision.pushes.isEmpty())
    }

    @Test
    fun `halbe Stunde frei neben schon freiem Slot ergibt eine Stunde und wird gemeldet`() {
        val previous = keys(102, today, "14:00")
        val days = mapOf(day(today, 102, booked = listOf("13:30", "15:00")))

        val push = BibFreeSlotNotifier.decide(days, setOf(102), previous, morning).pushes.single()

        assertEquals("F102 ist heute ab 14:00 frei", push.title)
        assertEquals("Frei bis 15:00 · Tippen zum Buchen", push.body)
    }

    @Test
    fun `eigene stornierte Buchung loest keine Meldung aus`() {
        // Vorher OWN_BOOKING: landet nicht in der Belegt-Baseline.
        val before = mapOf(day(today, 102, own = listOf("14:00", "14:30")))
        val baseline = BibFreeSlotNotifier.decide(before, setOf(102), emptySet(), morning).bookedBaseline
        assertTrue(baseline.isEmpty())

        val after = BibFreeSlotNotifier.decide(mapOf(day(today, 102)), setOf(102), baseline, morning)

        assertTrue(after.pushes.isEmpty())
    }

    @Test
    fun `morgen frei gewordene Slots werden nicht gemeldet, aber fuer den Tageswechsel gemerkt`() {
        val previous = keys(102, tomorrow, "10:00", "10:30")
        val days = mapOf(
            day(today, 102),
            day(tomorrow, 102, booked = listOf("12:00"))
        )

        val decision = BibFreeSlotNotifier.decide(days, setOf(102), previous, morning)

        assertTrue(decision.pushes.isEmpty())
        assertEquals(keys(102, tomorrow, "12:00"), decision.bookedBaseline)
    }

    @Test
    fun `Tageswechsel nutzt die gestern gemerkten Slots von heute`() {
        // Gestern Abend gesehen: heute 10:00 bis 11:00 belegt. Heute früh storniert.
        val yesterdayEvening = BibFreeSlotNotifier.decide(
            mapOf(day(today.minusDays(1), 102), day(today, 102, booked = listOf("10:00", "10:30"))),
            setOf(102),
            emptySet(),
            LocalDateTime.of(today.minusDays(1), LocalTime.of(19, 0))
        )

        val decision = BibFreeSlotNotifier.decide(
            mapOf(day(today, 102)), setOf(102), yesterdayEvening.bookedBaseline, morning
        )

        assertEquals("F102 ist heute ab 09:30 frei", decision.pushes.single().title)
    }

    @Test
    fun `Baseline enthaelt nur Lieblings-Raeume und nur heute und morgen`() {
        val days = mapOf(
            day(today, 101, booked = listOf("10:00")),
            day(today, 102, booked = listOf("11:00")),
            day(tomorrow, 101, booked = listOf("12:00")),
            day(tomorrow.plusDays(1), 101, booked = listOf("13:00"))
        )

        val decision = BibFreeSlotNotifier.decide(days, setOf(101), emptySet(), morning)

        assertEquals(
            keys(101, today, "10:00") + keys(101, tomorrow, "12:00"),
            decision.bookedBaseline
        )
    }

    @Test
    fun `ohne Favoriten gibt es weder Meldungen noch Baseline`() {
        val previous = keys(102, today, "14:00", "14:30")
        val days = mapOf(day(today, 102))

        val decision = BibFreeSlotNotifier.decide(days, emptySet(), previous, morning)

        assertTrue(decision.pushes.isEmpty())
        assertTrue(decision.bookedBaseline.isEmpty())
    }
}
