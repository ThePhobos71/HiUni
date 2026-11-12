package de.transio.hiuni.feature.settings

import de.transio.hiuni.core.update.ApkCheck
import de.transio.hiuni.core.update.AppBuildInfo
import de.transio.hiuni.core.update.FakeApkDownloader
import de.transio.hiuni.core.update.FakeApkVerifier
import de.transio.hiuni.core.update.FakeReleaseSource
import de.transio.hiuni.core.update.FakeUpdateInstaller
import de.transio.hiuni.core.update.FakeUpdateNotifier
import de.transio.hiuni.core.update.FakeUpdatePreferences
import de.transio.hiuni.core.update.InstallOutcome
import de.transio.hiuni.core.update.UpdateCheckService
import de.transio.hiuni.core.update.UpdateException
import de.transio.hiuni.core.update.UpdateInstallEvents
import de.transio.hiuni.core.update.testBuild
import de.transio.hiuni.core.update.testRelease
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {

    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000_000_000L), ZoneOffset.UTC)
    private val source = FakeReleaseSource(result = testRelease())
    private val prefs = FakeUpdatePreferences()
    private val notifier = FakeUpdateNotifier()
    private val downloader = FakeApkDownloader()
    private val verifier = FakeApkVerifier()
    private val installer = FakeUpdateInstaller()
    private val events = UpdateInstallEvents()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(build: AppBuildInfo = testBuild()) = AppUpdateViewModel(
        service = UpdateCheckService(source, prefs, notifier, build, clock),
        prefs = prefs,
        downloader = downloader,
        verifier = verifier,
        installer = installer,
        installEvents = events,
        build = build
    )

    @Test
    fun `Debug-Build zeigt Hinweis und prueft nie`() = runTest {
        val vm = vm(testBuild(isDebug = true))
        vm.checkNow()
        assertEquals(UpdatePhase.DebugBuild, vm.state.value.phase)
        assertEquals(0, source.calls)
    }

    @Test
    fun `Oeffnen prueft sofort und zeigt neue Version`() = runTest {
        val vm = vm()
        assertEquals(UpdatePhase.Available(testRelease(), skipped = false), vm.state.value.phase)
        assertEquals("1.4.1", vm.state.value.currentVersionName)
    }

    @Test
    fun `aktuelle Version zeigt aktuell`() = runTest {
        val vm = vm(testBuild(versionCode = 10402))
        assertEquals(UpdatePhase.UpToDate, vm.state.value.phase)
    }

    @Test
    fun `Fehler beim Pruefen wird angezeigt`() = runTest {
        source.error = UpdateException("GitHub nicht erreichbar")
        val vm = vm()
        assertEquals(UpdatePhase.Failed("GitHub nicht erreichbar", release = null), vm.state.value.phase)
    }

    @Test
    fun `Ueberspringen markiert die Version`() = runTest {
        val vm = vm()
        vm.skipVersion()
        assertEquals(UpdatePhase.Available(testRelease(), skipped = true), vm.state.value.phase)
        assertEquals(10402, prefs.snapshot().skippedVersionCode)
    }

    @Test
    fun `Download zeigt Fortschritt und endet bereit zur Installation`() = runTest {
        val gate = CompletableDeferred<Unit>()
        downloader.progressSteps = listOf(10, 42)
        downloader.gate = gate
        val vm = vm()
        vm.download()
        assertEquals(UpdatePhase.Downloading(testRelease(), percent = 42), vm.state.value.phase)
        gate.complete(Unit)
        assertEquals(UpdatePhase.ReadyToInstall(testRelease()), vm.state.value.phase)
    }

    @Test
    fun `fremde Signatur bricht ab und loescht den Download`() = runTest {
        verifier.result = ApkCheck.SignatureMismatch
        val vm = vm()
        vm.download()
        assertEquals(
            UpdatePhase.Failed("Signatur passt nicht, Update abgebrochen", testRelease()),
            vm.state.value.phase
        )
        assertEquals(1, downloader.clears)
    }

    @Test
    fun `Download-Fehler wird angezeigt`() = runTest {
        downloader.error = UpdateException("Download unvollständig")
        val vm = vm()
        vm.download()
        assertEquals(UpdatePhase.Failed("Download unvollständig", testRelease()), vm.state.value.phase)
    }

    @Test
    fun `Release ohne APK kann nicht geladen werden`() = runTest {
        source.result = testRelease(withApk = false)
        val vm = vm()
        vm.download()
        val phase = vm.state.value.phase
        assertTrue(phase is UpdatePhase.Failed)
    }

    @Test
    fun `Installieren ohne Erlaubnis fuehrt erst zur Freigabe`() = runTest {
        installer.canRequest = false
        val vm = vm()
        vm.download()
        vm.install()
        assertEquals(UpdatePhase.NeedsInstallPermission(testRelease()), vm.state.value.phase)
        assertTrue(installer.installed.isEmpty())

        vm.openInstallPermissionSettings()
        assertEquals(1, installer.settingsOpened)

        // Zurueck aus den Systemeinstellungen ohne Freigabe: bleibt stehen.
        vm.onResume()
        assertEquals(UpdatePhase.NeedsInstallPermission(testRelease()), vm.state.value.phase)

        // Mit Freigabe: Installation startet automatisch.
        installer.canRequest = true
        vm.onResume()
        assertEquals(UpdatePhase.Installing(testRelease()), vm.state.value.phase)
        assertEquals(listOf(downloader.file), installer.installed)
    }

    @Test
    fun `Installieren mit Erlaubnis startet die Session`() = runTest {
        val vm = vm()
        vm.download()
        vm.install()
        assertEquals(UpdatePhase.Installing(testRelease()), vm.state.value.phase)
        assertEquals(1, installer.installed.size)
    }

    @Test
    fun `Installieren ohne Download tut nichts`() = runTest {
        val vm = vm()
        vm.install()
        assertTrue(installer.installed.isEmpty())
        assertEquals(UpdatePhase.Available(testRelease(), skipped = false), vm.state.value.phase)
    }

    @Test
    fun `abgebrochene Installation fuehrt zurueck zu bereit`() = runTest {
        val vm = vm()
        vm.download()
        vm.install()
        events.publish(InstallOutcome.Aborted)
        assertEquals(UpdatePhase.ReadyToInstall(testRelease()), vm.state.value.phase)
    }

    @Test
    fun `fehlgeschlagene Installation zeigt die Meldung`() = runTest {
        val vm = vm()
        vm.download()
        vm.install()
        events.publish(InstallOutcome.Failure("Zu wenig Speicher"))
        assertEquals(UpdatePhase.Failed("Zu wenig Speicher", testRelease()), vm.state.value.phase)
    }

    @Test
    fun `Auto-Pruefung laesst sich umschalten`() = runTest {
        val vm = vm()
        assertTrue(vm.state.value.autoCheck)
        vm.setAutoCheck(false)
        assertFalse(prefs.snapshot().autoCheck)
        assertFalse(vm.state.value.autoCheck)
    }

    @Test
    fun `Changelog wird fuer die Karte aufbereitet`() {
        val md = "## Neu\n- **Self-Update**\n* `Mensa`\n\n\n\nText"
        assertEquals("Neu\n• Self-Update\n• Mensa\n\nText", de.transio.hiuni.feature.settings.ui.formatChangelog(md))
    }
}
