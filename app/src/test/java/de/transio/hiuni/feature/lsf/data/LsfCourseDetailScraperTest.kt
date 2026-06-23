package de.transio.hiuni.feature.lsf.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LsfCourseDetailScraperTest {

    private val scraper = LsfCourseDetailScraper()

    @Test
    fun `parse liest Credits aus Grunddaten-Tabelle`() {
        val detail = scraper.parse(buildHtml(credits = "6"))
        assertEquals(6, detail.credits)
    }

    @Test
    fun `parse liest SWS als Zahl`() {
        val detail = scraper.parse(buildHtml(sws = "4"))
        assertEquals(4, detail.sws)
    }

    @Test
    fun `parse ignoriert Credits wenn Wert siehe NNNN als Referenz`() {
        // Tutorien haben oft "Credits: siehe 3530" — das ist eine Referenz, nicht
        // 3530 ECTS.
        val detail = scraper.parse(buildHtml(credits = "siehe 3530"))
        assertNull(detail.credits)
    }

    @Test
    fun `parse ignoriert implausible Credits-Werte über 30`() {
        val detail = scraper.parse(buildHtml(credits = "200"))
        assertNull(detail.credits)
    }

    @Test
    fun `parse liest Veranstaltungsart und Kurztext`() {
        val detail = scraper.parse(
            buildHtml(art = "Vorlesung mit Übung", kurztext = "Mathe IV")
        )
        assertEquals("Vorlesung mit Übung", detail.veranstaltungsart)
        assertEquals("Mathe IV", detail.kurztext)
    }

    @Test
    fun `parse bevorzugt verantwortlich-und-durchfuehrend bei den Personen`() {
        val detail = scraper.parse(
            buildHtml(persons = listOf(
                "Tarasov, Alexandr, Dr." to "verantwortlich, nicht durchführend",
                "Fuchs-Kreiß, Alexander, Professor Dr." to "verantwortlich und durchführend",
                "Verovkin, Glib" to "nicht durchführend, nicht verantwortlich"
            ))
        )
        assertEquals("Prof. Dr. Alexander Fuchs-Kreiß", detail.responsiblePerson)
    }

    @Test
    fun `parse formatiert Name ohne Titel sinnvoll`() {
        val detail = scraper.parse(
            buildHtml(persons = listOf("Verovkin, Glib" to "verantwortlich und durchführend"))
        )
        assertEquals("Glib Verovkin", detail.responsiblePerson)
    }

    @Test
    fun `parse fällt auf durchfuehrend zurück wenn keine verantwortliche Person`() {
        val detail = scraper.parse(
            buildHtml(persons = listOf(
                "Müller, Anna, Dr." to "durchführend"
            ))
        )
        assertEquals("Dr. Anna Müller", detail.responsiblePerson)
    }

    @Test
    fun `parse liest Lerninhalte als Plain-Text`() {
        val lerninhalte = "<p>Erarbeitung von Wahrscheinlichkeitstheorie und Statistik.</p>" +
            "<p>• Deskriptive Statistik</p>" +
            "<p>• Hypothesentests</p>"
        val detail = scraper.parse(buildHtml(lerninhalte = lerninhalte))
        assertNotNull(detail.description)
        assertTrue(detail.description!!.contains("Statistik"))
        assertTrue(detail.description!!.contains("Hypothesentests"))
    }

    @Test
    fun `parse liest Bemerkung und Zielgruppe getrennt`() {
        val detail = scraper.parse(
            buildHtml(
                bemerkung = "<p>Beginn der Vorlesung: 27.10.2020</p>",
                zielgruppe = "<p>B.Sc. IMIT, B.Sc. Wirtschaftsinformatik</p>"
            )
        )
        assertNotNull(detail.remark)
        assertTrue(detail.remark!!.contains("27.10.2020"))
        assertNotNull(detail.targetAudience)
        assertTrue(detail.targetAudience!!.contains("IMIT"))
    }

    @Test
    fun `parse liest Modulkürzel aus LSF-Module-Tabelle`() {
        val detail = scraper.parse(buildHtml(modulkuerzel = "IT-EINF1"))
        assertEquals("IT-EINF1", detail.moduleAbbreviation)
    }

    @Test
    fun `parse liefert EMPTY wenn keine Tabellen vorhanden`() {
        val detail = scraper.parse("<html><body><p>nichts</p></body></html>")
        assertTrue(detail.isEmpty)
        assertNull(detail.credits)
    }

    private fun buildHtml(
        credits: String? = null,
        sws: String? = null,
        art: String? = null,
        kurztext: String? = null,
        persons: List<Pair<String, String>> = emptyList(),
        lerninhalte: String? = null,
        bemerkung: String? = null,
        zielgruppe: String? = null,
        modulkuerzel: String? = null
    ): String {
        val grunddatenRows = listOfNotNull(
            art?.let { "<tr><th>Veranstaltungsart</th><td>$it</td></tr>" },
            kurztext?.let { "<tr><th>Kurztext</th><td>$it</td></tr>" },
            sws?.let { "<tr><th>SWS</th><td>$it</td></tr>" },
            credits?.let { "<tr><th>Credits</th><td>$it</td></tr>" }
        ).joinToString("\n")

        val personRows = persons.joinToString("\n") { (name, role) ->
            "<tr><td><a href=\"#\">$name</a></td><td>$role</td></tr>"
        }

        val angabenRows = listOfNotNull(
            lerninhalte?.let { "<tr><th>Lerninhalte</th><td class=\"mod_n\">$it</td></tr>" },
            bemerkung?.let { "<tr><th>Bemerkung</th><td class=\"mod_n\">$it</td></tr>" },
            zielgruppe?.let { "<tr><th>Zielgruppe</th><td class=\"mod_n\">$it</td></tr>" }
        ).joinToString("\n")

        val moduleTable = modulkuerzel?.let {
            """
            <table summary="Übersicht über die zugehörigen Module">
              <caption>LSF - Module</caption>
              <tr><th>Modulkürzel</th><th>Modultitel</th></tr>
              <tr><td>$it</td><td><a href="#">Modultitel</a></td></tr>
            </table>
            """.trimIndent()
        }.orEmpty()

        return """
            <html><body>
            <table summary="Grunddaten zur Veranstaltung">
              $grunddatenRows
            </table>
            <table summary="Durchführende Dozenten">
              <tr><th>Kontaktperson</th><th>Zuständigkeit</th></tr>
              $personRows
            </table>
            <table summary="Weitere Angaben zur Veranstaltung">
              $angabenRows
            </table>
            $moduleTable
            </body></html>
        """.trimIndent()
    }
}
