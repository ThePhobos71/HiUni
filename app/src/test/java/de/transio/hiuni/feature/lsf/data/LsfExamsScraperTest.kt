package de.transio.hiuni.feature.lsf.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class LsfExamsScraperTest {

    private val scraper = LsfExamsScraper()

    @Test
    fun `parse extrahiert Veranstaltungs-Nr und Modulnamen aus dem Prüfungstext`() {
        val rows = scraper.parse(buildHtml(listOf(STATISTIK_ROW)), SEMESTER_2026_1)
        assertEquals(1, rows.size)
        val exam = rows.single()
        assertEquals("5395", exam.veranstaltungsNumber)
        assertEquals("Mathematische Methoden IV", exam.moduleName)
        assertEquals("Pflichtmodule Methoden", exam.parentModule)
    }

    @Test
    fun `parse liest examDate Uhrzeit und alle Räume aus dem Klausurplan`() {
        val exam = scraper.parse(buildHtml(listOf(STATISTIK_ROW)), SEMESTER_2026_1).single()
        assertEquals(LocalDate.of(2026, 7, 21), exam.examDate)
        assertEquals(LocalTime.of(10, 0), exam.examTime)
        assertEquals(listOf("SC.A.0.09", "SC.B.0.37"), exam.rooms)
    }

    @Test
    fun `parse extrahiert Anmelde- und Abmeldedatum aus Cell 3`() {
        val exam = scraper.parse(buildHtml(listOf(STATISTIK_ROW)), SEMESTER_2026_1).single()
        assertEquals(LocalDate.of(2026, 6, 15), exam.registrationDate)
        assertEquals(LocalDate.of(2026, 7, 20), exam.cancellationDeadline)
    }

    @Test
    fun `parse extrahiert das Anzeige-Semester aus Cell 2`() {
        val exam = scraper.parse(buildHtml(listOf(STATISTIK_ROW)), SEMESTER_2026_1).single()
        assertEquals("SoSe 26", exam.semester)
        assertEquals(SEMESTER_2026_1, exam.semesterCode)
    }

    @Test
    fun `parse setzt examDate examTime und rooms auf leer wenn Klausur noch nicht terminiert`() {
        val exam = scraper.parse(buildHtml(listOf(UNDATED_ROW)), SEMESTER_2026_1).single()
        assertNull(exam.examDate)
        assertNull(exam.examTime)
        assertTrue(exam.rooms.isEmpty())
        // Anmeldung ist trotzdem da
        assertEquals(LocalDate.of(2026, 6, 15), exam.registrationDate)
    }

    @Test
    fun `parse überspringt die Studiengang-colspan-Header-Zeile`() {
        // Wenn die Studiengang-Zeile als Datenzeile interpretiert würde, wäre Cell 0
        // "Studiengang: B.Sc. Wirtschaftsinformatik" und die Nummern-Extraktion würde
        // schlagen — wir erwarten, dass der Scraper sie überspringt.
        val rows = scraper.parse(buildHtml(listOf(STATISTIK_ROW, INFORMATIK_ROW)), SEMESTER_2026_1)
        assertEquals(2, rows.size)
    }

    @Test
    fun `parse wirft ScrapeException wenn keine Tabelle vorhanden`() {
        val html = """<html><body><div class="content">Keine Anmeldungen.</div></body></html>"""
        val ex = assertThrows(ScrapeException::class.java) {
            scraper.parse(html, SEMESTER_2026_1)
        }
        assertNotNull(ex.message)
    }

    @Test
    fun `parse überlebt Zeilen mit kaputtem Prüfungstext und logs nur`() {
        // Eine Datenzeile ohne Veranstaltungs-Nr wirft im parseRow eine ScrapeException,
        // die im parse() per runCatching geschluckt wird — die übrigen Zeilen müssen
        // trotzdem rauskommen.
        val rows = scraper.parse(buildHtml(listOf(STATISTIK_ROW, BROKEN_ROW)), SEMESTER_2026_1)
        assertEquals(1, rows.size)
        assertEquals("5395", rows.single().veranstaltungsNumber)
    }

    private fun buildHtml(rows: List<String>): String = """
        <html><body>
        <div class="content">
          <table border="0" width="100%">
            <tr><td colspan="7">Studiengang: B.Sc. Wirtschaftsinformatik</td></tr>
            <tr>
              <th class="tabelleheader">Prüfungstext</th>
              <th class="tabelleheader">Prüfer/-in</th>
              <th class="tabelleheader">Semester</th>
              <th class="tabelleheader">Anmeldedatum</th>
              <th class="tabelleheader">Prüfungsdatum</th>
              <th class="tabelleheader">Klausurplan</th>
            </tr>
            ${rows.joinToString("\n")}
          </table>
        </div>
        </body></html>
    """.trimIndent()

    companion object {
        private const val SEMESTER_2026_1 = "20261"

        private val STATISTIK_ROW = """
            <tr>
              <td class="mod_n">Pflichtmodule Methoden -- Mathematische Methoden IV /&nbsp;&nbsp;5395&nbsp;&nbsp;Statistik</td>
              <td class="mod_n">&nbsp;</td>
              <td class="mod_n"><span>SoSe 26</span></td>
              <td class="mod_n">15.06.2026 <span>(verbindliche Anmeldung)</span> -- Abmeldung bis zum 20.07.2026</td>
              <td class="mod_n">21.07.2026</td>
              <td class="mod_n">10:00 Uhr; Raum: SC.A.0.09;<br/>10:00 Uhr; Raum: SC.B.0.37;<br/>Zum <a href="#">Klausurplan</a></td>
            </tr>
        """.trimIndent()

        private val INFORMATIK_ROW = """
            <tr>
              <td class="mod_n">Pflichtbereich Informatik -- Einführung in die Informatik /&nbsp;&nbsp;3204&nbsp;&nbsp;Einführung in die Informatik</td>
              <td class="mod_n">&nbsp;</td>
              <td class="mod_n"><span>SoSe 26</span></td>
              <td class="mod_n">15.06.2026 (verbindliche Anmeldung) -- Abmeldung bis zum 20.07.2026</td>
              <td class="mod_n">23.07.2026</td>
              <td class="mod_n">08:00 Uhr; Raum: SC.B.0.37;</td>
            </tr>
        """.trimIndent()

        private val UNDATED_ROW = """
            <tr>
              <td class="mod_n">Wahlpflicht -- Projekt Recht /&nbsp;&nbsp;3207&nbsp;&nbsp;Projekt</td>
              <td class="mod_n">&nbsp;</td>
              <td class="mod_n"><span>SoSe 26</span></td>
              <td class="mod_n">15.06.2026 (verbindliche Anmeldung) -- Abmeldung bis zum 20.07.2026</td>
              <td class="mod_n">&nbsp;</td>
              <td class="mod_n">&nbsp;</td>
            </tr>
        """.trimIndent()

        private val BROKEN_ROW = """
            <tr>
              <td class="mod_n">Nur Text ohne Nummer</td>
              <td class="mod_n">&nbsp;</td>
              <td class="mod_n"><span>SoSe 26</span></td>
              <td class="mod_n">&nbsp;</td>
              <td class="mod_n">&nbsp;</td>
              <td class="mod_n">&nbsp;</td>
            </tr>
        """.trimIndent()
    }
}
