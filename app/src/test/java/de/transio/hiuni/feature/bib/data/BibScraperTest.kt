package de.transio.hiuni.feature.bib.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class BibScraperTest {

    private val scraper = BibScraper()

    @Test
    fun `parseAvailability liest FREE Zelle mit getBookingForm-Handler als bookable`() {
        val html = wrap(
            """<td id="cell-20260527-1600-101" onclick="getBookingForm('20260527', 1600, 101)"
                   style="background-color: #92CD00"
                   title="Raum FG, Platz F101, 16:00-16:30 Uhr">&nbsp;</td>"""
        )

        val day = scraper.parseAvailability(html).getValue(LocalDate.of(2026, 5, 27) to 101)
        assertEquals(1, day.slots.size)
        val slot = day.slots.single()
        assertEquals(SlotStatus.FREE, slot.status)
        assertTrue(slot.bookable)
        assertEquals(LocalTime.of(16, 0), slot.startTime)
        assertEquals(LocalTime.of(16, 30), slot.endTime)
    }

    @Test
    fun `parseAvailability erkennt fremde Buchung an Rotton DF2E3B`() {
        val html = wrap(
            """<td id="cell-20260527-1600-101"
                   style="background-color: #DF2E3B"
                   title="Raum FG, Platz F101, 16:00-16:30 Uhr">&nbsp;</td>"""
        )

        val day = scraper.parseAvailability(html).getValue(LocalDate.of(2026, 5, 27) to 101)
        val slot = day.slots.single()
        assertEquals(SlotStatus.BOOKED, slot.status)
        assertFalse("BOOKED-Slot darf nicht bookable sein", slot.bookable)
    }

    @Test
    fun `parseAvailability erkennt eigene Buchung an Grauton 999999`() {
        // ubwww rendert eigene Buchungen mit #999999 + getConfirmationForm
        // (statt getBookingForm) — beide Marker sind im echten HTML vorhanden.
        val html = wrap(
            """<td id="cell-20260527-1600-103"
                   onclick="getConfirmationForm('20260527', 1600, 103, 2, '')"
                   style="background-color: #999999"
                   rowspan="4"
                   title="Raum FG, Platz F103, 16:00-18:00 Uhr">&nbsp;</td>"""
        )

        val day = scraper.parseAvailability(html).getValue(LocalDate.of(2026, 5, 27) to 103)
        assertEquals("rowspan=4 erzeugt 4 Slots", 4, day.slots.size)
        day.slots.forEach {
            assertEquals(SlotStatus.OWN_BOOKING, it.status)
            assertFalse("OWN_BOOKING darf nicht als bookable markiert sein", it.bookable)
        }
        // Slots laufen lückenlos in 30-Min-Schritten.
        assertEquals(LocalTime.of(16, 0), day.slots.first().startTime)
        assertEquals(LocalTime.of(18, 0), day.slots.last().endTime)
    }

    @Test
    fun `parseAvailability expandiert rowspan in halbstuendliche Slots`() {
        val html = wrap(
            """<td id="cell-20260527-1000-105"
                   onclick="getBookingForm('20260527', 1000, 105)"
                   style="background-color: #92CD00"
                   rowspan="3"
                   title="Raum FG, Platz F105, 10:00-11:30 Uhr">&nbsp;</td>"""
        )

        val day = scraper.parseAvailability(html).getValue(LocalDate.of(2026, 5, 27) to 105)
        val starts = day.slots.map { it.startTime }
        assertEquals(
            listOf(LocalTime.of(10, 0), LocalTime.of(10, 30), LocalTime.of(11, 0)),
            starts
        )
    }

    @Test
    fun `parseAvailability ignoriert closed Zellen ohne cell- ID`() {
        val html = wrap(
            """<td style="background-color: #e8e3e3" title="geschlossen">&nbsp;</td>"""
        )

        assertTrue("closed-Zellen tauchen ohne cell-ID nicht im Output auf",
            scraper.parseAvailability(html).isEmpty())
    }

    @Test
    fun `parseAvailability militaryToTime mappt Suffix 50 auf Halbstunde`() {
        // ubwww nutzt "850" für 08:30 (Suffix 50 statt 30).
        val html = wrap(
            """<td id="cell-20260527-850-101"
                   onclick="getBookingForm('20260527', 850, 101)"
                   style="background-color: #92CD00">&nbsp;</td>"""
        )

        val slot = scraper.parseAvailability(html)
            .getValue(LocalDate.of(2026, 5, 27) to 101)
            .slots
            .single()
        assertEquals(LocalTime.of(8, 30), slot.startTime)
        assertEquals(LocalTime.of(9, 0), slot.endTime)
    }

    @Test
    fun `parseAvailability sortiert Slots pro Raum-Tag aufsteigend`() {
        val html = wrap(
            """<td id="cell-20260527-1500-101" onclick="getBookingForm('20260527', 1500, 101)" style="background-color: #92CD00">&nbsp;</td>
               <td id="cell-20260527-800-101"  onclick="getBookingForm('20260527', 800, 101)"  style="background-color: #92CD00">&nbsp;</td>
               <td id="cell-20260527-1200-101" onclick="getBookingForm('20260527', 1200, 101)" style="background-color: #92CD00">&nbsp;</td>"""
        )

        val starts = scraper.parseAvailability(html)
            .getValue(LocalDate.of(2026, 5, 27) to 101)
            .slots
            .map { it.startTime }
        assertEquals(starts, starts.sorted())
    }

    @Test
    fun `parseEndTimes parst HHMM-Optionen und deduped`() {
        // Format aus dem ubwww-Endpoint:
        //   `<option value="900">09:00</option><option value="950" selected>09:30</option>…`
        val html = """
            <option value="900">09:00</option>
            <option value="950" selected>09:30</option>
            <option value="1000">10:00</option>
            <option value="900">09:00</option>
        """.trimIndent()

        val ends = scraper.parseEndTimes(html)
        assertEquals(
            listOf(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0)),
            ends
        )
    }

    @Test
    fun `parseEndTimes liefert leere Liste fuer leeren Body`() {
        assertTrue(scraper.parseEndTimes("").isEmpty())
        assertTrue(scraper.parseEndTimes("   ").isEmpty())
    }

    @Test
    fun `parseMyBookings extrahiert Koordinaten aus deleteBookingFromList-Anchor`() {
        val html = """
            <ul>
              <li>
                <a href="javascript: deleteBookingFromList(20260527, 1600, 103, 1800)">stornieren</a>
                Raum F103 · 16:00 – 18:00
              </li>
            </ul>
        """.trimIndent()

        val bookings = scraper.parseMyBookings(html)
        assertEquals(1, bookings.size)
        val b = bookings.single()
        assertEquals(LocalDate.of(2026, 5, 27), b.date)
        assertEquals(LocalTime.of(16, 0), b.startTime)
        assertEquals(LocalTime.of(18, 0), b.endTime)
        assertEquals(103, b.roomId)
        assertEquals("F103", b.roomLabel)
    }

    @Test
    fun `parseMyBookings dedupliziert identische Eintraege`() {
        val html = """
            <ul>
              <li><a href="javascript: deleteBookingFromList(20260527, 1600, 103, 1800)">x</a></li>
              <li><a onclick="deleteBookingFromList(20260527, 1600, 103, 1800)">y</a></li>
            </ul>
        """.trimIndent()

        assertEquals(1, scraper.parseMyBookings(html).size)
    }

    @Test
    fun `toMilitary konvertiert LocalTime zurueck in HHMM-Format`() {
        assertEquals(800, BibScraper.toMilitary(LocalTime.of(8, 0)))
        assertEquals(850, BibScraper.toMilitary(LocalTime.of(8, 30)))
        assertEquals(1650, BibScraper.toMilitary(LocalTime.of(16, 30)))
        assertEquals(2000, BibScraper.toMilitary(LocalTime.of(20, 0)))
    }

    @Test
    fun `formatDate liefert YYYYMMDD ohne Trenner`() {
        assertEquals("20260527", BibScraper.formatDate(LocalDate.of(2026, 5, 27)))
        assertEquals("20260101", BibScraper.formatDate(LocalDate.of(2026, 1, 1)))
    }

    /** Wraps cell HTML in a minimal `<table>` so Jsoup finds the `<td>`s. */
    private fun wrap(cells: String): String =
        "<html><body><table><tbody><tr>$cells</tr></tbody></table></body></html>"
}
