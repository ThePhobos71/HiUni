package de.transio.hiuni.core.update

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.File

/** Gemeinsame Test-Doubles fuer den Update-Pfad. */

fun testRelease(
    version: AppVersion = AppVersion(1, 4, 2),
    withApk: Boolean = true,
    changelog: String = "- Neu"
) = ReleaseInfo(
    tag = "v${version.name}",
    version = version,
    title = "HiUni ${version.name}",
    changelog = changelog,
    htmlUrl = "https://github.com/ThePhobos71/HiUni/releases/tag/v${version.name}",
    apk = if (withApk) ReleaseAsset("HiUni-${version.name}.apk", "https://example.invalid/a.apk", 1234) else null
)

fun testBuild(isDebug: Boolean = false, versionCode: Int = 10401) = AppBuildInfo(
    isDebug = isDebug,
    versionCode = versionCode,
    versionName = "1.4.1",
    packageName = "de.transio.hiuni"
)

class FakeUpdatePreferences(initial: UpdatePrefsSnapshot = UpdatePrefsSnapshot()) : UpdatePreferences {
    val snapshotFlow = MutableStateFlow(initial)
    var cached: CachedRelease? = null

    override val autoCheck: Flow<Boolean> = snapshotFlow.map { it.autoCheck }
    override suspend fun snapshot(): UpdatePrefsSnapshot = snapshotFlow.value
    override suspend fun setAutoCheck(enabled: Boolean) { snapshotFlow.value = snapshotFlow.value.copy(autoCheck = enabled) }
    override suspend fun setSkippedVersionCode(code: Int?) { snapshotFlow.value = snapshotFlow.value.copy(skippedVersionCode = code) }
    override suspend fun setLastCheckEpochMs(epochMs: Long) { snapshotFlow.value = snapshotFlow.value.copy(lastCheckEpochMs = epochMs) }
    override suspend fun setLastNotifiedVersionCode(code: Int) { snapshotFlow.value = snapshotFlow.value.copy(lastNotifiedVersionCode = code) }
    override suspend fun cachedRelease(): CachedRelease? = cached
    override suspend fun saveCachedRelease(cache: CachedRelease) { cached = cache }
}

class FakeReleaseSource(var result: ReleaseInfo? = null, var error: UpdateException? = null) : ReleaseSource {
    var calls = 0
    override suspend fun fetchLatest(): ReleaseInfo? {
        calls++
        error?.let { throw it }
        return result
    }
}

class FakeUpdateNotifier : UpdateNotifier {
    val shown = mutableListOf<ReleaseInfo>()
    var cancels = 0
    override suspend fun notifyUpdateAvailable(release: ReleaseInfo) { shown += release }
    override fun cancel() { cancels++ }
}

class FakeApkDownloader : ApkDownloader {
    var progressSteps: List<Int> = listOf(50, 100)
    var error: UpdateException? = null
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var clears = 0
    val file = File("build/tmp/fake-update.apk")

    override suspend fun download(asset: ReleaseAsset, onProgress: (Int) -> Unit): File {
        progressSteps.forEach(onProgress)
        gate?.await()
        error?.let { throw it }
        return file
    }

    override fun clearDownloads() { clears++ }
}

class FakeApkVerifier(var result: ApkCheck = ApkCheck.Ok) : ApkVerifier {
    override suspend fun verify(file: File, release: ReleaseInfo): ApkCheck = result
}

class FakeUpdateInstaller(var canRequest: Boolean = true) : UpdateInstaller {
    val installed = mutableListOf<File>()
    var settingsOpened = 0
    var error: UpdateException? = null
    override fun canRequestInstalls(): Boolean = canRequest
    override fun openInstallPermissionSettings() { settingsOpened++ }
    override suspend fun install(file: File) {
        error?.let { throw it }
        installed += file
    }
}
