package de.transio.hiuni.feature.grades.data

import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.feature.grades.data.GradeDiffNotifier.GradeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für die reine Noten-Diff-Push-Logik. Kein HTTP, kein Room, kein Firebase,
 * genau deshalb liegt die Entscheidung in [GradeDiffNotifier].
 */
class GradeDiffNotifierTest {

    private fun registered(labnr: Long, titel: String = "Prüfung $labnr") =
        GradeSnapshot("l:$labnr", labnr, titel, note = null, status = GradeStatus.REGISTERED)

    private fun passed(labnr: Long, titel: String = "Prüfung $labnr", note: Double? = 2.0) =
        GradeSnapshot("l:$labnr", labnr, titel, note = note, status = GradeStatus.PASSED)

    private val baseline = listOf(passed(1, "Mathe I", 1.7), registered(2, "Diskrete Strukturen"))

    @Test
    fun `leere Baseline erzeugt keine Pushes trotz vieler benoteter Leistungen`() {
        val current = (1L..12L).map { passed(it) }
        assertTrue(GradeDiffNotifier.decide(previous = emptyList(), current = current).isEmpty())
    }

    @Test
    fun `neue bereits benotete Zeile erzeugt Einzel-Push`() {
        val current = baseline + passed(3, "Programmierung I", 1.3)
        val pushes = GradeDiffNotifier.decide(baseline, current)
        assertEquals(1, pushes.size)
        assertEquals("Neue Note eingetragen", pushes[0].title)
        assertEquals("Programmierung I", pushes[0].body)
        assertEquals("grade:3", pushes[0].refKey)
        assertEquals(NotificationKind.GRADE, pushes[0].kind)
    }

    @Test
    fun `neue angemeldete Zeile ohne Ergebnis erzeugt keinen Push`() {
        val current = baseline + registered(3)
        assertTrue(GradeDiffNotifier.decide(baseline, current).isEmpty())
    }

    @Test
    fun `Wechsel von angemeldet auf benotet erzeugt Push`() {
        val current = listOf(passed(1, "Mathe I", 1.7), passed(2, "Diskrete Strukturen", 2.3))
        val pushes = GradeDiffNotifier.decide(baseline, current)
        assertEquals(1, pushes.size)
        assertEquals("Diskrete Strukturen", pushes[0].body)
        assertEquals("grade:2", pushes[0].refKey)
    }

    @Test
    fun `Wechsel auf nicht bestanden ohne Note erzeugt Push`() {
        val failed = GradeSnapshot("l:2", 2, "Diskrete Strukturen", null, GradeStatus.FAILED)
        val pushes = GradeDiffNotifier.decide(baseline, listOf(passed(1, "Mathe I", 1.7), failed))
        assertEquals(1, pushes.size)
        assertEquals("grade:2", pushes[0].refKey)
    }

    @Test
    fun `Note erscheint nachtraeglich bei bereits bestandener Zeile erzeugt Push`() {
        val before = listOf(passed(5, "Seminar", note = null))
        val after = listOf(passed(5, "Seminar", note = 1.0))
        assertEquals(1, GradeDiffNotifier.decide(before, after).size)
    }

    @Test
    fun `unveraenderter Stand erzeugt keine Pushes`() {
        assertTrue(GradeDiffNotifier.decide(baseline, baseline).isEmpty())
    }

    @Test
    fun `Notenkorrektur einer schon benoteten Leistung erzeugt keinen Push`() {
        val current = listOf(passed(1, "Mathe I", 1.3), registered(2, "Diskrete Strukturen"))
        assertTrue(GradeDiffNotifier.decide(baseline, current).isEmpty())
    }

    @Test
    fun `verschwundene Zeile erzeugt keinen Push`() {
        assertTrue(GradeDiffNotifier.decide(baseline, listOf(passed(1, "Mathe I", 1.7))).isEmpty())
    }

    @Test
    fun `bis zur Schwelle gibt es je Note einen Einzel-Push`() {
        val newOnes = (10L until 10L + GradeDiffNotifier.BULK_THRESHOLD).map { passed(it) }
        val pushes = GradeDiffNotifier.decide(baseline, baseline + newOnes)
        assertEquals(GradeDiffNotifier.BULK_THRESHOLD, pushes.size)
        assertEquals(newOnes.map { "grade:${it.labnr}" }, pushes.map { it.refKey })
        assertTrue(pushes.all { it.title == "Neue Note eingetragen" })
    }

    @Test
    fun `ueber der Schwelle gibt es genau eine Sammel-Meldung`() {
        val newOnes = listOf(
            passed(10, "A"), passed(11, "B"), passed(12, "C"), passed(13, "D"), passed(14, "E")
        )
        val pushes = GradeDiffNotifier.decide(baseline, baseline + newOnes)
        assertEquals(1, pushes.size)
        assertEquals("5 neue Noten eingetragen", pushes[0].title)
        assertEquals("A, B, C und 2 weitere", pushes[0].body)
        assertTrue(pushes[0].refKey.startsWith("grades:bulk:5:"))
        assertEquals(NotificationKind.GRADE, pushes[0].kind)
    }

    @Test
    fun `Sammel-RefKey unterscheidet verschiedene Noten-Schuebe`() {
        val a = GradeDiffNotifier.decide(baseline, baseline + (10L..13L).map { passed(it) })
        val b = GradeDiffNotifier.decide(baseline, baseline + (20L..23L).map { passed(it) })
        assertNotEquals(a[0].refKey, b[0].refKey)
    }

    @Test
    fun `Zeile ohne labnr nutzt Titel-Fallback im RefKey`() {
        val noLab = GradeSnapshot("p:1801#1", null, "Praktikum", 1.0, GradeStatus.PASSED)
        val pushes = GradeDiffNotifier.decide(baseline, baseline + noLab)
        assertEquals("grade:t:Praktikum", pushes.single().refKey)
    }

    @Test
    fun `kein Push-Text verraet die Note`() {
        val newOnes = listOf(passed(3, "Programmierung I", 1.3))
        val single = GradeDiffNotifier.decide(baseline, baseline + newOnes).single()
        assertFalse(single.title.contains("1,3") || single.body.contains("1,3") || single.body.contains("1.3"))
    }
}
