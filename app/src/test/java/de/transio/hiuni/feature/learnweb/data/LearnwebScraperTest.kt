package de.transio.hiuni.feature.learnweb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class LearnwebScraperTest {

    private val scraper = LearnwebScraper()

    @Test
    fun `parseCourses extrahiert 8 Kurse aus dem Sample`() {
        val courses = scraper.parseCourses(SAMPLE_HTML)
        // 8 Optionen ohne "Alle Kurse"
        assertEquals(8, courses.size)
        val ids = courses.map { it.courseId }.toSet()
        assertEquals(
            setOf(1663L, 837L, 841L, 845L, 847L, 1004L, 1464L, 1659L),
            ids
        )
    }

    @Test
    fun `parseCourses uebernimmt Tree-Title wenn Select-Text gekuerzt`() {
        val courses = scraper.parseCourses(SAMPLE_HTML)
        // value="841" hat im Select "Buchung für Seminar- und IT-Stu..." (Truncated),
        // Tree liefert vollständigeren Titel (wir setzen den Tree-Titel explizit).
        val buchung = courses.first { it.courseId == 841L }
        assertEquals("SoSe 2026: 3198 Buchung für Seminar- und IT-Studienprojekte", buchung.name)
    }

    @Test
    fun `parseCourses skip Alle-Kurse mit value 1`() {
        val courses = scraper.parseCourses(SAMPLE_HTML)
        assertTrue(courses.none { it.courseId == 1L })
        assertTrue(courses.none { it.name == "Alle Kurse" })
    }

    @Test
    fun `parseCourses ohne Tree-Augmentation funktioniert ueber Select alleine`() {
        val selectOnly = """
            <html><body>
            <select id="calendar-course-filter-1" name="course">
              <option selected="selected" value="1">Alle Kurse</option>
              <option value="100">Kurs A</option>
              <option value="200">Kurs B</option>
            </select>
            </body></html>
        """.trimIndent()
        val courses = scraper.parseCourses(selectOnly)
        assertEquals(2, courses.size)
        assertEquals("Kurs A", courses.first { it.courseId == 100L }.name)
        assertEquals("Kurs B", courses.first { it.courseId == 200L }.name)
        assertNull(courses.first { it.courseId == 100L }.treeHref)
    }

    @Test
    fun `parseCourses ohne Select faellt auf Tree zurueck`() {
        val treeOnly = """
            <html><body>
            <ul>
              <li class="type_course depth_3" data-node-key="555">
                <p class="tree_item branch">
                  <a title="Nur-Tree-Kurs"
                     href="https://www.uni-hildesheim.de/learnweb2026/course/view.php?id=555">
                    Nur-Tree-Kurs
                  </a>
                </p>
              </li>
            </ul>
            </body></html>
        """.trimIndent()
        val courses = scraper.parseCourses(treeOnly)
        assertEquals(1, courses.size)
        val only = courses.single()
        assertEquals(555L, only.courseId)
        assertEquals("Nur-Tree-Kurs", only.name)
        assertNotNull(only.treeHref)
    }

    @Test
    fun `parseCourses bei leerem HTML liefert leere Liste`() {
        assertEquals(emptyList<ParsedCourse>(), scraper.parseCourses(""))
        assertEquals(emptyList<ParsedCourse>(), scraper.parseCourses("<html></html>"))
    }

    @Test
    fun `parseAssignments bei leerem HTML liefert leere Liste`() {
        assertEquals(emptyList<ParsedAssignment>(), scraper.parseAssignments(""))
        assertEquals(emptyList<ParsedAssignment>(), scraper.parseAssignments("<html></html>"))
    }

    @Test
    fun `parseAssignments extrahiert die vier Assignments aus dem Sample`() {
        val assignments = scraper.parseAssignments(ASSIGNMENTS_SAMPLE_HTML)
        val ids = assignments.map { it.eventId }.toSet()
        assertEquals(setOf(4875L, 1304L, 5037L, 6461L), ids)
        // Dedupliziert: das vierte Markup hat `eventId=6461` doppelt.
        assertEquals(4, assignments.size)
    }

    @Test
    fun `parseAssignments dedupliziert ueber eventId`() {
        // Selbes Markup zweimal — Scraper muss auf eindeutige IDs reduzieren.
        val doubled = "<html><body>$ASSIGNMENTS_SAMPLE_INNER$ASSIGNMENTS_SAMPLE_INNER</body></html>"
        val parsed = scraper.parseAssignments(doubled)
        assertEquals(4, parsed.size)
    }

    @Test
    fun `parseAssignments verwendet Uhrzeit aus Title wenn vorhanden`() {
        val parsed = scraper.parseAssignments(ASSIGNMENTS_SAMPLE_HTML)
        // eventId=1304 hat im Title "23:59 Uhr" — entsprechend müssen Stunde
        // und Minute genau auf 23:59 lokaler Zeit landen.
        val assignment = parsed.first { it.eventId == 1304L }
        val zoned = Instant.ofEpochMilli(assignment.dueEpochMillis)
            .atZone(ZoneId.systemDefault())
        assertEquals(LocalTime.of(23, 59), zoned.toLocalTime())
    }

    @Test
    fun `parseAssignments faellt auf 23 59 zurueck wenn keine Zeit im Title`() {
        val parsed = scraper.parseAssignments(ASSIGNMENTS_SAMPLE_HTML)
        // eventId=5037 hat keine Uhrzeit im Title → Default 23:59.
        val assignment = parsed.first { it.eventId == 5037L }
        val zoned = Instant.ofEpochMilli(assignment.dueEpochMillis)
            .atZone(ZoneId.systemDefault())
        assertEquals(LocalTime.of(23, 59), zoned.toLocalTime())
    }

    @Test
    fun `parseAssignments behaelt URL und Title pro Eintrag`() {
        val parsed = scraper.parseAssignments(ASSIGNMENTS_SAMPLE_HTML)
        val a = parsed.first { it.eventId == 4875L }
        assertEquals(
            "https://www.uni-hildesheim.de/learnweb2026/mod/assign/view.php?id=32906",
            a.url
        )
        assertTrue("title sollte 'Hausaufgabe 2' enthalten", a.title.contains("Hausaufgabe 2"))
        assertEquals("mod_assign", a.rawComponent)
    }

    @Test
    fun `parseAssignments ignoriert nicht-mod_assign-Eintraege`() {
        val mixed = """
            <html><body>
            <table><tbody><tr>
            <td class="day hasevent" data-day-timestamp="1780351200">
              <ul>
                <li data-event-component="mod_quiz" data-event-eventtype="due">
                  <a data-event-id="9999"
                     href="https://www.uni-hildesheim.de/learnweb2026/mod/quiz/view.php?id=1"
                     title="Quiz Eintrag">
                    <span class="eventname">Quiz Eintrag</span>
                  </a>
                </li>
                <li data-event-component="mod_assign" data-event-eventtype="due">
                  <a data-event-id="100"
                     href="https://www.uni-hildesheim.de/learnweb2026/mod/assign/view.php?id=42"
                     title="Echte Abgabe">
                    <span class="eventname">Echte Abgabe</span>
                  </a>
                </li>
              </ul>
            </td>
            </tr></tbody></table>
            </body></html>
        """.trimIndent()
        val parsed = scraper.parseAssignments(mixed)
        assertEquals(1, parsed.size)
        assertEquals(100L, parsed.single().eventId)
    }

    companion object {
        // Sample basiert exakt auf dem vom User dokumentierten Layout. Das Select
        // hat "..." beim Buchungs-Kurs (gekürzt), der Tree liefert den vollen
        // Titel — Scraper soll den Tree-Titel bevorzugen.
        private val SAMPLE_HTML = """
            <html><body>
            <select id="calendar-course-filter-1" name="course">
              <option selected="selected" value="1">Alle Kurse</option>
              <option value="1663">Fachschaft iplus+</option>
              <option value="837">SoSe 2026: 3186 IT-Studienprojekt (BSc)</option>
              <option value="841">SoSe 2026: 3198 Buchung für Seminar- und IT-Stu...</option>
              <option value="845">SoSe 2026: 3204 Logistik und Produktion 1</option>
              <option value="847">SoSe 2026: 3207 Nachhaltigkeitsmanagement</option>
              <option value="1004">SoSe 2026: 3670 Mobile Software Engineering</option>
              <option value="1464">SoSe 2026: 5395 Mathematische Methoden IV: Statistik</option>
              <option value="1659">Studierendenvertretung (AStA/StuPa)</option>
            </select>
            <ul>
              <li class="type_course depth_3" data-node-key="841">
                <p class="tree_item branch">
                  <a title="SoSe 2026: 3198 Buchung für Seminar- und IT-Studienprojekte"
                     href="https://www.uni-hildesheim.de/learnweb2026/course/view.php?id=841">
                    SoSe 2026: 3198 Buchung für Seminar- und IT-Stu...
                  </a>
                </p>
              </li>
              <li class="type_course depth_3" data-node-key="1659">
                <p class="tree_item branch">
                  <a title="Studierendenvertretung (AStA/StuPa)"
                     href="https://www.uni-hildesheim.de/learnweb2026/course/view.php?id=1659">
                    Studierendenvertretung (AStA/StuPa)
                  </a>
                </p>
              </li>
            </ul>
            </body></html>
        """.trimIndent()

        // Inneres Markup mit den 4 Assignment-Items — wir wrappen das einmal in
        // `<html><body>` für den Standard-Test, und doppelt für den Dedup-Test.
        // Die td-Strukturen sind reduziert auf das fürs Scraping Nötige.
        // Zeitstempel:
        //  - 4875: 2026-06-02 (Title ohne explizite Uhrzeit → Default 23:59)
        //  - 1304: 2026-06-19 (Title mit "23:59 Uhr")
        //  - 5037: 2026-06-25 (kein Uhrzeit-Match → 23:59)
        //  - 6461: 2026-06-30 (Title mit "12:00 Uhr") — taucht ZWEIMAL auf,
        //          Dedup soll greifen.
        // data-day-timestamp ist Sekunden-UTC für Tag-Beginn in Berlin-Zeit
        // (CEST = UTC+2 im Juni). Beispiel: 2026-06-02 00:00 CEST = 1780351200.
        private val ASSIGNMENTS_SAMPLE_INNER = """
            <table><tbody><tr>
            <td class="day text-sm-center clickable hasevent"
                data-day="2" data-day-timestamp="1780351200"
                data-region="day">
              <ul>
                <li data-region="event-item"
                    data-event-component="mod_assign"
                    data-event-eventtype="due">
                  <a data-action="view-event"
                     data-event-id="4875"
                     href="https://www.uni-hildesheim.de/learnweb2026/mod/assign/view.php?id=32906"
                     title="Hausaufgabe 2 ist fällig.">
                    <span class="calendar-circle calendar_event_course">&nbsp;</span>
                    <span class="eventname">Hausaufgabe 2 ist fällig.</span>
                  </a>
                </li>
              </ul>
            </td>
            <td class="day clickable hasevent"
                data-day-timestamp="1781820000">
              <ul>
                <li data-event-component="mod_assign" data-event-eventtype="due">
                  <a data-event-id="1304"
                     href="https://www.uni-hildesheim.de/learnweb2026/mod/assign/view.php?id=11"
                     title="Abgabe der Bonusaufgabe [bis 19.06.2026, 23:59 Uhr] ist fällig.">
                    <span class="eventname">Abgabe der Bonusaufgabe ist fällig.</span>
                  </a>
                </li>
              </ul>
            </td>
            <td class="day clickable hasevent"
                data-day-timestamp="1782338400">
              <ul>
                <li data-event-component="mod_assign" data-event-eventtype="due">
                  <a data-event-id="5037"
                     href="https://www.uni-hildesheim.de/learnweb2026/mod/assign/view.php?id=22"
                     title="Übungsblatt 5 ist fällig.">
                    <span class="eventname">Übungsblatt 5 ist fällig.</span>
                  </a>
                </li>
              </ul>
            </td>
            <td class="day clickable hasevent"
                data-day-timestamp="1782770400">
              <ul>
                <li data-event-component="mod_assign" data-event-eventtype="due">
                  <a data-event-id="6461"
                     href="https://www.uni-hildesheim.de/learnweb2026/mod/assign/view.php?id=33"
                     title="Projektabgabe [bis 30.06.2026, 12:00 Uhr] ist fällig.">
                    <span class="eventname">Projektabgabe ist fällig.</span>
                  </a>
                </li>
                <li data-event-component="mod_assign" data-event-eventtype="due">
                  <a data-event-id="6461"
                     href="https://www.uni-hildesheim.de/learnweb2026/mod/assign/view.php?id=33"
                     title="Projektabgabe [bis 30.06.2026, 12:00 Uhr] ist fällig.">
                    <span class="eventname">Projektabgabe ist fällig.</span>
                  </a>
                </li>
              </ul>
            </td>
            </tr></tbody></table>
        """.trimIndent()

        private val ASSIGNMENTS_SAMPLE_HTML = """
            <html><body>
            $ASSIGNMENTS_SAMPLE_INNER
            </body></html>
        """.trimIndent()
    }
}
