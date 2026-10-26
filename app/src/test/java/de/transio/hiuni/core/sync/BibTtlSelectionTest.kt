package de.transio.hiuni.core.sync

import de.transio.hiuni.core.sync.PrefetchOrchestrator.Companion.TTL_BIB_FAVORITES_MS
import de.transio.hiuni.core.sync.PrefetchOrchestrator.Companion.TTL_BIB_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * TTL-Wahl für den Bib-Snapshot: kurz nur, wenn es Lieblings-Räume gibt UND die
 * Uhrzeit im Beobachtungsfenster liegt, in dem eine Frei-Meldung noch etwas nützt.
 */
class BibTtlSelectionTest {

    @Test
    fun `ohne Favoriten bleibt die lange TTL zu jeder Uhrzeit`() {
        for (hour in 0..23) {
            assertEquals(TTL_BIB_MS, PrefetchOrchestrator.bibTtlMs(false, LocalTime.of(hour, 15)))
        }
    }

    @Test
    fun `mit Favoriten tagsueber die kurze TTL`() {
        assertEquals(TTL_BIB_FAVORITES_MS, PrefetchOrchestrator.bibTtlMs(true, LocalTime.of(7, 0)))
        assertEquals(TTL_BIB_FAVORITES_MS, PrefetchOrchestrator.bibTtlMs(true, LocalTime.of(12, 30)))
        assertEquals(TTL_BIB_FAVORITES_MS, PrefetchOrchestrator.bibTtlMs(true, LocalTime.of(18, 59)))
    }

    @Test
    fun `mit Favoriten nachts und abends die lange TTL`() {
        assertEquals(TTL_BIB_MS, PrefetchOrchestrator.bibTtlMs(true, LocalTime.of(6, 59)))
        // Ab 19:00 passt vor Buchungsschluss (20:00) kein Ein-Stunden-Block mehr.
        assertEquals(TTL_BIB_MS, PrefetchOrchestrator.bibTtlMs(true, LocalTime.of(19, 0)))
        assertEquals(TTL_BIB_MS, PrefetchOrchestrator.bibTtlMs(true, LocalTime.of(23, 30)))
    }

    @Test
    fun `kurze TTL trifft den zweiten 15-Minuten-Tickle sicher`() {
        // Unter 30 min, damit der zweite Tickle nach einem Refresh greift, aber
        // über 15 min, damit nicht jeder Tickle die Bib abruft.
        assertTrue(TTL_BIB_FAVORITES_MS < 30L * 60 * 1000)
        assertTrue(TTL_BIB_FAVORITES_MS > 15L * 60 * 1000)
    }
}
