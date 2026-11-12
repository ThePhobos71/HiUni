package de.transio.hiuni.core.update

import java.security.MessageDigest

/** Signatur- und Versionsdaten der installierten App. */
data class InstalledFacts(
    val packageName: String,
    val versionCode: Long,
    /** SHA-256 (Hex) der aktuellen Signatur-Zertifikate. */
    val signerDigests: Set<String>
)

/** Signatur- und Versionsdaten einer heruntergeladenen APK. */
data class ApkFacts(
    val packageName: String,
    val versionCode: Long,
    /** SHA-256 (Hex) der Zertifikate, mit denen die APK signiert ist. */
    val signerDigests: Set<String>,
    /** Inklusive früherer Zertifikate bei Schlüsselrotation (APK-Signatur v3). */
    val signerHistory: Set<String> = signerDigests
)

sealed interface ApkCheck {
    data object Ok : ApkCheck
    data object Unreadable : ApkCheck
    data object WrongPackage : ApkCheck
    data object SignatureMismatch : ApkCheck
    data object NotNewer : ApkCheck
    data object VersionMismatch : ApkCheck
}

/**
 * Reiner Kern der APK-Prüfung vor der Installation. Die Android-Seite
 * ([AndroidApkVerifier]) liest nur die Fakten aus dem PackageManager, die
 * Entscheidung fällt hier.
 *
 * Reihenfolge: lesbar, Paketname, Signatur, Version. Die Signatur kommt vor der
 * Version, damit eine fremde APK immer als Signaturfehler gemeldet wird.
 */
object ApkVerification {

    fun verify(installed: InstalledFacts, apk: ApkFacts?, expectedVersionCode: Int?): ApkCheck {
        if (apk == null) return ApkCheck.Unreadable
        if (apk.packageName != installed.packageName) return ApkCheck.WrongPackage
        if (!signatureMatches(installed.signerDigests, apk)) return ApkCheck.SignatureMismatch
        if (apk.versionCode <= installed.versionCode) return ApkCheck.NotNewer
        if (expectedVersionCode != null && apk.versionCode != expectedVersionCode.toLong()) {
            return ApkCheck.VersionMismatch
        }
        return ApkCheck.Ok
    }

    /**
     * Gleiche Zertifikate, oder (bei rotiertem Schlüssel) alle installierten
     * Zertifikate stehen in der Historie der neuen APK. Leere Mengen passen nie.
     */
    private fun signatureMatches(installed: Set<String>, apk: ApkFacts): Boolean {
        if (installed.isEmpty() || apk.signerDigests.isEmpty()) return false
        if (apk.signerDigests == installed) return true
        return apk.signerHistory.containsAll(installed)
    }

    fun message(check: ApkCheck): String = when (check) {
        ApkCheck.Ok -> "APK geprüft"
        ApkCheck.Unreadable -> "APK nicht lesbar, Update abgebrochen"
        ApkCheck.WrongPackage -> "APK gehört nicht zu HiUni, Update abgebrochen"
        ApkCheck.SignatureMismatch -> "Signatur passt nicht, Update abgebrochen"
        ApkCheck.NotNewer -> "APK ist nicht neuer als die installierte Version"
        ApkCheck.VersionMismatch -> "APK-Version passt nicht zum Release, Update abgebrochen"
    }

    fun sizeMatches(expected: Long, actual: Long): Boolean = expected == actual

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
