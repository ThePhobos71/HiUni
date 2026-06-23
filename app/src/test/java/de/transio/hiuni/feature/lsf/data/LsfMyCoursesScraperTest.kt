package de.transio.hiuni.feature.lsf.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LsfMyCoursesScraperTest {

    private val scraper = LsfMyCoursesScraper()

    @Test
    fun `parse extrahiert publishid Titel und Code aus dem Anker`() {
        val page = scraper.parse(buildHtml(currentEntries = listOf(LOGISTIK_FIXTURE)))
        assertEquals(1, page.entries.size)
        val entry = page.entries.single()
        assertEquals("120558", entry.lsfId)
        assertEquals("3204", entry.code)
        assertEquals("Logistik und Produktion 1", entry.title)
    }

    @Test
    fun `parse liest Tag Zeit Rhythmus und Raum aus dem Termin-Table`() {
        val entry = scraper.parse(buildHtml(currentEntries = listOf(LOGISTIK_FIXTURE)))
            .entries.single()
        assertEquals("Do.", entry.day)
        assertEquals("08:00", entry.timeStart)
        assertEquals("10:00", entry.timeEnd)
        assertEquals("wöchentlich", entry.rhythm)
        assertEquals("SC.B.0.37", entry.room)
    }

    @Test
    fun `parse erkennt grueneWarnung-Status angemeldet`() {
        val entry = scraper.parse(buildHtml(currentEntries = listOf(LOGISTIK_FIXTURE)))
            .entries.single()
        assertEquals("angemeldet", entry.status)
    }

    @Test
    fun `parse erkennt grueneWarnung-Status zugelassen`() {
        val entry = scraper.parse(buildHtml(currentEntries = listOf(IT_PROJEKT_ZUGELASSEN_FIXTURE)))
            .entries.single()
        assertEquals("zugelassen", entry.status)
    }

    @Test
    fun `parse importiert auch Veranstaltungen aus vergangenen Semestern mit eigenem Semester`() {
        val page = scraper.parse(
            buildHtml(
                currentEntries = listOf(LOGISTIK_FIXTURE),
                pastEntries = listOf(MATH_PAST_FIXTURE)
            )
        )
        assertEquals(2, page.entries.size)
        val current = page.entries.first { it.lsfId == "120558" }
        val past = page.entries.first { it.lsfId == "117399" }
        assertEquals("Sommer 2026", current.semester)
        assertEquals("Winter 2025/26", past.semester)
    }

    @Test
    fun `parse merkt sich das aktuelle Semester aus dem 1sem-Container`() {
        val page = scraper.parse(
            buildHtml(
                currentEntries = listOf(LOGISTIK_FIXTURE),
                pastEntries = listOf(MATH_PAST_FIXTURE)
            )
        )
        assertEquals("Sommer 2026", page.currentSemester)
    }

    @Test
    fun `parse dedupiert doppelte publishid mehrerer Gruppen`() {
        // Ein Modul mit mehreren Gruppen rendert mehrere Leistungen_Inhalt-Blöcke
        // im selben h2 — wir wollen nur EINEN Eintrag pro publishid.
        val page = scraper.parse(buildHtml(currentEntries = listOf(BWL_MULTI_GRUPPE_FIXTURE)))
        assertEquals(1, page.entries.size)
        assertEquals("107622", page.entries.single().lsfId)
    }

    @Test
    fun `parse behandelt Veranstaltung ohne Code als Titel-only`() {
        val page = scraper.parse(buildHtml(currentEntries = listOf(KINDERVATER_NO_CODE_FIXTURE)))
        val entry = page.entries.single()
        assertNull(entry.code)
        assertTrue("title sollte Verabschiedung enthalten", entry.title.contains("Verabschiedung"))
    }

    @Test
    fun `parse liefert leere Page wenn kein aktueller Semester-Container existiert`() {
        val html = """<html><body><div>nothing useful</div></body></html>"""
        val page = scraper.parse(html)
        assertEquals(0, page.entries.size)
        assertEquals("", page.currentSemester)
    }

    @Test
    fun `parse extrahiert Lehrperson aus Termin-Tabelle wenn vorhanden`() {
        val entry = scraper.parse(buildHtml(currentEntries = listOf(MATH_WITH_LECTURER_FIXTURE)))
            .entries.single()
        assertNotNull(entry.lecturer)
        assertEquals("Kindervater", entry.lecturer)
    }

    private fun buildHtml(
        currentEntries: List<String>,
        pastEntries: List<String> = emptyList()
    ): String = """
        <html><body>
        <div class="functionnavi">
          <h2 onclick="anzeigen('VeranstAus1semSommer_2026_(angemeldet_oder_zugelassen)')">Sommer 2026 (angemeldet oder zugelassen):</h2>
          <div id="CaptionVeranstAus1sem" class="Container_CaptionVeranstAus"></div>
          <div class="Container_VeranstAus" id="VeranstAus1semSommer_2026_(angemeldet_oder_zugelassen)">
            ${currentEntries.joinToString("\n<hr>\n")}
          </div>
        </div>
        ${if (pastEntries.isNotEmpty()) """
        <div class="functionnavi">
          <h2 onclick="anzeigen('VeranstAus2semWinter_2025_26')">Winter 2025/26:</h2>
          <div class="Container_VeranstAus" id="VeranstAus2semWinter_2025_26">
            ${pastEntries.joinToString("\n<hr>\n")}
          </div>
        </div>
        """ else ""}
        </body></html>
    """.trimIndent()

    companion object {
        private val LOGISTIK_FIXTURE = """
            <h2>Veranstaltung:
              <a class="regular" href="https://lsf.uni-hildesheim.de/qisserver/rds?state=verpublish&amp;status=init&amp;vmfile=no&amp;publishid=120558&amp;moduleCall=webInfo">3204 Logistik und Produktion 1</a>
            </h2>
            <div class="Leistungen_Inhalt">
              Gruppe: 1-Gruppe
              <span class="grueneWarnung">angemeldet</span>
              <table summary="Termine der Veranstaltung Logistik und Produktion 1">
                <tr><th>Tag</th><th>Zeit</th><th>Rhythmus</th><th>Dauer</th><th>Raum</th><th>Lehrperson</th><th>Hinweis</th></tr>
                <tr>
                  <td>Do.</td>
                  <td>08:00 bis 10:00</td>
                  <td>wöchentlich</td>
                  <td></td>
                  <td><a href="#" hreflang="de">SC.B.0.37</a></td>
                  <td></td>
                  <td>&nbsp;</td>
                </tr>
              </table>
            </div>
        """.trimIndent()

        private val IT_PROJEKT_ZUGELASSEN_FIXTURE = """
            <h2>Veranstaltung:
              <a href="?state=verpublish&publishid=120688">3186 IT-Studienprojekt (BSc)</a>
            </h2>
            <div class="Leistungen_Inhalt">
              <span class="grueneWarnung">zugelassen</span>
              <table summary="Termine der Veranstaltung IT-Studienprojekt (BSc)">
                <tr><th>h</th><th>h</th><th>h</th><th>h</th><th>h</th><th>h</th><th>h</th></tr>
                <tr>
                  <td>Do.</td>
                  <td>16:00 bis 18:00</td>
                  <td>wöchentlich</td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                </tr>
              </table>
            </div>
        """.trimIndent()

        private val BWL_MULTI_GRUPPE_FIXTURE = """
            <h2>Veranstaltung:
              <a href="?state=verpublish&publishid=107622">3201 Grundlagen der Betriebswirtschaftslehre 2</a>
            </h2>
            <div class="Leistungen_Inhalt">
              Gruppe: 1-Gruppe
              <span class="grueneWarnung">angemeldet</span>
              <table summary="Termine der Veranstaltung BWL 2">
                <tr><th>x</th><th>x</th><th>x</th><th>x</th><th>x</th><th>x</th><th>x</th></tr>
                <tr><td>Di.</td><td>10:00 bis 12:00</td><td>wöchentlich</td><td></td><td><a>SC.B.0.37</a></td><td></td><td></td></tr>
              </table>
            </div>
            <div class="Leistungen_Inhalt">
              Gruppe: 7-Gruppe
              <span class="grueneWarnung">angemeldet</span>
              <table summary="Termine der Veranstaltung BWL 2">
                <tr><th>x</th><th>x</th><th>x</th><th>x</th><th>x</th><th>x</th><th>x</th></tr>
                <tr><td>Do.</td><td>14:00 bis 16:00</td><td>wöchentlich</td><td></td><td><a>SC.B.0.37</a></td><td></td><td></td></tr>
              </table>
            </div>
        """.trimIndent()

        private val KINDERVATER_NO_CODE_FIXTURE = """
            <h2>Veranstaltung:
              <a href="?state=verpublish&publishid=125313">Verabschiedung Kindervater</a>
            </h2>
            <div class="Leistungen_Inhalt">
              <span class="grueneWarnung">angemeldet</span>
              <table summary="Termine der Veranstaltung Verabschiedung">
                <tr><th>x</th><th>x</th><th>x</th><th>x</th><th>x</th><th>x</th><th>x</th></tr>
                <tr><td>Sa.</td><td>00:00 bis 02:00</td><td>Einzeltermin</td><td>am 04.07.2026</td><td><a>SC.A.0.09</a></td><td></td><td></td></tr>
              </table>
            </div>
        """.trimIndent()

        private val MATH_WITH_LECTURER_FIXTURE = """
            <h2>Veranstaltung:
              <a href="?state=verpublish&publishid=120126">5395 Mathematische Methoden IV: Statistik</a>
            </h2>
            <div class="Leistungen_Inhalt">
              <span class="grueneWarnung">angemeldet</span>
              <table summary="Termine der Veranstaltung Statistik">
                <tr><th>x</th><th>x</th><th>x</th><th>x</th><th>x</th><th>x</th><th>x</th></tr>
                <tr>
                  <td>Mi.</td>
                  <td>10:00 bis 12:00</td>
                  <td>wöchentlich</td>
                  <td></td>
                  <td><a>SC.A.0.09</a></td>
                  <td><a href="#">Kindervater</a></td>
                  <td></td>
                </tr>
              </table>
            </div>
        """.trimIndent()

        private val MATH_PAST_FIXTURE = """
            <h2>Veranstaltung:
              <a href="?state=verpublish&publishid=117399">5390 Mathematische Methoden III: Analysis</a>
            </h2>
            <div class="Leistungen_Inhalt">
              <span class="grueneWarnung">angemeldet</span>
              <table summary="Termine der Veranstaltung Analysis">
                <tr><th>x</th><th>x</th><th>x</th><th>x</th><th>x</th><th>x</th><th>x</th></tr>
                <tr><td>Do.</td><td>12:00 bis 14:00</td><td>wöchentlich</td><td></td><td><a>H 2</a></td><td></td><td></td></tr>
              </table>
            </div>
        """.trimIndent()
    }
}
