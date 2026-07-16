package de.transio.hiuni.feature.grades.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tests für [NotenspiegelScraper] gegen die echten (anonymisierten + synthetisch
 * durchgewürfelten) QIS-Fixtures unter `src/test/resources/lsf/`.
 *
 * Die Notenwerte, Prüfungsdaten und die Modul-zu-Prüfungsnr-Zuordnung der Fixture
 * sind absichtlich verfälscht (Datenschutz) — die Assertions hier spiegeln die
 * durchgewürfelten Werte, NICHT ein echtes Transcript.
 */
class NotenspiegelScraperTest {

    private val scraper = NotenspiegelScraper()

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/lsf/$name")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("Fixture /lsf/$name nicht gefunden")

    private fun epochDay(y: Int, m: Int, d: Int): Long = LocalDate.of(y, m, d).toEpochDay()

    // ---------------------------------------------------------------------
    // Menü-Fixture: findNotenspiegelUrl
    // ---------------------------------------------------------------------

    @Test
    fun `findNotenspiegelUrl extrahiert Notenspiegel-Link inkl asi-Token`() {
        val url = scraper.findNotenspiegelUrl(fixture("notenspiegel_menu.html"))
        assertNotNull("Notenspiegel-URL sollte gefunden werden", url)
        requireNotNull(url)
        assertTrue("URL muss den Notenspiegel-State tragen", url.contains("state=notenspiegelStudent"))
        assertTrue("URL muss das session-gebundene asi-Token tragen", url.contains("asi=TESTASI0123456789abc"))
    }

    @Test
    fun `findNotenspiegelUrl liefert null wenn der Link fehlt`() {
        val html = """
            <html><body>
              <div id="makronavigation">
                <ul class="menue">
                  <li><a href="https://lsf.uni-hildesheim.de/qisserver/rds?state=wplan">Persönlicher Stundenplan</a></li>
                </ul>
              </div>
            </body></html>
        """.trimIndent()
        assertNull(scraper.findNotenspiegelUrl(html))
    }

    // ---------------------------------------------------------------------
    // Lang-Fixture: parse
    // ---------------------------------------------------------------------

    private fun parseLang(): NotenspiegelResult = scraper.parse(fixture("notenspiegel_lang.html"))

    /** Bequemer Zugriff auf nur die Leistungszeilen. */
    private fun langGrades(): List<ParsedGrade> = parseLang().grades

    @Test
    fun `parse liest genau die 38 Leistungszeilen und keine Konto-Zeilen`() {
        val result = parseLang()
        assertEquals("Erwartet 38 Leistungszeilen (Konto-/Summen-Zeilen NICHT enthalten)", 38, result.grades.size)

        // Keine der geparsten Zeilen darf ein Gruppen-/Summen-Konto sein:
        // Konto-Nummern (1001, 1100, 8997, 8999, …) tauchen nur als kontoNr auf,
        // niemals als eigene pruefungsNr einer Leistungszeile.
        val kontoNummern = setOf("1001", "1100", "1200", "1300", "1400", "2000", "2200",
            "3000", "3100", "4000", "4100", "4120", "5000", "8995", "8997", "8999")
        val pruefungsNummern = result.grades.map { it.pruefungsNr }.toSet()
        assertTrue(
            "Konto-Nummern dürfen nicht als Leistungszeile geparst werden",
            pruefungsNummern.none { it in kontoNummern }
        )
    }

    @Test
    fun `parse Stichprobe Web-Datenbankpraktikum-Zeile labnr 2438258`() {
        val g = langGrades().single { it.labnr == 2438258L }
        assertEquals(GradeStatus.PASSED, g.status)
        assertEquals(2.3, g.note!!, 0.0001) // durchgewürfelter Wert (Original war 1,0)
        assertEquals(6, g.bonusLp)
        assertEquals(1, g.versuch)
        assertEquals("23011", g.pruefungsNr)
        assertEquals(epochDay(2025, 3, 30), g.pruefungsDatum) // durchgewürfeltes Datum
    }

