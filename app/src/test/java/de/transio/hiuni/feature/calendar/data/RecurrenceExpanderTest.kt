package de.transio.hiuni.feature.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Tests für [RecurrenceExpander]. Wir fixieren die Zone auf Europe/Berlin, damit
 * die Tests robust gegen die Build-Umgebung sind (CI läuft typischerweise in UTC).
 */
class RecurrenceExpanderTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    private fun instant(date: LocalDate, time: LocalTime): Instant =
        LocalDateTime.of(date, time).atZone(berlin).toInstant()

    private fun master(
        start: Instant,
        end: Instant,
        rule: String?
    ): CustomEventEntity = CustomEventEntity(
        id = 1L,
        title = "Mathe-VL",
        description = null,
        location = "F101",
        startTime = start,
        endTime = end,
        sourceKind = CustomEventEntity.SOURCE_USER,
        sourceReference = null,
        reminderMinutesBefore = null,
        courseLsfId = null,
        recurrenceRule = rule
    )

    /* ──────────────────────────────────────────────────────────────────
     * Single-shot (kein recurrenceRule)
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `single-shot event inside window returns master once`() {
        val start = instant(LocalDate.of(2026, 6, 24), LocalTime.of(10, 0))
        val end = instant(LocalDate.of(2026, 6, 24), LocalTime.of(12, 0))
        val ev = master(start, end, rule = null)
        val from = instant(LocalDate.of(2026, 6, 22), LocalTime.MIDNIGHT)
        val to = instant(LocalDate.of(2026, 6, 29), LocalTime.MIDNIGHT)

        val result = RecurrenceExpander.expand(ev, from, to, berlin)

        assertEquals(1, result.size)
        assertEquals(start, result.first().startTime)
    }

    @Test
    fun `single-shot event outside window returns empty`() {
        val start = instant(LocalDate.of(2026, 1, 1), LocalTime.of(10, 0))
        val end = instant(LocalDate.of(2026, 1, 1), LocalTime.of(12, 0))
        val ev = master(start, end, rule = null)
        val from = instant(LocalDate.of(2026, 6, 22), LocalTime.MIDNIGHT)
        val to = instant(LocalDate.of(2026, 6, 29), LocalTime.MIDNIGHT)

        val result = RecurrenceExpander.expand(ev, from, to, berlin)

        assertTrue("Expected empty, got $result", result.isEmpty())
    }

    /* ──────────────────────────────────────────────────────────────────
     * WEEKLY
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `WEEKLY Mathe-VL jeden Mittwoch 10-12 bis 31_07 expandiert auf 5 Wochen im Juni-Juli-Fenster`() {
        // Master: Mi, 24.06.2026, 10:00 — 12:00
        val masterStart = instant(LocalDate.of(2026, 6, 24), LocalTime.of(10, 0))
        val masterEnd = instant(LocalDate.of(2026, 6, 24), LocalTime.of(12, 0))
        val rule = RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            interval = 1,
            byDays = null, // = der Wochentag von startEpoch (Mittwoch)
            until = LocalDate.of(2026, 7, 31)
        ).toJsonString()
        val ev = master(masterStart, masterEnd, rule)

        // Fenster: 22.06.2026 — 03.08.2026 (deckt alle Mittwoch-Vorkommen)
        val from = instant(LocalDate.of(2026, 6, 22), LocalTime.MIDNIGHT)
        val to = instant(LocalDate.of(2026, 8, 3), LocalTime.MIDNIGHT)

        val result = RecurrenceExpander.expand(ev, from, to, berlin)

        // Mittwoche zwischen 24.06.2026 (inkl) und 31.07.2026 (exkl):
        // 24.06, 01.07, 08.07, 15.07, 22.07, 29.07 → 6 Vorkommen.
        val expectedDates = listOf(
            LocalDate.of(2026, 6, 24),
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 8),
            LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 7, 22),
            LocalDate.of(2026, 7, 29)
        )
        assertEquals(
            "WEEKLY-Expansion sollte ${expectedDates.size} Occurrences haben",
            expectedDates.size,
            result.size
        )
        val gotDates = result.map { it.startTime.atZone(berlin).toLocalDate() }
        assertEquals(expectedDates, gotDates)
        // Alle Occurrences haben gleiche Master-id + gleiche Dauer (2h).
        result.forEach { occ ->
            assertEquals(1L, occ.id)
            assertEquals("Mathe-VL", occ.title)
            assertEquals(2L * 3600 * 1000, occ.endTime.toEpochMilli() - occ.startTime.toEpochMilli())
        }
    }

    @Test
    fun `WEEKLY byDays MO,WE liefert beide Wochentage pro Woche`() {
        // Master: Mi, 24.06.2026 — byDays Mo+Mi → ab Master fängt Mi an, in der nächsten Woche kommt Mo zuerst.
        val masterStart = instant(LocalDate.of(2026, 6, 24), LocalTime.of(10, 0))
        val masterEnd = instant(LocalDate.of(2026, 6, 24), LocalTime.of(11, 0))
        val rule = RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            interval = 1,
            byDays = listOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY),
            until = LocalDate.of(2026, 7, 8) // exklusiv
        ).toJsonString()
        val ev = master(masterStart, masterEnd, rule)
        val from = instant(LocalDate.of(2026, 6, 22), LocalTime.MIDNIGHT)
        val to = instant(LocalDate.of(2026, 7, 31), LocalTime.MIDNIGHT)

        val result = RecurrenceExpander.expand(ev, from, to, berlin)
        val gotDates = result.map { it.startTime.atZone(berlin).toLocalDate() }

        // Erwartet: Mi 24.06 (Master-Woche, Montag liegt vor Master und wird übersprungen),
        //          Mo 29.06, Mi 01.07, Mo 06.07. Mi 08.07 ist exklusiv über until.
        val expected = listOf(
            LocalDate.of(2026, 6, 24),
            LocalDate.of(2026, 6, 29),
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 6)
        )
        assertEquals(expected, gotDates)
    }

    @Test
    fun `WEEKLY interval=2 ueberspringt jede zweite Woche`() {
        val masterStart = instant(LocalDate.of(2026, 6, 24), LocalTime.of(10, 0))
        val masterEnd = instant(LocalDate.of(2026, 6, 24), LocalTime.of(11, 0))
        val rule = RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            interval = 2,
            byDays = null,
            until = LocalDate.of(2026, 9, 1)
        ).toJsonString()
        val ev = master(masterStart, masterEnd, rule)
        val from = instant(LocalDate.of(2026, 6, 1), LocalTime.MIDNIGHT)
        val to = instant(LocalDate.of(2026, 10, 1), LocalTime.MIDNIGHT)

        val result = RecurrenceExpander.expand(ev, from, to, berlin)
        val gotDates = result.map { it.startTime.atZone(berlin).toLocalDate() }

        // 24.06 → +14d → 08.07 → 22.07 → 05.08 → 19.08. 02.09 ist > until exklusiv.
        val expected = listOf(
            LocalDate.of(2026, 6, 24),
            LocalDate.of(2026, 7, 8),
            LocalDate.of(2026, 7, 22),
            LocalDate.of(2026, 8, 5),
            LocalDate.of(2026, 8, 19)
        )
        assertEquals(expected, gotDates)
    }

    /* ──────────────────────────────────────────────────────────────────
     * DAILY
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `DAILY 5 Tage Fenster liefert 5 Occurrences`() {
        val masterStart = instant(LocalDate.of(2026, 6, 24), LocalTime.of(9, 0))
        val masterEnd = instant(LocalDate.of(2026, 6, 24), LocalTime.of(9, 30))
        val rule = RecurrenceRule(
            freq = RecurrenceRule.Freq.DAILY,
            interval = 1,
            until = LocalDate.of(2026, 6, 29)
        ).toJsonString()
        val ev = master(masterStart, masterEnd, rule)
        val from = instant(LocalDate.of(2026, 6, 20), LocalTime.MIDNIGHT)
        val to = instant(LocalDate.of(2026, 7, 1), LocalTime.MIDNIGHT)

        val result = RecurrenceExpander.expand(ev, from, to, berlin)
        val gotDates = result.map { it.startTime.atZone(berlin).toLocalDate() }
        // 24.06, 25.06, 26.06, 27.06, 28.06 — 29.06 ist exklusiv.
        assertEquals(
            listOf(
                LocalDate.of(2026, 6, 24),
                LocalDate.of(2026, 6, 25),
                LocalDate.of(2026, 6, 26),
                LocalDate.of(2026, 6, 27),
                LocalDate.of(2026, 6, 28)
            ),
            gotDates
        )
    }

    /* ──────────────────────────────────────────────────────────────────
     * MONTHLY
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `MONTHLY 1-Tag-im-Monat ueber 6 Monate liefert 6 Occurrences`() {
        // Bib-Schließtag: 1.6.2026, jeden 1. eines Monats.
        val masterStart = instant(LocalDate.of(2026, 6, 1), LocalTime.of(0, 0))
        val masterEnd = instant(LocalDate.of(2026, 6, 1), LocalTime.of(23, 59))
        val rule = RecurrenceRule(
            freq = RecurrenceRule.Freq.MONTHLY,
            interval = 1,
            until = LocalDate.of(2026, 12, 2) // exklusiv → 01.12 ist letzte Inklusion
        ).toJsonString()
        val ev = master(masterStart, masterEnd, rule)
        val from = instant(LocalDate.of(2026, 5, 1), LocalTime.MIDNIGHT)
        val to = instant(LocalDate.of(2027, 1, 1), LocalTime.MIDNIGHT)

        val result = RecurrenceExpander.expand(ev, from, to, berlin)
        val gotDates = result.map { it.startTime.atZone(berlin).toLocalDate() }
        assertEquals(
            listOf(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 12, 1)
            ),
            gotDates
        )
    }

    @Test
    fun `MONTHLY 31_Januar skippt Februar`() {
        val masterStart = instant(LocalDate.of(2026, 1, 31), LocalTime.of(9, 0))
        val masterEnd = instant(LocalDate.of(2026, 1, 31), LocalTime.of(10, 0))
        val rule = RecurrenceRule(
            freq = RecurrenceRule.Freq.MONTHLY,
            interval = 1,
            until = LocalDate.of(2026, 5, 1)
        ).toJsonString()
        val ev = master(masterStart, masterEnd, rule)
        val from = instant(LocalDate.of(2026, 1, 1), LocalTime.MIDNIGHT)
        val to = instant(LocalDate.of(2026, 6, 1), LocalTime.MIDNIGHT)

        val result = RecurrenceExpander.expand(ev, from, to, berlin)
        val gotDates = result.map { it.startTime.atZone(berlin).toLocalDate() }
        // Erwartet: 31.01, (Feb skip — 31. existiert nicht), 31.03, (April hat nur 30 — skip).
        // Mai hat 31 → wäre drin, aber 31.05 > until=01.05 exklusiv.
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 3, 31)
            ),
            gotDates
        )
    }

    /* ──────────────────────────────────────────────────────────────────
     * Edge cases
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `corrupted recurrence rule falls back to single-shot`() {
        val start = instant(LocalDate.of(2026, 6, 24), LocalTime.of(10, 0))
        val end = instant(LocalDate.of(2026, 6, 24), LocalTime.of(12, 0))
        val ev = master(start, end, rule = "{not valid json")
        val from = instant(LocalDate.of(2026, 6, 22), LocalTime.MIDNIGHT)
        val to = instant(LocalDate.of(2026, 6, 29), LocalTime.MIDNIGHT)

        val result = RecurrenceExpander.expand(ev, from, to, berlin)
        assertEquals(1, result.size)
        assertEquals(start, result.first().startTime)
    }

    @Test
    fun `unbounded WEEKLY caps at 2 years from master`() {
        val masterStart = instant(LocalDate.of(2026, 1, 7), LocalTime.of(10, 0))
        val masterEnd = instant(LocalDate.of(2026, 1, 7), LocalTime.of(11, 0))
        val rule = RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            interval = 1,
            byDays = null,
            until = null
        ).toJsonString()
        val ev = master(masterStart, masterEnd, rule)

        // Riesiges Fenster → 5 Jahre. Wir erwarten max 2 Jahre Occurrences (≤105 Mittwoche).
        val from = instant(LocalDate.of(2025, 1, 1), LocalTime.MIDNIGHT)
        val to = instant(LocalDate.of(2030, 1, 1), LocalTime.MIDNIGHT)

        val result = RecurrenceExpander.expand(ev, from, to, berlin)
        // 2 Jahre Mittwoche ≈ 104. Hard cap des Expanders ist 365; das 2-Jahres-Cap greift früher.
        assertTrue("Expected ≤110 occurrences (2y cap), got ${result.size}", result.size <= 110)
        assertTrue("Expected ≥100 occurrences", result.size >= 100)
    }

    /* ──────────────────────────────────────────────────────────────────
     * nextOccurrenceAfter
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `nextOccurrenceAfter findet naechsten Mittwoch nach now`() {
        val masterStart = instant(LocalDate.of(2026, 6, 24), LocalTime.of(10, 0))
        val masterEnd = instant(LocalDate.of(2026, 6, 24), LocalTime.of(11, 0))
        val rule = RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            interval = 1,
            byDays = null,
            until = LocalDate.of(2026, 12, 31)
        ).toJsonString()
        val ev = master(masterStart, masterEnd, rule)

        // now = Donnerstag, 02.07.2026 → nächste Occurrence = Mi, 08.07.2026 10:00
        val now = instant(LocalDate.of(2026, 7, 2), LocalTime.of(8, 0))
        val next = RecurrenceExpander.nextOccurrenceAfter(ev, now, berlin)
        assertNotNull(next)
        val expected = instant(LocalDate.of(2026, 7, 8), LocalTime.of(10, 0))
        assertEquals(expected, next)
    }

    @Test
    fun `nextOccurrenceAfter Termin heute aber Uhrzeit vorbei liefert naechste Woche`() {
        // Master: Mi 24.06.2026, 10:00. Wöchentlich.
        val masterStart = instant(LocalDate.of(2026, 6, 24), LocalTime.of(10, 0))
        val masterEnd = instant(LocalDate.of(2026, 6, 24), LocalTime.of(11, 0))
        val rule = RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            interval = 1,
            byDays = null,
            until = LocalDate.of(2026, 12, 31)
        ).toJsonString()
        val ev = master(masterStart, masterEnd, rule)

        // now = Mi 08.07.2026 um 10:30 — der heutige Termin (10:00) ist bereits vorbei.
        // inklusive Suche ab now darf NICHT den heutigen 10:00-Slot liefern (der ist <
        // now), sondern den nächsten Mittwoch 15.07.2026 10:00.
        val now = instant(LocalDate.of(2026, 7, 8), LocalTime.of(10, 30))
        val next = RecurrenceExpander.nextOccurrenceAfter(ev, now, berlin)
        assertNotNull(next)
        assertEquals(instant(LocalDate.of(2026, 7, 15), LocalTime.of(10, 0)), next)
    }

    @Test
    fun `firstOccurrenceStartStrictlyAfter ueberspringt exakt gleiche Occurrence`() {
        val masterStart = instant(LocalDate.of(2026, 6, 24), LocalTime.of(10, 0))
        val masterEnd = instant(LocalDate.of(2026, 6, 24), LocalTime.of(11, 0))
        val rule = RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            interval = 1,
            byDays = null,
            until = LocalDate.of(2026, 12, 31)
        ).toJsonString()
        val ev = master(masterStart, masterEnd, rule)

        // `after` == exakter Start einer Occurrence (Mi 08.07 10:00). Strikt-danach muss
        // die FOLGE-Occurrence liefern (15.07), nicht dieselbe.
        val after = instant(LocalDate.of(2026, 7, 8), LocalTime.of(10, 0))
        val next = RecurrenceExpander.firstOccurrenceStartStrictlyAfter(ev, after, berlin)
        assertEquals(instant(LocalDate.of(2026, 7, 15), LocalTime.of(10, 0)), next)
    }

    @Test
    fun `firstOccurrenceStartStrictlyAfter WEEKLY ueber Jahreswechsel`() {
        // Master: Mi 30.12.2026, 09:00, wöchentlich, unbounded (2-Jahres-Cap).
        val masterStart = instant(LocalDate.of(2026, 12, 30), LocalTime.of(9, 0))
        val masterEnd = instant(LocalDate.of(2026, 12, 30), LocalTime.of(10, 0))
        val rule = RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            interval = 1,
            byDays = null,
            until = null
        ).toJsonString()
        val ev = master(masterStart, masterEnd, rule)

        // after == der 30.12.2026-Termin selbst → nächste Occurrence ist Mi 06.01.2027.
        val after = instant(LocalDate.of(2026, 12, 30), LocalTime.of(9, 0))
        val next = RecurrenceExpander.firstOccurrenceStartStrictlyAfter(ev, after, berlin)
        assertNotNull(next)
        assertEquals(instant(LocalDate.of(2027, 1, 6), LocalTime.of(9, 0)), next)
    }

    @Test
    fun `firstOccurrenceStartStrictlyAfter DAILY ueber Jahreswechsel bleibt taeglich`() {
        val masterStart = instant(LocalDate.of(2026, 12, 31), LocalTime.of(8, 0))
        val masterEnd = instant(LocalDate.of(2026, 12, 31), LocalTime.of(8, 30))
        val rule = RecurrenceRule(
            freq = RecurrenceRule.Freq.DAILY,
            interval = 1,
            until = null
        ).toJsonString()
        val ev = master(masterStart, masterEnd, rule)

        // after == 31.12.2026 08:00 → nächste tägliche Occurrence 01.01.2027 08:00.
        val after = instant(LocalDate.of(2026, 12, 31), LocalTime.of(8, 0))
        val next = RecurrenceExpander.firstOccurrenceStartStrictlyAfter(ev, after, berlin)
        assertEquals(instant(LocalDate.of(2027, 1, 1), LocalTime.of(8, 0)), next)
    }

    @Test
    fun `firstOccurrenceStartStrictlyAfter returns null wenn until erreicht`() {
        val masterStart = instant(LocalDate.of(2026, 6, 24), LocalTime.of(10, 0))
        val masterEnd = instant(LocalDate.of(2026, 6, 24), LocalTime.of(11, 0))
        val rule = RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            interval = 1,
            byDays = null,
            until = LocalDate.of(2026, 7, 2) // exklusiv → 01.07 (Mi) ist letzte Occurrence
        ).toJsonString()
        val ev = master(masterStart, masterEnd, rule)

        // after == 01.07.2026 10:00 (letzte Occurrence) → keine Folge mehr.
        val after = instant(LocalDate.of(2026, 7, 1), LocalTime.of(10, 0))
        val next = RecurrenceExpander.firstOccurrenceStartStrictlyAfter(ev, after, berlin)
        assertNull("Nach der letzten Occurrence darf keine Folge kommen", next)
    }

    @Test
    fun `nextOccurrenceAfter returns null wenn until vorbei`() {
        val masterStart = instant(LocalDate.of(2026, 6, 24), LocalTime.of(10, 0))
        val masterEnd = instant(LocalDate.of(2026, 6, 24), LocalTime.of(11, 0))
        val rule = RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            interval = 1,
            byDays = null,
            until = LocalDate.of(2026, 7, 1)
        ).toJsonString()
        val ev = master(masterStart, masterEnd, rule)
        // now = 02.07.2026 — alle Occurrences sind bereits vorbei.
        val now = instant(LocalDate.of(2026, 7, 2), LocalTime.of(8, 0))
        val next = RecurrenceExpander.nextOccurrenceAfter(ev, now, berlin)
        assertNull("Erwartet null — keine Occurrences mehr nach until", next)
    }

    /* ──────────────────────────────────────────────────────────────────
     * JSON round-trip
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `RecurrenceRule JSON roundtrip preserves all fields`() {
        val rule = RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            interval = 2,
            byDays = listOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY),
            until = LocalDate.of(2026, 7, 31)
        )
        val json = rule.toJsonString()
        val parsed = RecurrenceRule.fromJsonString(json)
        assertNotNull(parsed)
        assertEquals(rule.freq, parsed!!.freq)
        assertEquals(rule.interval, parsed.interval)
        assertEquals(rule.byDays, parsed.byDays)
        assertEquals(rule.until, parsed.until)
    }

    @Test
    fun `null oder blank JSON returns null rule`() {
        assertNull(RecurrenceRule.fromJsonString(null))
        assertNull(RecurrenceRule.fromJsonString(""))
        assertNull(RecurrenceRule.fromJsonString("   "))
    }

    @Test
    fun `fromJsonString unknown freq returns null`() {
        assertNull(RecurrenceRule.fromJsonString("""{"freq":"YEARLY","interval":1}"""))
    }

    @Test
    fun `single shot bleibt single shot via expand`() {
        val start = instant(LocalDate.of(2026, 6, 24), LocalTime.of(10, 0))
        val end = instant(LocalDate.of(2026, 6, 24), LocalTime.of(12, 0))
        val ev = master(start, end, rule = null)
        val from = instant(LocalDate.of(2026, 6, 22), LocalTime.MIDNIGHT)
        val to = instant(LocalDate.of(2026, 6, 29), LocalTime.MIDNIGHT)
        val result = RecurrenceExpander.expand(ev, from, to, berlin)
        assertEquals(1, result.size)
        assertFalse("Single-shot darf keine recurrenceRule haben", !result.first().recurrenceRule.isNullOrBlank())
    }
}
