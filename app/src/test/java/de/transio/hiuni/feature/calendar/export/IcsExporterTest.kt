package de.transio.hiuni.feature.calendar.export

import de.transio.hiuni.feature.calendar.data.RecurrenceRule
import de.transio.hiuni.feature.email.data.IcsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Tests für [IcsExporter]: RFC-5545-Gerüst, Zeitzonen-Handling, RRULE, Escaping,
 * Line-Folding und VALARM. Zone fest auf Europe/Berlin, damit die Tests
 * unabhängig von der Build-Umgebung (CI in UTC) laufen.
 */
class IcsExporterTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val stamp: Instant = Instant.parse("2026-10-26T09:30:00Z")

    private fun berlinInstant(y: Int, m: Int, d: Int, h: Int, min: Int): Instant =
        LocalDateTime.of(y, m, d, h, min).atZone(berlin).toInstant()

    private fun lines(ics: String): List<String> = ics.removeSuffix("\r\n").split("\r\n")

    /** Hebt das Folding wieder auf (CRLF + ein Leerzeichen am Zeilenanfang). */
    private fun unfoldedLines(ics: String): List<String> =
        ics.replace("\r\n ", "").removeSuffix("\r\n").split("\r\n")

    private fun veventBlock(ics: String): List<String> {
        val all = unfoldedLines(ics)
        val from = all.indexOf("BEGIN:VEVENT")
        val to = all.indexOf("END:VEVENT")
        return all.subList(from, to + 1)
    }

    private val lecture = IcsEvent(
        uid = "user-1@hiuni",
        summary = "Mathe 1",
        timing = IcsTiming.Timed(
            start = berlinInstant(2026, 10, 27, 10, 15),
            end = berlinInstant(2026, 10, 27, 11, 45)
        ),
        location = "F101",
        description = "Vorlesung"
    )

    /* ──────────────────────────────────────────────────────────────────
     * Gerüst
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `Kalender-Kopf enthält alle Pflicht- und Komfort-Properties`() {
        val ics = IcsExporter.export(listOf(lecture), stamp)
        val l = lines(ics)
        assertEquals("BEGIN:VCALENDAR", l.first())
        assertEquals("END:VCALENDAR", l.last())
        assertEquals(
            listOf(
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//HiUni//DE",
                "CALSCALE:GREGORIAN",
                "METHOD:PUBLISH",
                "X-WR-CALNAME:HiUni",
                "X-WR-TIMEZONE:Europe/Berlin"
            ),
            l.take(7)
        )
    }

    @Test
    fun `jede Zeile endet mit CRLF, keine nackten LF`() {
        val ics = IcsExporter.export(listOf(lecture), stamp)
        assertTrue(ics.endsWith("\r\n"))
        assertFalse(ics.replace("\r\n", "").contains('\n'))
        assertFalse(ics.replace("\r\n", "").contains('\r'))
    }

    @Test
    fun `VTIMEZONE für Europe Berlin mit Sommer- und Winterzeit-Regel`() {
        val ics = IcsExporter.export(listOf(lecture), stamp)
        val l = lines(ics)
        val from = l.indexOf("BEGIN:VTIMEZONE")
        val to = l.indexOf("END:VTIMEZONE")
        assertTrue("VTIMEZONE fehlt", from in 0 until to)
        val tz = l.subList(from, to + 1)
        assertTrue(tz.contains("TZID:Europe/Berlin"))
        // Sommerzeit: letzter Sonntag im März, +01:00 → +02:00
        val daylight = tz.subList(tz.indexOf("BEGIN:DAYLIGHT"), tz.indexOf("END:DAYLIGHT"))
        assertTrue(daylight.contains("TZOFFSETFROM:+0100"))
        assertTrue(daylight.contains("TZOFFSETTO:+0200"))
        assertTrue(daylight.contains("RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU"))
        // Winterzeit: letzter Sonntag im Oktober, +02:00 → +01:00
        val standard = tz.subList(tz.indexOf("BEGIN:STANDARD"), tz.indexOf("END:STANDARD"))
        assertTrue(standard.contains("TZOFFSETFROM:+0200"))
        assertTrue(standard.contains("TZOFFSETTO:+0100"))
        assertTrue(standard.contains("RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU"))
        // VTIMEZONE muss vor dem ersten VEVENT stehen, das die TZID referenziert.
        assertTrue(to < l.indexOf("BEGIN:VEVENT"))
    }

    /* ──────────────────────────────────────────────────────────────────
     * Einzeltermin
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `Einzeltermin mit lokaler Zeit und TZID`() {
        val ics = IcsExporter.export(listOf(lecture), stamp)
        assertEquals(
            listOf(
                "BEGIN:VEVENT",
                "UID:user-1@hiuni",
                "DTSTAMP:20261026T093000Z",
                "DTSTART;TZID=Europe/Berlin:20261027T101500",
                "DTEND;TZID=Europe/Berlin:20261027T114500",
                "SUMMARY:Mathe 1",
                "LOCATION:F101",
                "DESCRIPTION:Vorlesung",
                "END:VEVENT"
            ),
            veventBlock(ics)
        )
    }

    @Test
    fun `Termin ohne Ort und Beschreibung lässt die Properties weg`() {
        val ics = IcsExporter.export(
            listOf(lecture.copy(location = null, description = "  ")),
            stamp
        )
        val block = veventBlock(ics)
        assertFalse(block.any { it.startsWith("LOCATION") })
        assertFalse(block.any { it.startsWith("DESCRIPTION") })
    }

    @Test
    fun `Termin ohne Dauer bekommt kein DTEND`() {
        val start = berlinInstant(2026, 11, 3, 23, 59)
        val ics = IcsExporter.export(
            listOf(lecture.copy(timing = IcsTiming.Timed(start, start))),
            stamp
        )
        val block = veventBlock(ics)
        assertTrue(block.contains("DTSTART;TZID=Europe/Berlin:20261103T235900"))
        assertFalse(block.any { it.startsWith("DTEND") })
    }

    @Test
    fun `ganztägiger Termin nutzt VALUE=DATE mit exklusivem Ende`() {
        val ics = IcsExporter.export(
            listOf(
                IcsEvent(
                    uid = "exam-1@hiuni",
                    summary = "Klausur: Mathe 1",
                    timing = IcsTiming.AllDay(LocalDate.of(2027, 2, 15))
                )
            ),
            stamp
        )
        val block = veventBlock(ics)
        assertTrue(block.contains("DTSTART;VALUE=DATE:20270215"))
        assertTrue(block.contains("DTEND;VALUE=DATE:20270216"))
    }

    @Test
    fun `nur ganztägige Termine brauchen keine VTIMEZONE`() {
        val ics = IcsExporter.export(
            listOf(
                IcsEvent(
                    uid = "exam-1@hiuni",
                    summary = "Klausur",
                    timing = IcsTiming.AllDay(LocalDate.of(2027, 2, 15))
                )
            ),
            stamp
        )
        assertFalse(ics.contains("BEGIN:VTIMEZONE"))
    }

    /* ──────────────────────────────────────────────────────────────────
     * Wiederholung
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `wöchentliche Serie über die Zeitumstellung bleibt ein VEVENT mit RRULE`() {
        // Mo 19.10.2026 ist noch Sommerzeit (CEST), ab So 25.10. gilt CET.
        // Mit TZID bleibt die Vorlesung in beiden Phasen um 10:15 Ortszeit.
        val series = IcsEvent(
            uid = "user-7@hiuni",
            summary = "Algorithmen",
            timing = IcsTiming.Timed(
                start = berlinInstant(2026, 10, 19, 10, 15),
                end = berlinInstant(2026, 10, 19, 11, 45)
            ),
            recurrence = IcsRecurrence(
                freq = RecurrenceRule.Freq.WEEKLY,
                byDays = listOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY),
                untilExclusive = LocalDate.of(2026, 11, 30)
            )
        )
        val ics = IcsExporter.export(listOf(series), stamp)
        assertEquals(1, Regex("BEGIN:VEVENT").findAll(ics).count())
        val block = veventBlock(ics)
        assertTrue(block.contains("DTSTART;TZID=Europe/Berlin:20261019T101500"))
        assertTrue(block.contains("DTEND;TZID=Europe/Berlin:20261019T114500"))
        // UNTIL ist laut RFC inklusiv und bei TZID-DTSTART in UTC anzugeben.
        // Exklusives App-until 30.11. → letzter erlaubter Start 29.11. 23:59:59 CET
        // = 29.11. 22:59:59 UTC.
        assertTrue(
            block.joinToString("\n"),
            block.contains("RRULE:FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20261129T225959Z")
        )
    }

    @Test
    fun `UNTIL im Sommer rechnet mit Sommerzeit-Offset`() {
        val series = lecture.copy(
            recurrence = IcsRecurrence(
                freq = RecurrenceRule.Freq.DAILY,
                interval = 2,
                untilExclusive = LocalDate.of(2026, 7, 31)
            )
        )
        val block = veventBlock(IcsExporter.export(listOf(series), stamp))
        // 31.07. 00:00 CEST = 30.07. 22:00 UTC, minus eine Sekunde.
        assertTrue(block.contains("RRULE:FREQ=DAILY;INTERVAL=2;UNTIL=20260730T215959Z"))
    }

    @Test
    fun `Serie ohne Enddatum hat RRULE ohne UNTIL`() {
        val series = lecture.copy(
            recurrence = IcsRecurrence(freq = RecurrenceRule.Freq.MONTHLY)
        )
        val block = veventBlock(IcsExporter.export(listOf(series), stamp))
        assertTrue(block.contains("RRULE:FREQ=MONTHLY"))
    }

    /* ──────────────────────────────────────────────────────────────────
     * Escaping und Folding
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `Textwerte escapen Backslash, Semikolon, Komma und Zeilenumbrüche`() {
        assertEquals(
            "Mathe\\; Analysis\\, Teil 1\\\\2\\nNeu\\nZeile3",
            IcsExporter.escapeText("Mathe; Analysis, Teil 1\\2\r\nNeu\nZeile3")
        )
    }

    @Test
    fun `escapte Werte landen in SUMMARY, LOCATION und DESCRIPTION`() {
        val ics = IcsExporter.export(
            listOf(
                lecture.copy(
                    summary = "Logik; Teil 1, Übung",
                    location = "Raum A, OG 1",
                    description = "Zeile 1\nZeile 2"
                )
            ),
            stamp
        )
        val block = veventBlock(ics)
        assertTrue(block.contains("SUMMARY:Logik\\; Teil 1\\, Übung"))
        assertTrue(block.contains("LOCATION:Raum A\\, OG 1"))
        assertTrue(block.contains("DESCRIPTION:Zeile 1\\nZeile 2"))
    }

    @Test
    fun `kurze Zeilen werden nicht gefaltet`() {
        val line = "SUMMARY:" + "a".repeat(67)
        assertEquals(75, line.toByteArray(Charsets.UTF_8).size)
        assertEquals(line, IcsExporter.foldLine(line))
    }

    @Test
    fun `Folding trennt nach 75 Oktetten und nie mitten in einem Umlaut`() {
        // "SUMMARY:" (8) + 66×a = 74 Oktette; das folgende ü (2 Oktette) passt
        // nicht mehr in die erste Zeile und muss komplett in die zweite.
        val line = "SUMMARY:" + "a".repeat(66) + "ü" + "ö".repeat(60)
        val folded = IcsExporter.foldLine(line)
        val physical = folded.split("\r\n")
        assertEquals("SUMMARY:" + "a".repeat(66), physical[0])
        assertTrue(physical[1].startsWith(" ü"))
        physical.forEach { assertTrue(it, it.toByteArray(Charsets.UTF_8).size <= 75) }
        physical.drop(1).forEach { assertTrue(it.startsWith(" ")) }
        assertEquals(line, folded.replace("\r\n ", ""))
    }

    @Test
    fun `Folding hält Mehr-Byte-Zeichen und Surrogat-Paare zusammen`() {
        val line = "DESCRIPTION:" + "€".repeat(40) + "\uD834\uDD1E".repeat(30)
        val folded = IcsExporter.foldLine(line)
        val physical = folded.split("\r\n")
        assertTrue(physical.size > 2)
        physical.forEach { part ->
            assertTrue(part.toByteArray(Charsets.UTF_8).size <= 75)
            // Kein abgeschnittenes Surrogat am Zeilenende/-anfang.
            assertFalse(part.last().isHighSurrogate())
            assertFalse(part.trimStart().first().isLowSurrogate())
        }
        assertEquals(line, folded.replace("\r\n ", ""))
    }

    @Test
    fun `im Export ist keine physische Zeile länger als 75 Oktette`() {
        val ics = IcsExporter.export(
            listOf(
                lecture.copy(
                    summary = "Grundlagen der Wirtschaftsinformatik für Fortgeschrittene, Übungsgruppe Ä",
                    description = "Bitte Übungsblätter mitbringen; Prüfungsvorbereitung. ".repeat(5)
                )
            ),
            stamp
        )
        lines(ics).forEach { assertTrue(it, it.toByteArray(Charsets.UTF_8).size <= 75) }
    }

    /* ──────────────────────────────────────────────────────────────────
     * VALARM
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `Erinnerung wird zu VALARM mit negativem TRIGGER`() {
        val block = veventBlock(
            IcsExporter.export(listOf(lecture.copy(reminderMinutesBefore = 15)), stamp)
        )
        val from = block.indexOf("BEGIN:VALARM")
        val to = block.indexOf("END:VALARM")
        assertTrue(from in 0 until to)
        assertEquals(
            listOf(
                "BEGIN:VALARM",
                "ACTION:DISPLAY",
                "DESCRIPTION:Mathe 1",
                "TRIGGER:-PT15M",
                "END:VALARM"
            ),
            block.subList(from, to + 1)
        )
    }

    @Test
    fun `ohne Erinnerung kein VALARM, Null-Minuten-Erinnerung zum Start`() {
        val none = IcsExporter.export(listOf(lecture), stamp)
        assertFalse(none.contains("BEGIN:VALARM"))
        val zero = veventBlock(IcsExporter.export(listOf(lecture.copy(reminderMinutesBefore = 0)), stamp))
        assertTrue(zero.contains("TRIGGER:PT0M"))
    }

    /* ──────────────────────────────────────────────────────────────────
     * Round-Trip gegen den App-eigenen IcsParser
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `Round-Trip über IcsParser liefert die Ursprungswerte`() {
        val tricky = lecture.copy(
            uid = "user-2@hiuni",
            summary = "Datenbanken; Übung, Gruppe A " + "x".repeat(80),
            location = "Samelsonplatz, Raum C 012",
            description = "Pfad C:\\temp\\neu\nZweite Zeile",
            reminderMinutesBefore = 30
        )
        val ics = IcsExporter.export(listOf(lecture, tricky), stamp)
        val parsed = IcsParser.parseAll(ics)
        assertEquals(2, parsed.size)
        val p = parsed[1]
        assertEquals("user-2@hiuni", p.uid)
        assertEquals(tricky.summary, p.summary)
        assertEquals(tricky.location, p.location)
        // VALARM hat ein eigenes DESCRIPTION, das die Event-Beschreibung nicht überschreiben darf.
        assertEquals(tricky.description, p.description)
        assertEquals((tricky.timing as IcsTiming.Timed).start, p.start)
        assertEquals(tricky.timing.end, p.end)
        assertEquals(lecture.summary, parsed[0].summary)
    }
}
