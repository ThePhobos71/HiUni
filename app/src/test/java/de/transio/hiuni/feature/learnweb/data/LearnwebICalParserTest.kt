package de.transio.hiuni.feature.learnweb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnwebICalParserTest {

    private val parser = LearnwebICalParser()

    @Test
    fun `parseFeed extrahiert zwei VEVENTs mit allen Feldern`() {
        val ical = SAMPLE_FEED.trimIndent()
        val events = parser.parseFeed(ical)
        assertEquals(2, events.size)

        val first = events.first { it.uid == "event_4875@learnweb.uni-hildesheim.de" }
        assertEquals("Klausur Logistik 1", first.title)
        assertTrue(first.description?.contains("Tragen Sie") == true)
        assertEquals("Logistik und Produktion 1", first.courseName)
        assertEquals("https://www.uni-hildesheim.de/learnweb2026/mod/quiz/view.php?id=12345", first.url)
        assertNotNull(first.endEpoch)
        assertTrue(first.endEpoch!! > first.startEpoch)

        val punkt = events.first { it.uid == "event_4876@learnweb.uni-hildesheim.de" }
        // DTSTART ohne DTEND → endEpoch bleibt null
        assertNull(punkt.endEpoch)
        assertEquals("Abgabe-Frist", punkt.title)
    }

    @Test
    fun `parseFeed haelt leeren Input aus`() {
        assertTrue(parser.parseFeed("").isEmpty())
        assertTrue(parser.parseFeed(null).isEmpty())
        assertTrue(parser.parseFeed("kein valides ical").isEmpty())
    }

    @Test
    fun `parseFeed ueberspringt VEVENTs ohne UID`() {
        val ical = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            SUMMARY:Kein UID
            DTSTART:20260701T100000Z
            END:VEVENT
            BEGIN:VEVENT
            UID:has_uid@x
            SUMMARY:Mit UID
            DTSTART:20260702T100000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val events = parser.parseFeed(ical)
        assertEquals(1, events.size)
        assertEquals("has_uid@x", events.single().uid)
    }

    companion object {
        // Sample basiert auf einem typischen Moodle-iCal-Export. Bewusst kurz
        // gehalten — die wichtigen Felder UID/SUMMARY/DTSTART/DTEND/URL/
        // CATEGORIES/DESCRIPTION sind alle drin.
        private val SAMPLE_FEED = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Moodle Pty Ltd//NONSGML Moodle Version 4.3//EN
            METHOD:PUBLISH
            BEGIN:VEVENT
            UID:event_4875@learnweb.uni-hildesheim.de
            SUMMARY:Klausur Logistik 1
            DESCRIPTION:Tragen Sie sich rechtzeitig ein.
            DTSTART:20260715T080000Z
            DTEND:20260715T100000Z
            URL:https://www.uni-hildesheim.de/learnweb2026/mod/quiz/view.php?id=12345
            CATEGORIES:Logistik und Produktion 1
            END:VEVENT
            BEGIN:VEVENT
            UID:event_4876@learnweb.uni-hildesheim.de
            SUMMARY:Abgabe-Frist
            DTSTART:20260720T235900Z
            CATEGORIES:Informationssysteme
            END:VEVENT
            END:VCALENDAR
        """
    }
}
