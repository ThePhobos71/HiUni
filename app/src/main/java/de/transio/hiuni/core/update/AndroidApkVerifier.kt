package de.transio.hiuni.core.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Prüft eine geladene APK gegen die installierte App. */
interface ApkVerifier {
    suspend fun verify(file: File, release: ReleaseInfo): ApkCheck
}

/**
 * Liest Paketname, versionCode und Signatur-Zertifikate der APK
 * (`getPackageArchiveInfo` mit `GET_SIGNING_CERTIFICATES`) und der installierten
 * App, vergleicht die SHA-256-Fingerprints und entscheidet über [ApkVerification].
 * Android selbst würde ein anders signiertes Update zwar auch ablehnen, aber erst
 * nach dem Bestätigungsdialog und mit einer unverständlichen Meldung.
 */
@Singleton
class AndroidApkVerifier @Inject constructor(
    @ApplicationContext private val context: Context
) : ApkVerifier {

    override suspend fun verify(file: File, release: ReleaseInfo): ApkCheck = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installedInfo = runCatching { installedPackageInfo(pm) }
            .onFailure { Timber.e(it, "Eigene Paketinfo nicht lesbar") }
            .getOrNull() ?: return@withContext ApkCheck.Unreadable
        val installed = InstalledFacts(
            packageName = installedInfo.packageName,
            versionCode = installedInfo.longVersionCode,
            signerDigests = currentSigners(installedInfo)
        )
        val apk = runCatching { archivePackageInfo(pm, file) }
            .onFailure { Timber.w(it, "APK nicht lesbar") }
            .getOrNull()
            ?.let { info ->
                ApkFacts(
                    packageName = info.packageName,
                    versionCode = info.longVersionCode,
                    signerDigests = currentSigners(info),
                    signerHistory = signerHistory(info)
                )
            }
        ApkVerification.verify(installed, apk, release.version.code).also {
            Timber.i("APK-Prüfung %s: %s", file.name, it)
        }
    }

    private fun installedPackageInfo(pm: PackageManager): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    private fun archivePackageInfo(pm: PackageManager, file: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    /** Aktuelle Signer ohne frühere (rotierte) Zertifikate. */
    private fun currentSigners(info: PackageInfo): Set<String> {
        val signing = info.signingInfo ?: return emptySet()
        return signing.apkContentsSigners.orEmpty()
            .map { ApkVerification.sha256Hex(it.toByteArray()) }
            .toSet()
    }

    /** Aktuelle plus frühere Zertifikate (Schlüsselrotation, nur bei einem Signer). */
    private fun signerHistory(info: PackageInfo): Set<String> {
        val signing = info.signingInfo ?: return emptySet()
        val history = if (signing.hasMultipleSigners()) {
            signing.apkContentsSigners
        } else {
            signing.signingCertificateHistory
        }
        return (history.orEmpty().map { ApkVerification.sha256Hex(it.toByteArray()) } + currentSigners(info)).toSet()
    }
}
