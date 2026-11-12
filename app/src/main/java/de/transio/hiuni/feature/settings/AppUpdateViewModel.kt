package de.transio.hiuni.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.update.ApkCheck
import de.transio.hiuni.core.update.ApkDownloader
import de.transio.hiuni.core.update.ApkVerification
import de.transio.hiuni.core.update.ApkVerifier
import de.transio.hiuni.core.update.AppBuildInfo
import de.transio.hiuni.core.update.CheckOutcome
import de.transio.hiuni.core.update.InstallOutcome
import de.transio.hiuni.core.update.ReleaseInfo
import de.transio.hiuni.core.update.UpdateCheckService
import de.transio.hiuni.core.update.UpdateException
import de.transio.hiuni.core.update.UpdateInstallEvents
import de.transio.hiuni.core.update.UpdateInstaller
import de.transio.hiuni.core.update.UpdatePreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Zustand der App-Updates-Karte. */
sealed interface UpdatePhase {
    /** Debug-Build: kein Check, nur Hinweis. */
    data object DebugBuild : UpdatePhase
    data object Idle : UpdatePhase
    data object Checking : UpdatePhase
    data object UpToDate : UpdatePhase
    data class Available(val release: ReleaseInfo, val skipped: Boolean) : UpdatePhase
    data class Downloading(val release: ReleaseInfo, val percent: Int) : UpdatePhase
    data class ReadyToInstall(val release: ReleaseInfo) : UpdatePhase
    /** HiUni darf noch keine Apps installieren, erst Systemeinstellung öffnen. */
    data class NeedsInstallPermission(val release: ReleaseInfo) : UpdatePhase
    /** Session committet, Android zeigt den Bestätigungsdialog. */
    data class Installing(val release: ReleaseInfo) : UpdatePhase
    data class Failed(val message: String, val release: ReleaseInfo?) : UpdatePhase
}

data class AppUpdateUiState(
    val currentVersionName: String,
    val autoCheck: Boolean = true,
    val phase: UpdatePhase = UpdatePhase.Idle
)

/**
 * Zustandsmaschine des Update-Screens: prüfen, laden (mit Fortschritt), APK
 * prüfen (Größe, Paket, Signatur, Version), Installations-Erlaubnis einholen,
 * PackageInstaller-Session starten. Der Download hängt am viewModelScope; wer den
 * Screen verlässt, bricht ihn ab.
 */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val service: UpdateCheckService,
    private val prefs: UpdatePreferences,
    private val downloader: ApkDownloader,
    private val verifier: ApkVerifier,
    private val installer: UpdateInstaller,
    private val installEvents: UpdateInstallEvents,
    build: AppBuildInfo
) : ViewModel() {

    private val isDebug = build.isDebug
    private val phase = MutableStateFlow<UpdatePhase>(if (isDebug) UpdatePhase.DebugBuild else UpdatePhase.Idle)
    private var downloadedFile: File? = null
    private var job: Job? = null

    val state: StateFlow<AppUpdateUiState> = combine(phase, prefs.autoCheck) { p, auto ->
        AppUpdateUiState(currentVersionName = build.versionName, autoCheck = auto, phase = p)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppUpdateUiState(currentVersionName = build.versionName, phase = phase.value)
    )

    init {
        viewModelScope.launch {
            installEvents.outcomes.collect(::onInstallOutcome)
        }
        // Beim Öffnen gleich prüfen: dank ETag meist eine kostenlose 304-Antwort.
        checkNow()
    }

    fun checkNow() {
        if (isDebug || job?.isActive == true) return
        job = viewModelScope.launch {
            phase.value = UpdatePhase.Checking
            phase.value = try {
                when (val outcome = service.checkNow()) {
                    CheckOutcome.Disabled -> UpdatePhase.DebugBuild
                    CheckOutcome.UpToDate -> UpdatePhase.UpToDate
                    is CheckOutcome.Available -> UpdatePhase.Available(outcome.release, outcome.skipped)
                }
            } catch (e: UpdateException) {
                UpdatePhase.Failed(e.message ?: "Prüfung fehlgeschlagen", release = null)
            }
        }
    }

    fun setAutoCheck(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoCheck(enabled) }
    }

    fun skipVersion() {
        val current = phase.value as? UpdatePhase.Available ?: return
        viewModelScope.launch {
            service.skip(current.release)
            phase.value = current.copy(skipped = true)
        }
    }

    fun download() {
        val release = releaseOf(phase.value) ?: return
        if (job?.isActive == true) return
        val asset = release.apk
        if (asset == null) {
            phase.value = UpdatePhase.Failed("Im Release fehlt die APK-Datei", release)
            return
        }
        job = viewModelScope.launch {
            phase.value = UpdatePhase.Downloading(release, 0)
            try {
                val file = downloader.download(asset) { percent ->
                    phase.value = UpdatePhase.Downloading(release, percent)
                }
                val check = verifier.verify(file, release)
                if (check != ApkCheck.Ok) {
                    downloader.clearDownloads()
                    downloadedFile = null
                    phase.value = UpdatePhase.Failed(ApkVerification.message(check), release)
                    return@launch
                }
                downloadedFile = file
                phase.value = UpdatePhase.ReadyToInstall(release)
            } catch (e: UpdateException) {
                phase.value = UpdatePhase.Failed(e.message ?: "Download fehlgeschlagen", release)
            }
        }
    }

    fun install() {
        val release = when (val p = phase.value) {
            is UpdatePhase.ReadyToInstall -> p.release
            is UpdatePhase.NeedsInstallPermission -> p.release
            else -> return
        }
        val file = downloadedFile ?: return
        if (!installer.canRequestInstalls()) {
            phase.value = UpdatePhase.NeedsInstallPermission(release)
            return
        }
        viewModelScope.launch {
            phase.value = UpdatePhase.Installing(release)
            try {
                installer.install(file)
            } catch (e: UpdateException) {
                phase.value = UpdatePhase.Failed(e.message ?: "Installation fehlgeschlagen", release)
            }
        }
    }

    fun openInstallPermissionSettings() {
        installer.openInstallPermissionSettings()
    }

    /** Rückkehr aus den Systemeinstellungen: mit Erlaubnis geht es direkt weiter. */
    fun onResume() {
        if (phase.value is UpdatePhase.NeedsInstallPermission && installer.canRequestInstalls()) {
            install()
        }
    }

    private fun onInstallOutcome(outcome: InstallOutcome) {
        val release = releaseOf(phase.value) ?: return
        when (outcome) {
            InstallOutcome.PendingUserAction, InstallOutcome.Success -> Unit
            InstallOutcome.Aborted ->
                phase.value = if (downloadedFile != null) UpdatePhase.ReadyToInstall(release)
                else UpdatePhase.Available(release, skipped = false)
            is InstallOutcome.Failure -> phase.value = UpdatePhase.Failed(outcome.message, release)
        }
    }

    private fun releaseOf(p: UpdatePhase): ReleaseInfo? = when (p) {
        is UpdatePhase.Available -> p.release
        is UpdatePhase.Downloading -> p.release
        is UpdatePhase.ReadyToInstall -> p.release
        is UpdatePhase.NeedsInstallPermission -> p.release
        is UpdatePhase.Installing -> p.release
        is UpdatePhase.Failed -> p.release
        UpdatePhase.DebugBuild, UpdatePhase.Idle, UpdatePhase.Checking, UpdatePhase.UpToDate -> null
    }
}
