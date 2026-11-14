package de.transio.hiuni.feature.rooms.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Belegung je Datum aus echten LSF-iCal-Exporten (gekürzt, 2026-11-14):
 *  - `sc_a_1_19_kw46.ics`: Termine von SC.A.1.19 aus der Wochenansicht KW 46.
 *  - `hoersaal_sonderfaelle.ics`: Sonderfälle aus Hörsaal 1 und Hörsaal 4
 *    (Einzeltermin per EXDATE gestrichen, 14-täglich, Block Mo bis Fr, täglich).
 */
class RoomIcsParserTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/raumfinder/$name")) { "Fixture $name fehlt" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    private val scA119 by lazy { RoomIcsParser.parseEvents(fixture("sc_a_1_19_kw46.ics")) }
    private val special by lazy { RoomIcsParser.parseEvents(fixture("hoersaal_sonderfaelle.ics")) }

    private fun span(from: String, to: String) = BusySpan(minutes(from), minutes(to))
    private fun minutes(hhmm: String): Int = hhmm.substringBefore(':').toInt() * 60 + hhmm.substringAfter(':').toInt()
    private fun d(iso: String) = LocalDate.parse(iso)

    @Test
    fun `liest alle VEVENTs des Exports`() {
        assertEquals(13, scA119.size)
    }

    @Test
    fun `Donnerstag mit mehreren woechentlichen Terminen, angrenzende zusammengefasst`() {
        assertEquals(
            listOf(span("09:00", "10:00"), span("11:00", "12:00"), span("13:00", "14:00"), span("16:00", "18:00")),
            RoomIcsParser.busySpansOn(scA119, d("2026-11-12"))
        )
    }

    @Test
    fun `Einzeltermin zaehlt nur an seinem Tag`() {
        // 11.11.: Serie 10 bis 12 plus Einzeltermin 12 bis 14.
        assertEquals(listOf(span("10:00", "14:00")), RoomIcsParser.busySpansOn(scA119, d("2026-11-11")))
        // Eine Woche später nur noch die Serie.
        assertEquals(listOf(span("10:00", "12:00")), RoomIcsParser.busySpansOn(scA119, d("2026-11-18")))
    }

    @Test
    fun `EXDATE in der vorlesungsfreien Zeit macht den Tag frei`() {
        assertEquals(emptyList<BusySpan>(), RoomIcsParser.busySpansOn(scA119, d("2026-12-23")))
        assertEquals(emptyList<BusySpan>(), RoomIcsParser.busySpansOn(scA119, d("2026-12-28")))
    }

    @Test
    fun `UNTIL und Serienbeginn werden beachtet`() {
        // 26.10.: nur die beiden Serien, die an dem Tag schon begonnen haben.
        assertEquals(listOf(span("14:00", "18:00")), RoomIcsParser.busySpansOn(scA119, d("2026-10-26")))
        // 21.12.: 325290 (bis 21.12.) läuft noch, danach nicht mehr.
        assertEquals(listOf(span("09:00", "12:00"), span("14:00", "18:00")), RoomIcsParser.busySpansOn(scA119, d("2026-12-21")))
        assertEquals(listOf(span("09:00", "12:00"), span("16:00", "18:00")), RoomIcsParser.busySpansOn(scA119, d("2027-01-04")))
    }

    @Test
    fun `Wochenende ist frei`() {
        assertEquals(emptyList<BusySpan>(), RoomIcsParser.busySpansOn(scA119, d("2026-11-14")))
    }

    @Test
    fun `Einzeltermin mit EXDATE auf sich selbst faellt aus, auch mit 24 Uhr als Ende`() {
        assertEquals(emptyList<BusySpan>(), RoomIcsParser.busySpansOn(special, d("2026-10-19")))
    }

    @Test
    fun `14-taeglich gilt nur in jeder zweiten Woche`() {
        assertEquals(listOf(span("12:00", "14:00")), RoomIcsParser.busySpansOn(special, d("2026-10-27")))
        assertEquals(emptyList<BusySpan>(), RoomIcsParser.busySpansOn(special, d("2026-11-03")))
        assertEquals(listOf(span("12:00", "14:00")), RoomIcsParser.busySpansOn(special, d("2026-11-10")))
    }

    @Test
    fun `Block Montag bis Freitag mit UNTIL in UTC`() {
        assertEquals(listOf(span("09:00", "16:00")), RoomIcsParser.busySpansOn(special, d("2026-10-05")))
        assertEquals(listOf(span("09:00", "16:00")), RoomIcsParser.busySpansOn(special, d("2026-10-06")))
        // UNTIL=20261006T235900Z ist 07.10. 01:59 in Berlin, der Termin um 9 Uhr liegt danach.
        assertEquals(emptyList<BusySpan>(), RoomIcsParser.busySpansOn(special, d("2026-10-07")))
        // Zweiter Block ab Do 08.10. bis Fr 16.10., Wochenende ausgenommen.
        assertEquals(listOf(span("09:00", "16:00")), RoomIcsParser.busySpansOn(special, d("2026-10-12")))
        assertEquals(listOf(span("09:00", "16:00")), RoomIcsParser.busySpansOn(special, d("2026-10-16")))
        assertEquals(emptyList<BusySpan>(), RoomIcsParser.busySpansOn(special, d("2026-10-10")))
    }

    @Test
    fun `taegliche Serie`() {
        assertEquals(listOf(span("09:00", "20:00")), RoomIcsParser.busySpansOn(special, d("2027-02-13")))
        assertEquals(listOf(span("09:00", "20:00")), RoomIcsParser.busySpansOn(special, d("2027-02-14")))
        assertEquals(emptyList<BusySpan>(), RoomIcsParser.busySpansOn(special, d("2027-02-15")))
    }

    private fun ics(vararg events: String) = buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n")
        events.forEach { append("BEGIN:VEVENT\r\n").append(it.trimIndent().replace("\n", "\r\n")).append("\r\nEND:VEVENT\r\n") }
        append("END:VCALENDAR\r\n")
    }

    @Test
    fun `24 Uhr als Ende reicht bis Tagesende`() {
        val events = RoomIcsParser.parseEvents(
            ics(
                """
                DTSTART;TZID=Europe/Berlin:20261116T080000
                DTEND;TZID=Europe/Berlin:20261116T240000
                EXDATE;TZID=Europe/Berlin:
                """
            )
        )
        assertEquals(listOf(BusySpan(480, 1440)), RoomIcsParser.busySpansOn(events, d("2026-11-16")))
        assertEquals(emptyList<BusySpan>(), RoomIcsParser.busySpansOn(events, d("2026-11-17")))
    }

    @Test
    fun `Termin ueber Mitternacht belegt den Folgetag bis zum Ende`() {
        val events = RoomIcsParser.parseEvents(
            ics(
                """
                DTSTART;TZID=Europe/Berlin:20261110T200000
                DTEND;TZID=Europe/Berlin:20261111T020000
                """
            )
        )
        assertEquals(listOf(BusySpan(1200, 1440)), RoomIcsParser.busySpansOn(events, d("2026-11-10")))
        assertEquals(listOf(BusySpan(0, 120)), RoomIcsParser.busySpansOn(events, d("2026-11-11")))
    }

    @Test
    fun `COUNT begrenzt die Serie, gefaltete Zeilen und STATUS CANCELLED`() {
        val events = RoomIcsParser.parseEvents(
            ics(
                """
                DTSTART;TZID=Europe/Berlin:20261102T080000
                DTEND;TZID=Europe/Berlin:20261102T100000
                RRULE:FREQ=WEEKLY;COUNT=2;BY
                 DAY=MO
                """,
                """
                DTSTART;TZID=Europe/Berlin:20261109T120000
                DTEND;TZID=Europe/Berlin:20261109T130000
                STATUS:CANCELLED
                """
            )
        )
        assertEquals(1, events.size)
        assertEquals(listOf(BusySpan(480, 600)), RoomIcsParser.busySpansOn(events, d("2026-11-09")))
        assertEquals(emptyList<BusySpan>(), RoomIcsParser.busySpansOn(events, d("2026-11-16")))
    }

    @Test
    fun `unbekannte Frequenz gilt vorsichtig als belegt`() {
        val events = RoomIcsParser.parseEvents(
            ics(
                """
                DTSTART;TZID=Europe/Berlin:20261102T080000
                DTEND;TZID=Europe/Berlin:20261102T100000
                RRULE:FREQ=HOURLY;INTERVAL=1
                """
            )
        )
        assertEquals(listOf(BusySpan(480, 600)), RoomIcsParser.busySpansOn(events, d("2026-11-20")))
    }
}
