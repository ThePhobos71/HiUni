package de.transio.hiuni.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    private val hour = 60L * 60 * 1000
    private val now = 1_000L * hour

    private fun release(code: AppVersion = AppVersion(1, 4, 2), withApk: Boolean = true) = ReleaseInfo(
        tag = "v${code.name}",
        version = code,
        title = "HiUni ${code.name}",
        changelog = "",
        htmlUrl = "u",
        apk = if (withApk) ReleaseAsset("HiUni-${code.name}.apk", "d", 10) else null
    )

    @Test
    fun `Debug-Build prueft nie im Hintergrund`() {
        assertFalse(UpdatePolicy.mayCheckInBackground(isDebug = true, autoCheck = true, lastCheckEpochMs = 0, nowEpochMs = now))
    }

    @Test
    fun `ausgeschaltete Auto-Pruefung verhindert Hintergrund-Check`() {
        assertFalse(UpdatePolicy.mayCheckInBackground(isDebug = false, autoCheck = false, lastCheckEpochMs = 0, nowEpochMs = now))
    }

    @Test
    fun `Hintergrund-Check hoechstens alle sechs Stunden`() {
        assertFalse(UpdatePolicy.mayCheckInBackground(false, true, now - 5 * hour, now))
        assertTrue(UpdatePolicy.mayCheckInBackground(false, true, now - 6 * hour, now))
        assertTrue(UpdatePolicy.mayCheckInBackground(false, true, 0, now))
    }

    @Test
    fun `Zeitstempel in der Zukunft blockiert nicht dauerhaft`() {
        // Uhr wurde zurueckgestellt: sonst gaebe es nie wieder einen Check.
        assertTrue(UpdatePolicy.mayCheckInBackground(false, true, now + 10 * hour, now))
    }

    @Test
    fun `nur hoehere Version gilt als neu`() {
        assertTrue(UpdatePolicy.isNewer(release(AppVersion(1, 4, 2)), installedVersionCode = 10401))
        assertFalse(UpdatePolicy.isNewer(release(AppVersion(1, 4, 2)), installedVersionCode = 10402))
        assertFalse(UpdatePolicy.isNewer(release(AppVersion(1, 4, 2)), installedVersionCode = 10500))
    }

    @Test
    fun `Benachrichtigung nur fuer neue, nicht uebersprungene, noch nicht gemeldete Version mit APK`() {
        val r = release(AppVersion(1, 4, 2))
        assertTrue(UpdatePolicy.shouldNotify(r, 10401, skippedVersionCode = null, lastNotifiedVersionCode = null))
        assertFalse(UpdatePolicy.shouldNotify(r, 10402, null, null))
        assertFalse(UpdatePolicy.shouldNotify(r, 10401, skippedVersionCode = 10402, lastNotifiedVersionCode = null))
        assertFalse(UpdatePolicy.shouldNotify(r, 10401, null, lastNotifiedVersionCode = 10402))
        assertFalse(UpdatePolicy.shouldNotify(release(withApk = false), 10401, null, null))
    }

    @Test
    fun `uebersprungene aeltere Version blockiert neuere nicht`() {
        val r = release(AppVersion(1, 4, 3))
        assertTrue(UpdatePolicy.shouldNotify(r, 10401, skippedVersionCode = 10402, lastNotifiedVersionCode = 10402))
    }
}
