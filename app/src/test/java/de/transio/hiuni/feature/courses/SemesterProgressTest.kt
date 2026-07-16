package de.transio.hiuni.feature.courses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SemesterProgressTest {

    @Test
    fun `parseSemesterRange erkennt Sommersemester`() {
        val range = parseSemesterRange("Sommer 2026")
        assertEquals(LocalDate.of(2026, 4, 1), range?.start)
        assertEquals(LocalDate.of(2026, 9, 30), range?.end)
    }

    @Test
    fun `parseSemesterRange erkennt Wintersemester ueber Jahreswechsel`() {
        val range = parseSemesterRange("Winter 2025/26")
        assertEquals(LocalDate.of(2025, 10, 1), range?.start)
        assertEquals(LocalDate.of(2026, 3, 31), range?.end)
    }

    @Test
    fun `parseSemesterRange gibt null bei unbekanntem Format`() {
        assertNull(parseSemesterRange(""))
        assertNull(parseSemesterRange("Sonstiges 2026"))
        assertNull(parseSemesterRange("WiSe 2025"))
    }

    @Test
    fun `semesterProgress ist 0 am Semesterstart`() {
        val progress = semesterProgress("Sommer 2026", today = LocalDate.of(2026, 4, 1))
        assertEquals(0f, progress)
    }

    @Test
    fun `semesterProgress ist 1 am Semesterende`() {
        val progress = semesterProgress("Sommer 2026", today = LocalDate.of(2026, 9, 30))
        assertEquals(1f, progress)
    }

    @Test
    fun `semesterProgress klemmt auf 0 vor Semesterbeginn`() {
        val progress = semesterProgress("Sommer 2026", today = LocalDate.of(2026, 1, 1))
        assertEquals(0f, progress)
    }

    @Test
    fun `semesterProgress klemmt auf 1 nach Semesterende`() {
        val progress = semesterProgress("Sommer 2026", today = LocalDate.of(2027, 1, 1))
        assertEquals(1f, progress)
    }

    @Test
    fun `semesterProgress liegt in der Mitte bei ca 50 Prozent`() {
        // Sommer 2026: 01.04.-30.09. -> Mitte ca. 1./2. Juli.
        val progress = semesterProgress("Sommer 2026", today = LocalDate.of(2026, 7, 1))
        assertTrue("expected ~0.5 but was $progress", progress != null && progress in 0.45f..0.55f)
    }

    @Test
    fun `semesterProgress ueber Winter-Jahreswechsel funktioniert`() {
        val progress = semesterProgress("Winter 2025/26", today = LocalDate.of(2026, 1, 1))
        assertTrue("expected between 0 and 1 but was $progress", progress != null && progress in 0f..1f)
        assertTrue(progress!! > 0.4f && progress < 0.6f)
    }

    @Test
    fun `semesterProgress gibt null bei nicht parsebarem Semester`() {
        assertNull(semesterProgress("", today = LocalDate.of(2026, 1, 1)))
        assertNull(semesterProgress("Unbekannt", today = LocalDate.of(2026, 1, 1)))
    }
}
