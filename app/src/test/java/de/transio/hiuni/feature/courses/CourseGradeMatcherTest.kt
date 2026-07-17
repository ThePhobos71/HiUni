package de.transio.hiuni.feature.courses

import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.grades.data.GradeEntity
import de.transio.hiuni.feature.grades.data.GradeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests für die reine Match-Logik [CourseGradeMatcher]. Deckt die vier
 * Match-Pfade (Veranstaltungs-Nr, Titel+Semester, Wiederholung, kein Match) sowie
 * die Präzedenz der manuellen Note ab.
 */
class CourseGradeMatcherTest {

    private fun course(
        name: String,
        semester: String = "WiSe 24/25",
        lsfCode: String? = null,
        grade: String? = null
    ) = CourseEntity(
        id = "c-$name",
        name = name,
        professor = "Prof",
        credits = 6,
        semester = semester,
        source = CourseEntity.SOURCE_LSF,
        lsfCode = lsfCode,
        grade = grade
    )

    private fun grade(
        titel: String,
        veranstaltungsNr: String? = null,
        semester: String = "WiSe 24/25",
        note: Double? = null,
        status: GradeStatus = GradeStatus.PASSED,
        versuch: Int = 1,
        pruefungsDatum: Long? = null,
        rowId: Long = 1
    ) = GradeEntity(
        rowId = rowId,
        mergeKey = "l:$rowId",
        labnr = rowId,
        pruefungsNr = rowId.toString(),
        titel = titel,
        veranstaltungsNr = veranstaltungsNr,
        kontoNr = null,
        kontoName = null,
        semester = semester,
        note = note,
        status = status,
        bonusLp = 6,
        vermerk = "",
        versuch = versuch,
        pruefungsDatum = pruefungsDatum,
        fetchedAt = 0L
    )

    // ── lsfCode-Treffer (primärer Pfad) ─────────────────────────────────────

    @Test
    fun `matcht ueber Veranstaltungs-Nr gegen lsfCode`() {
        val course = course("Betriebliche Informationssysteme", lsfCode = "3202")
        val grades = listOf(
            grade("Betriebliche Informationssysteme", veranstaltungsNr = "3202", note = 2.7),
            grade("Anderes Fach", veranstaltungsNr = "9999", note = 1.0, rowId = 2)
        )
        val eff = CourseGradeMatcher.effectiveGrade(course, grades)
        assertEquals(GradeSource.NOTENSPIEGEL, eff.source)
        assertEquals("2,7", eff.label)
    }

    @Test
    fun `Veranstaltungs-Nr schlaegt Titel wenn beide passen wuerden`() {
        // lsfCode matcht row 1 (andere Note), Titel wuerde row 2 matchen.
        val course = course("Mathe", lsfCode = "5210")
        val grades = listOf(
            grade("Voellig anderer Titel", veranstaltungsNr = "5210", note = 1.3, rowId = 1),
            grade("Mathe", veranstaltungsNr = "0000", note = 4.0, rowId = 2)
        )
        val eff = CourseGradeMatcher.effectiveGrade(course, grades)
        assertEquals("1,3", eff.label)
    }

    // ── Titel + Semester Fallback ───────────────────────────────────────────

    @Test
    fun `Titel-Fallback matcht bei fehlendem lsfCode inklusive Klammer-Suffix`() {
        val course = course("Betriebliche Informationssysteme (Vorlesung)", lsfCode = null)
        val grades = listOf(
            grade("Betriebliche Informationssysteme", note = 2.0)
        )
        val eff = CourseGradeMatcher.effectiveGrade(course, grades)
        assertEquals(GradeSource.NOTENSPIEGEL, eff.source)
        assertEquals("2,0", eff.label)
    }

    @Test
    fun `Titel-Fallback greift nur bei gleichem Semester`() {
        val course = course("Analysis", semester = "SoSe 26", lsfCode = null)
        val grades = listOf(
            grade("Analysis", semester = "WiSe 24/25", note = 1.7)
        )
        assertNull(CourseGradeMatcher.bestMatch(course, grades))
    }

    // ── Wiederholungsversuche → beste Zeile ─────────────────────────────────

    @Test
    fun `bei Wiederholung wird bestandene Zeile bevorzugt`() {
        val course = course("Statistik", lsfCode = "5390")
        val grades = listOf(
            grade("Statistik", veranstaltungsNr = "5390", note = 5.0, status = GradeStatus.FAILED, versuch = 1, rowId = 1),
            grade("Statistik", veranstaltungsNr = "5390", note = 2.3, status = GradeStatus.PASSED, versuch = 2, rowId = 2)
        )
        val match = CourseGradeMatcher.bestMatch(course, grades)
        assertEquals(2, match?.versuch)
        assertEquals("2,3", CourseGradeMatcher.effectiveGrade(course, grades).label)
    }

