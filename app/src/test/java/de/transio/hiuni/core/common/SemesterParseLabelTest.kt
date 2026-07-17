package de.transio.hiuni.core.common

import de.transio.hiuni.core.common.Semester.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests für die konsolidierte, tolerante Label-Parser-Logik
 * [Semester.parseLabel] und das darauf aufbauende [Semester.earliestOf].
 *
 * Deckt die im Projekt real vorkommenden Schreibweisen ab:
 *  - Noten-/LSF-Kurzform: „WiSe 24/25", „WS 2024/25", „SoSe 25", „SS 2025"
 *  - Meine-Veranstaltungen-Langform: „Sommer 2026", „Winter 2025/26"
 */
class SemesterParseLabelTest {

    @Test
    fun `parseLabel versteht WiSe Kurzform mit zweistelligem Jahresbereich`() {
        assertEquals(Semester(Period.WS, 2024), Semester.parseLabel("WiSe 24/25"))
    }

    @Test
    fun `parseLabel versteht WS Langform mit vierstelligem Startjahr`() {
        assertEquals(Semester(Period.WS, 2024), Semester.parseLabel("WS 2024/25"))
    }

    @Test
    fun `parseLabel versteht SoSe Kurzform`() {
        assertEquals(Semester(Period.SS, 2025), Semester.parseLabel("SoSe 25"))
    }

    @Test
    fun `parseLabel versteht SS Langform`() {
        assertEquals(Semester(Period.SS, 2025), Semester.parseLabel("SS 2025"))
    }

    @Test
    fun `parseLabel versteht Sommer-Langform der Kurse`() {
        assertEquals(Semester(Period.SS, 2026), Semester.parseLabel("Sommer 2026"))
    }

    @Test
    fun `parseLabel versteht Winter-Langform der Kurse`() {
        assertEquals(Semester(Period.WS, 2025), Semester.parseLabel("Winter 2025/26"))
    }

    @Test
    fun `parseLabel ist tolerant gegenueber Whitespace und Gross-Klein`() {
        assertEquals(Semester(Period.WS, 2023), Semester.parseLabel("  wise 23/24 "))
        assertEquals(Semester(Period.SS, 2026), Semester.parseLabel("SOMMER 2026"))
    }

    @Test
    fun `parseLabel gibt null bei Muell`() {
        assertNull(Semester.parseLabel(""))
        assertNull(Semester.parseLabel("   "))
        assertNull(Semester.parseLabel("Frühjahr"))
        assertNull(Semester.parseLabel("SoSe"))           // kein Jahr
        assertNull(Semester.parseLabel("Semester 3"))     // kein SS/WS-Präfix passt …
        assertNull(Semester.parseLabel("2025"))           // kein Period-Präfix
    }

    @Test
    fun `WiSe hat groesseren ordinal als SoSe des gleichen Startjahres`() {
        val sose = Semester.parseLabel("SoSe 24")!!
        val wise = Semester.parseLabel("WiSe 24/25")!!
        // Studienjahr: SoSe (früher) -> WiSe (später).
        assert(wise.ordinal > sose.ordinal)
    }

    @Test
    fun `earliestOf nimmt das Semester mit kleinstem ordinal`() {
        val labels = listOf("SoSe 26", "WiSe 23/24", "Sommer 2026", "WiSe 25/26")
        assertEquals(Semester(Period.WS, 2023), Semester.earliestOf(labels))
    }

    @Test
    fun `earliestOf ignoriert unparsbare Labels`() {
        val labels = listOf("Müll", "", "SoSe 25", "kaputt")
        assertEquals(Semester(Period.SS, 2025), Semester.earliestOf(labels))
    }

    @Test
    fun `earliestOf gibt null wenn nichts parsebar`() {
        assertNull(Semester.earliestOf(listOf("", "Müll")))
        assertNull(Semester.earliestOf(emptyList()))
    }
}
