package de.transio.hiuni.core.update

/**
 * Reine Entscheidungsregeln für den Update-Check. Keine Android-Abhängigkeit,
 * damit Debug-Sperre, Drossel und Überspringen als JVM-Test abgesichert sind.
 */
object UpdatePolicy {

    /**
     * Mindestabstand zwischen zwei automatischen Checks. GitHub erlaubt ohne Token
     * 60 Anfragen pro Stunde und IP, wir bleiben weit darunter.
     */
    const val MIN_BACKGROUND_INTERVAL_MS: Long = 6L * 60 * 60 * 1000

    /**
     * Darf ein automatischer Check (Worker, App-Start) laufen? Debug-Builds prüfen
     * nie, ihr Versionscode passt ohnehin nicht zur Release-Formel.
     */
    fun mayCheckInBackground(
        isDebug: Boolean,
        autoCheck: Boolean,
        lastCheckEpochMs: Long,
        nowEpochMs: Long
    ): Boolean {
        if (isDebug || !autoCheck) return false
        // Zeitstempel in der Zukunft (Uhr zurückgestellt): nicht für immer sperren.
        if (lastCheckEpochMs > nowEpochMs) return true
        return nowEpochMs - lastCheckEpochMs >= MIN_BACKGROUND_INTERVAL_MS
    }

    fun isNewer(release: ReleaseInfo, installedVersionCode: Int): Boolean =
        release.version.code > installedVersionCode

    /**
     * Mitteilung nur für eine neuere Version mit APK, die der Nutzer nicht
     * übersprungen hat und die noch nicht gemeldet wurde (sonst käme alle sechs
     * Stunden dieselbe Meldung).
     */
    fun shouldNotify(
        release: ReleaseInfo,
        installedVersionCode: Int,
        skippedVersionCode: Int?,
        lastNotifiedVersionCode: Int?
    ): Boolean {
        if (!isNewer(release, installedVersionCode)) return false
        if (release.apk == null) return false
        val code = release.version.code
        return code != skippedVersionCode && code != lastNotifiedVersionCode
    }
}
