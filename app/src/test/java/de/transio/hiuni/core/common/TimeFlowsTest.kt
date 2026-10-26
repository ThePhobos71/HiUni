package de.transio.hiuni.core.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TimeFlowsTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 10, 27)

    private fun TestScope.clockAt(dateTime: LocalDateTime, zone: ZoneId = berlin) =
        VirtualClock(testScheduler, dateTime.atZone(zone).toInstant(), zone)

    private fun <T> TestScope.collectInto(flow: Flow<T>): List<T> {
        val values = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(values) }
        return values
    }

    private fun TestScope.advance(millis: Long) {
        testScheduler.advanceTimeBy(millis)
        testScheduler.runCurrent()
    }

    @Test
    fun `currentDateFlow liefert heute sofort und den Folgetag direkt nach Mitternacht`() = runTest {
        val clock = clockAt(day.atTime(23, 59, 30))
        val dates = collectInto(currentDateFlow(clock))

        assertEquals(listOf(day), dates)

        advance(29_000) // 23:59:59
        assertEquals(listOf(day), dates)

        advance(1_000) // 00:00:00
        assertEquals(listOf(day, day.plusDays(1)), dates)
    }

    @Test
    fun `currentDateFlow meldet waehrend des Tages kein Datum doppelt`() = runTest {
        val clock = clockAt(day.atTime(8, 0))
        val dates = collectInto(currentDateFlow(clock))

        advance(10L * 60 * 60 * 1000) // 18:00, mehrere Nachprüfungen dazwischen

        assertEquals(listOf(day), dates)
    }

    @Test
    fun `currentDateFlow beruecksichtigt den 25-Stunden-Tag bei Ende der Sommerzeit`() = runTest {
        // 25.10.2026: Uhr wird um 03:00 auf 02:00 zurückgestellt, der Tag hat 25 Stunden.
        val dstEnd = LocalDate.of(2026, 10, 25)
        val clock = clockAt(dstEnd.atTime(0, 0, 30))
        val dates = collectInto(currentDateFlow(clock))

        advance(24L * 60 * 60 * 1000) // 24h später ist es lokal erst 23:00:30
        assertEquals(listOf(dstEnd), dates)

        advance(60L * 60 * 1000) // 00:00:30 am 26.10.
        assertEquals(listOf(dstEnd, dstEnd.plusDays(1)), dates)
    }

    @Test
    fun `currentDateFlow folgt einem Zeitzonenwechsel spaetestens nach dem Pruefintervall`() = runTest {
        val clock = clockAt(day.atTime(20, 0))
        val dates = collectInto(currentDateFlow(clock))

        // Reise nach Tokio: dort ist es bereits 04:00 am Folgetag.
        clock.zoneId = ZoneId.of("Asia/Tokyo")
        advance(15L * 60 * 1000)

        assertEquals(listOf(day, day.plusDays(1)), dates)
    }

    @Test
    fun `minuteTickerFlow tickt sofort und dann zu jeder vollen Minute`() = runTest {
        val clock = clockAt(day.atTime(9, 0, 20))
        val ticks = collectInto(minuteTickerFlow(clock))

        assertEquals(listOf(instant(9, 0, 20)), ticks)

        advance(39_000) // 09:00:59
        assertEquals(1, ticks.size)

        advance(1_000) // 09:01:00
        advance(60_000) // 09:02:00
        assertEquals(listOf(instant(9, 0, 20), instant(9, 1), instant(9, 2)), ticks)
    }

    private fun instant(hour: Int, minute: Int, second: Int = 0): Instant =
        day.atTime(hour, minute, second).atZone(berlin).toInstant()
}
