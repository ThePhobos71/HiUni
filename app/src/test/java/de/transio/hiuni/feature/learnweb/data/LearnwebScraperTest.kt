package de.transio.hiuni.feature.learnweb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    }
}
