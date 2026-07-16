package de.transio.hiuni.core.design.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine Logik-Tests für [shouldShowStaleness] — die Sichtbarkeitsentscheidung der
 * Stale-Kennzeichnung. Bewusst Android-frei (kein Robolectric), weil die Funktion
 * absichtlich ohne Framework-Abhängigkeiten geschrieben ist.
 *
 * Die Regel: sichtbar NUR wenn offline ODER Daten älter als der Schwellwert (6h),
 * und niemals ohne je erfolgten Refresh ([lastRefreshEpoch] <= 0).
 */
class StalenessLabelTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `nie geladen liefert nie sichtbar - auch offline`() {
        assertFalse(shouldShowStaleness(lastRefreshEpoch = 0L, isOnline = false, nowEpoch = now))
        assertFalse(shouldShowStaleness(lastRefreshEpoch = 0L, isOnline = true, nowEpoch = now))
        // negativer/ungültiger Timestamp zählt ebenfalls als "nie"
        assertFalse(shouldShowStaleness(lastRefreshEpoch = -1L, isOnline = false, nowEpoch = now))
    }

    @Test
    fun `offline mit frischem Cache ist trotzdem sichtbar`() {
        // Gerade eben aktualisiert, aber offline → gecachte Daten kennzeichnen.
        val justNow = now - 1_000L
        assertTrue(shouldShowStaleness(lastRefreshEpoch = justNow, isOnline = false, nowEpoch = now))
    }

    @Test
    fun `online mit frischem Cache ist unsichtbar`() {
        val twoHoursAgo = now - 2L * 60 * 60 * 1000
        assertFalse(shouldShowStaleness(lastRefreshEpoch = twoHoursAgo, isOnline = true, nowEpoch = now))
    }

    @Test
    fun `online aber aelter als Schwellwert ist sichtbar`() {
        val sevenHoursAgo = now - 7L * 60 * 60 * 1000
        assertTrue(shouldShowStaleness(lastRefreshEpoch = sevenHoursAgo, isOnline = true, nowEpoch = now))
    }

    @Test
    fun `genau am Schwellwert ist sichtbar - Grenze inklusiv`() {
        val exactlyThreshold = now - STALENESS_THRESHOLD_MS
        assertTrue(shouldShowStaleness(lastRefreshEpoch = exactlyThreshold, isOnline = true, nowEpoch = now))
    }

    @Test
    fun `eine Millisekunde unter Schwellwert ist unsichtbar`() {
        val justUnder = now - (STALENESS_THRESHOLD_MS - 1L)
        assertFalse(shouldShowStaleness(lastRefreshEpoch = justUnder, isOnline = true, nowEpoch = now))
    }

    @Test
    fun `eigener Schwellwert wird respektiert`() {
        val oneHourAgo = now - 1L * 60 * 60 * 1000
        // Mit 30-Minuten-Schwelle ist 1h alt "stale", mit Default-6h nicht.
        assertTrue(
            shouldShowStaleness(
                lastRefreshEpoch = oneHourAgo,
                isOnline = true,
                nowEpoch = now,
                thresholdMs = 30L * 60 * 1000,
            )
        )
        assertFalse(shouldShowStaleness(lastRefreshEpoch = oneHourAgo, isOnline = true, nowEpoch = now))
    }
}
