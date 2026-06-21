package de.transio.hiuni.feature.mensa.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnnouncementCoverageTest {

    private fun ann(text: String, date: LocalDate = LocalDate.of(2026, 5, 25)) = Announcement(
        date = date,
        text = text,
        time = AnnouncementTime.EVENING,
        lane = null
    )

    @Test
    fun `full date range with year covers every day in the window inclusive`() {
        val a = ann("Vom 25.05.2026 bis 29.05.2026 bleibt die Abendmensa geschlossen.")
        assertTrue(a.covers(LocalDate.of(2026, 5, 25)))
        assertTrue(a.covers(LocalDate.of(2026, 5, 27)))
        assertTrue(a.covers(LocalDate.of(2026, 5, 29)))
        assertFalse(a.covers(LocalDate.of(2026, 5, 24)))
        assertFalse(a.covers(LocalDate.of(2026, 5, 30)))
    }

    @Test
    fun `partial date range without year inherits anchor year`() {
        val a = ann("vom 25.05. bis 29.05. ist geschlossen")
        assertTrue(a.covers(LocalDate.of(2026, 5, 25)))
        assertTrue(a.covers(LocalDate.of(2026, 5, 29)))
        assertFalse(a.covers(LocalDate.of(2026, 5, 30)))
    }

    @Test
    fun `single date covers only that day`() {
        val a = ann("Am 25.05.2026 bleibt die Mensa geschlossen.")
        assertTrue(a.covers(LocalDate.of(2026, 5, 25)))
        assertFalse(a.covers(LocalDate.of(2026, 5, 26)))
    }

    @Test
    fun `falls back to anchor date when text has no date`() {
        val a = ann("Heute Sonderaktion an Theke 2")
        assertTrue(a.covers(LocalDate.of(2026, 5, 25)))
        assertFalse(a.covers(LocalDate.of(2026, 5, 26)))
    }

    @Test
    fun `dash variants are accepted`() {
        val a = ann("25.05.2026 – 29.05.2026 Wartung")
        assertTrue(a.covers(LocalDate.of(2026, 5, 27)))
    }
}
