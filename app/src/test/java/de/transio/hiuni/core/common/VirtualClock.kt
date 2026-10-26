package de.transio.hiuni.core.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Test-Uhr, die an die virtuelle Zeit des [TestCoroutineScheduler] gekoppelt ist:
 * `advanceTimeBy(60_000)` schiebt also sowohl die `delay`s als auch `instant()` um
 * eine Minute weiter. Die Zone ist veränderbar, um Zeitzonenwechsel zu simulieren.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VirtualClock(
    private val scheduler: TestCoroutineScheduler,
    private val start: Instant,
    var zoneId: ZoneId
) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = VirtualClock(scheduler, start, zone)
    override fun instant(): Instant = start.plusMillis(scheduler.currentTime)
}