    @Test
    fun `ohne bestandene Zeile gewinnt der hoechste Versuch`() {
        val course = course("BIS", lsfCode = "3202")
        val grades = listOf(
            grade("BIS", veranstaltungsNr = "3202", note = 5.0, status = GradeStatus.FAILED, versuch = 1, rowId = 1),
            grade("BIS", veranstaltungsNr = "3202", note = 5.0, status = GradeStatus.FAILED, versuch = 2, rowId = 2)
        )
        // Ohne bestandene Zeile wählt pickBest den höchsten Versuch — das ist die
        // stabile Aussage (der genaue Anzeige-Text „5,0" vs. „nicht bestanden" hängt
        // von der displayLabel-Formatierung ab und wird in eigenen Tests geprüft).
        val match = CourseGradeMatcher.bestMatch(course, grades)
        assertEquals(2, match?.versuch)
        val label = CourseGradeMatcher.effectiveGrade(course, grades).label
        assertTrue("nicht bestandene Zeile liefert einen Label", label != null)
        assertTrue("Label ist nicht bestanden", label != "bestanden")
    }

    @Test
    fun `FAILED ohne konkrete Note zeigt nicht bestanden`() {
        val course = course("BIS", lsfCode = "3202")
        val grades = listOf(
            grade("BIS", veranstaltungsNr = "3202", note = null, status = GradeStatus.FAILED, versuch = 1, rowId = 1)
        )
        assertEquals("nicht bestanden", CourseGradeMatcher.effectiveGrade(course, grades).label)
    }

    // ── angemeldet zählt nicht als Note ─────────────────────────────────────

    @Test
    fun `angemeldete Pruefung liefert keine Note`() {
        val course = course("Neu angemeldet", lsfCode = "1234")
        val grades = listOf(
            grade("Neu angemeldet", veranstaltungsNr = "1234", note = null, status = GradeStatus.REGISTERED)
        )
        val eff = CourseGradeMatcher.effectiveGrade(course, grades)
        assertEquals(GradeSource.NONE, eff.source)
        assertNull(eff.label)
    }

    @Test
    fun `bestandenes Praktikum ohne Note zeigt bestanden`() {
        val course = course("Programmierpraktikum", lsfCode = "3507")
        val grades = listOf(
            grade("Programmierpraktikum", veranstaltungsNr = "3507", note = null, status = GradeStatus.PASSED)
        )
        assertEquals("bestanden", CourseGradeMatcher.effectiveGrade(course, grades).label)
    }

    // ── kein Match → null / NONE ────────────────────────────────────────────

    @Test
    fun `kein Match ergibt EffectiveGrade NONE`() {
        val course = course("Etwas ganz anderes", lsfCode = "0001")
        val grades = listOf(
            grade("Andere Veranstaltung", veranstaltungsNr = "9999", note = 1.0)
        )
        val eff = CourseGradeMatcher.effectiveGrade(course, grades)
        assertEquals(GradeSource.NONE, eff.source)
        assertNull(CourseGradeMatcher.bestMatch(course, grades))
    }

    @Test
    fun `leere Notenliste ergibt NONE`() {
        val course = course("Kurs", lsfCode = "1234")
        assertEquals(EffectiveGrade.NONE, CourseGradeMatcher.effectiveGrade(course, emptyList()))
    }

    // ── manuelle Note hat Vorrang ───────────────────────────────────────────

    @Test
    fun `manuelle Note schlaegt gematchte Notenspiegel-Note`() {
        val course = course("BIS", lsfCode = "3202", grade = "1,0")
        val grades = listOf(
            grade("BIS", veranstaltungsNr = "3202", note = 3.7, status = GradeStatus.PASSED)
        )
        val eff = CourseGradeMatcher.effectiveGrade(course, grades)
        assertEquals(GradeSource.MANUAL, eff.source)
        assertEquals("1,0", eff.label)
    }

    @Test
    fun `leere manuelle Note wird ignoriert und Notenspiegel greift`() {
        val course = course("BIS", lsfCode = "3202", grade = "   ")
        val grades = listOf(
            grade("BIS", veranstaltungsNr = "3202", note = 2.0)
        )
        val eff = CourseGradeMatcher.effectiveGrade(course, grades)
        assertEquals(GradeSource.NOTENSPIEGEL, eff.source)
        assertEquals("2,0", eff.label)
    }
}
