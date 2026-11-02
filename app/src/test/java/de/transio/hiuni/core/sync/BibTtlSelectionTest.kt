package de.transio.hiuni.core.sync

import de.transio.hiuni.core.sync.PrefetchOrchestrator.Companion.TTL_BIB_FAVORITES_MS
import de.transio.hiuni.core.sync.PrefetchOrchestrator.Companion.TTL_BIB_MS
import de.transio.hiuni.feature.bib.data.BibAlarmMode
import de.transio.hiuni.feature.bib.data.BibRoomAlarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * TTL-Wahl für den Bib-Snapshot: kurz nur, wenn es Lieblings-Räume gibt, der
 * Raum-Alarm heute aktiv ist UND die Uhrzeit im eingestellten Zeitfenster liegt.
 */
class BibTtlSelectionTest {

    private val today = LocalDate.of(2026, 10, 26)
    private val always = BibRoomAlarm(mode = BibAlarmMode.ALWAYS)

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = today) =
        LocalDateTime.of(date, LocalTime.of(hour, minute))

    @Test
    fun `ohne Favoriten bleibt die lange TTL zu jeder Uhrzeit`() {
        for (hour in 0..23) {
            assertEquals(TTL_BIB_MS, PrefetchOrchestrator.bibTtlMs(false, always, at(hour, 15)))
        }
    }

    @Test
    fun `mit Favoriten und aktivem Alarm im Standardfenster die kurze TTL`() {
        assertEquals(TTL_BIB_FAVORITES_MS, PrefetchOrchestrator.bibTtlMs(true, always, at(8, 0)))
        assertEquals(TTL_BIB_FAVORITES_MS, PrefetchOrchestrator.bibTtlMs(true, always, at(12, 30)))
        assertEquals(TTL_BIB_FAVORITES_MS, PrefetchOrchestrator.bibTtlMs(true, always, at(19, 59)))
    }

    @Test
    fun `ausserhalb des Fensters die lange TTL`() {
        assertEquals(TTL_BIB_MS, PrefetchOrchestrator.bibTtlMs(true, always, at(7, 59)))
        assertEquals(TTL_BIB_MS, PrefetchOrchestrator.bibTtlMs(true, always, at(20, 0)))
        assertEquals(TTL_BIB_MS, PrefetchOrchestrator.bibTtlMs(true, always, at(23, 30)))
    }

    @Test
    fun `eigenes Zeitfenster bestimmt die kurze TTL`() {
        val afternoon = always.copy(fromHour = 14, toHour = 18)

        assertEquals(TTL_BIB_MS, PrefetchOrchestrator.bibTtlMs(true, afternoon, at(13, 59)))
        assertEquals(TTL_BIB_FAVORITES_MS, PrefetchOrchestrator.bibTtlMs(true, afternoon, at(14, 0)))
        assertEquals(TTL_BIB_FAVORITES_MS, PrefetchOrchestrator.bibTtlMs(true, afternoon, at(17, 59)))
        assertEquals(TTL_BIB_MS, PrefetchOrchestrator.bibTtlMs(true, afternoon, at(18, 0)))
    }

    @Test
    fun `Alarm aus haelt die lange TTL auch mit Favoriten`() {
        val off = BibRoomAlarm(mode = BibAlarmMode.OFF)
        assertEquals(TTL_BIB_MS, PrefetchOrchestrator.bibTtlMs(true, off, at(12, 0)))
    }

    @Test
    fun `Nur heute gilt nur am Aktivierungstag`() {
        val todayOnly = BibRoomAlarm(mode = BibAlarmMode.TODAY_ONLY, activatedOn = today)

        assertEquals(TTL_BIB_FAVORITES_MS, PrefetchOrchestrator.bibTtlMs(true, todayOnly, at(12, 0)))
        assertEquals(TTL_BIB_MS, PrefetchOrchestrator.bibTtlMs(true, todayOnly, at(12, 0, today.plusDays(1))))
    }

    @Test
    fun `kurze TTL trifft den zweiten 15-Minuten-Tickle sicher`() {
        // Unter 30 min, damit der zweite Tickle nach einem Refresh greift, aber
        // über 15 min, damit nicht jeder Tickle die Bib abruft.
        assertTrue(TTL_BIB_FAVORITES_MS < 30L * 60 * 1000)
        assertTrue(TTL_BIB_FAVORITES_MS > 15L * 60 * 1000)
    }
}