    @Test
    fun `parse angemeldete Wiederholung ohne Note - Pruefungsnr 214104 Versuch 3`() {
        val angemeldet = langGrades().filter { it.pruefungsNr == "214104" && it.versuch == 3 }
        assertEquals(1, angemeldet.size)
        val g = angemeldet.single()
        assertEquals(GradeStatus.REGISTERED, g.status)
        assertNull("angemeldete Prüfung hat keine Note", g.note)
        assertNull("dritter Versuch ohne Info-Link hat keine labnr", g.labnr)
    }

    @Test
    fun `parse Zeile ohne Klassenspiegel-Link - Pruefungsnr 1801 hat keine labnr`() {
        val g = langGrades().single { it.pruefungsNr == "1801" }
        assertNull(g.labnr)
        assertEquals(GradeStatus.REGISTERED, g.status)
        assertEquals("p:1801#1", g.mergeKey) // fällt auf pruefungsNr#versuch zurück
    }

    @Test
    fun `parse Praktikum ohne Note - Pruefungsnr 5011 PASSED mit 12 LP`() {
        val g = langGrades().single { it.pruefungsNr == "5011" }
        assertEquals(GradeStatus.PASSED, g.status)
        assertNull("Praktikum ist bestanden ohne Note", g.note)
        assertEquals(12, g.bonusLp)
        assertNull("Praktikum hat keinen Klassenspiegel-Link", g.labnr)
    }

    @Test
    fun `parse qis_new-Zeilen werden ganz normal geparst`() {
        // 1801 und 25011 tragen zusätzlich die qis_new-Markierung in ihren td-Klassen;
        // das darf die Klassifizierung als Leistungszeile (tabelle1_) nicht stören.
        val grades = langGrades()
        val nachhaltigkeit = grades.single { it.pruefungsNr == "25011" }
        assertEquals(GradeStatus.REGISTERED, nachhaltigkeit.status)
        assertNotNull(grades.single { it.pruefungsNr == "1801" })
    }

    @Test
    fun `parse Wiederholungsversuche gleicher Pruefungsnr tragen konsistenten Modulnamen`() {
        // 214103 kommt viermal vor — alle vier Zeilen müssen denselben (durchgewürfelten)
        // Titel tragen, sonst wäre die Modulzuordnung inkonsistent.
        val mathIII = langGrades().filter { it.pruefungsNr == "214103" }
        assertEquals(4, mathIII.size)
        assertEquals(1, mathIII.map { it.titel }.toSet().size)
    }

    @Test
    fun `parse gescheiterte Zeilen behalten Note 5,0`() {
        val failed = langGrades().filter { it.status == GradeStatus.FAILED }
        assertTrue("Fixture enthält nicht-bestandene Zeilen", failed.isNotEmpty())
        assertTrue(
            "alle nicht-bestandenen Zeilen tragen die deutsche 5,0",
            failed.all { it.note == 5.0 }
        )
    }

    @Test
    fun `parse Summary liest durchgewuerfelten GPA und LP-Summen`() {
        val summary = parseLang().summary
        assertNotNull(summary)
        requireNotNull(summary)
        assertEquals(2.2, summary.gpa!!, 0.0001)   // Konto 8997, Original war 2,6
        assertEquals(111, summary.weightedLp)       // gewichtete LP, Original war 109
        assertEquals(118, summary.totalLp)          // Konto 8999, Original war 121
    }

    // ---------------------------------------------------------------------
    // Datenschutz: Stammdaten dürfen NIRGENDS im Parse-Ergebnis erscheinen
    // ---------------------------------------------------------------------

    @Test
    fun `parse traegt keine Stammdaten in irgendeinem geparsten String`() {
        val result = parseLang()
        val allStrings = buildList {
            result.grades.forEach { g ->
                add(g.pruefungsNr)
                add(g.titel)
                g.kontoNr?.let { add(it) }
                g.kontoName?.let { add(it) }
                add(g.semester)
                add(g.vermerk)
            }
        }
        val forbidden = listOf("Max Mustermann", "Mustermann", "123456", "Musterstadt", "Musterstraße")
        for (needle in forbidden) {
            assertTrue(
                "Stammdatum \"$needle\" darf in keinem geparsten Feld auftauchen",
                allStrings.none { it.contains(needle) }
            )
        }
        // Sicherheitsnetz: die Matrikelnummer darf auch nicht versehentlich als
        // pruefungsNr durchrutschen.
        assertFalse(result.grades.any { it.pruefungsNr == "123456" })
    }
}
