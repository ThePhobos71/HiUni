package de.transio.hiuni.core.update

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UpdateCheckServiceTest {

    private val hour = 60L * 60 * 1000
    private val nowMs = 500L * hour
    private val clock = Clock.fixed(Instant.ofEpochMilli(nowMs), ZoneOffset.UTC)

    private val source = FakeReleaseSource(result = testRelease())
    private val prefs = FakeUpdatePreferences()
    private val notifier = FakeUpdateNotifier()

    private fun service(build: AppBuildInfo = testBuild()) =
        UpdateCheckService(source, prefs, notifier, build, clock)

    @Test
    fun `Debug-Build prueft nie, weder im Hintergrund noch manuell`() = runTest {
        val s = service(testBuild(isDebug = true))
        assertEquals(BackgroundCheckResult.NOT_ALLOWED, s.backgroundCheck())
        assertEquals(CheckOutcome.Disabled, s.checkNow())
        assertEquals(0, source.calls)
    }

    @Test
    fun `Hintergrund-Check meldet neue Version einmal und merkt sie sich`() = runTest {
        val s = service()
        assertEquals(BackgroundCheckResult.NOTIFIED, s.backgroundCheck())
        assertEquals(listOf(testRelease()), notifier.shown)
        assertEquals(10402, prefs.snapshot().lastNotifiedVersionCode)
        assertEquals(nowMs, prefs.snapshot().lastCheckEpochMs)
    }

    @Test
    fun `Hintergrund-Check ist gedrosselt`() = runTest {
        prefs.setLastCheckEpochMs(nowMs - hour)
        assertEquals(BackgroundCheckResult.NOT_ALLOWED, service().backgroundCheck())
        assertEquals(0, source.calls)
    }

    @Test
    fun `ausgeschaltete Auto-Pruefung verhindert Hintergrund-Check`() = runTest {
        prefs.setAutoCheck(false)
        assertEquals(BackgroundCheckResult.NOT_ALLOWED, service().backgroundCheck())
        assertEquals(0, source.calls)
    }

    @Test
    fun `bereits gemeldete Version wird nicht erneut gemeldet`() = runTest {
        prefs.setLastNotifiedVersionCode(10402)
        assertEquals(BackgroundCheckResult.UPDATE_KNOWN, service().backgroundCheck())
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `uebersprungene Version wird nicht gemeldet`() = runTest {
        prefs.setSkippedVersionCode(10402)
        assertEquals(BackgroundCheckResult.UPDATE_KNOWN, service().backgroundCheck())
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `aktuelle Version meldet nichts`() = runTest {
        assertEquals(BackgroundCheckResult.UP_TO_DATE, service(testBuild(versionCode = 10402)).backgroundCheck())
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `kein Release gilt als aktuell`() = runTest {
        source.result = null
        assertEquals(BackgroundCheckResult.UP_TO_DATE, service().backgroundCheck())
        assertEquals(CheckOutcome.UpToDate, service().checkNow())
    }

    @Test
    fun `Fehler im Hintergrund wird geschluckt und nicht als Pruefung gezaehlt`() = runTest {
        source.error = UpdateException("weg")
        assertEquals(BackgroundCheckResult.FAILED, service().backgroundCheck())
        assertEquals(0L, prefs.snapshot().lastCheckEpochMs)
    }

    @Test
    fun `manueller Check ignoriert Drossel und zeigt auch uebersprungene Version`() = runTest {
        prefs.setLastCheckEpochMs(nowMs - hour)
        prefs.setSkippedVersionCode(10402)
        assertEquals(CheckOutcome.Available(testRelease(), skipped = true), service().checkNow())
        assertEquals(nowMs, prefs.snapshot().lastCheckEpochMs)
    }

    @Test(expected = UpdateException::class)
    fun `manueller Check reicht Fehler weiter`() = runTest {
        source.error = UpdateException("weg")
        service().checkNow()
    }

    @Test
    fun `Ueberspringen merkt die Version und entfernt die Mitteilung`() = runTest {
        service().skip(testRelease())
        assertEquals(10402, prefs.snapshot().skippedVersionCode)
        assertEquals(1, notifier.cancels)
    }
}
