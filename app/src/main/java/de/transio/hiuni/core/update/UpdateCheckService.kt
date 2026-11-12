package de.transio.hiuni.core.update

import timber.log.Timber
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Build-Daten der laufenden App (aus `BuildConfig`, in Tests frei wählbar). */
data class AppBuildInfo(
    val isDebug: Boolean,
    val versionCode: Int,
    val versionName: String,
    val packageName: String
)

/** Quelle des neuesten Releases (GitHub). */
interface ReleaseSource {
    /**
     * @return das neueste veröffentlichte Release oder `null`, wenn es keines gibt.
     * @throws UpdateException bei Netz-, Limit- oder Parse-Fehlern.
     */
    suspend fun fetchLatest(): ReleaseInfo?
}

/** Zeigt bzw. entfernt die Mitteilung „HiUni X.Y.Z verfügbar". */
interface UpdateNotifier {
    suspend fun notifyUpdateAvailable(release: ReleaseInfo)
    fun cancel()
}

enum class BackgroundCheckResult {
    /** Debug-Build, Auto-Prüfung aus oder gedrosselt: GitHub wurde nicht gefragt. */
    NOT_ALLOWED,
    UP_TO_DATE,
    /** Neue Version gefunden und gemeldet. */
    NOTIFIED,
    /** Neue Version, aber bereits gemeldet oder übersprungen. */
    UPDATE_KNOWN,
    FAILED
}

sealed interface CheckOutcome {
    /** Debug-Build: Updates gibt es nur für Release-Builds. */
    data object Disabled : CheckOutcome
    data object UpToDate : CheckOutcome
    data class Available(val release: ReleaseInfo, val skipped: Boolean) : CheckOutcome
}

/**
 * Gemeinsamer Kern für den Hintergrund-Check (Worker, App-Start) und den
 * manuellen Check aus den Einstellungen.
 */
@Singleton
class UpdateCheckService @Inject constructor(
    private val source: ReleaseSource,
    private val prefs: UpdatePreferences,
    private val notifier: UpdateNotifier,
    private val build: AppBuildInfo,
    private val clock: Clock
) {

    /** Gedrosselter Check mit Mitteilung. Wirft nie, Fehler landen als [BackgroundCheckResult.FAILED]. */
    suspend fun backgroundCheck(): BackgroundCheckResult {
        val snapshot = prefs.snapshot()
        val now = clock.millis()
        if (!UpdatePolicy.mayCheckInBackground(build.isDebug, snapshot.autoCheck, snapshot.lastCheckEpochMs, now)) {
            return BackgroundCheckResult.NOT_ALLOWED
        }
        val release = try {
            source.fetchLatest()
        } catch (e: UpdateException) {
            Timber.w(e, "Update-Check im Hintergrund fehlgeschlagen")
            return BackgroundCheckResult.FAILED
        }
        prefs.setLastCheckEpochMs(now)
        if (release == null || !UpdatePolicy.isNewer(release, build.versionCode)) {
            return BackgroundCheckResult.UP_TO_DATE
        }
        if (!UpdatePolicy.shouldNotify(release, build.versionCode, snapshot.skippedVersionCode, snapshot.lastNotifiedVersionCode)) {
            return BackgroundCheckResult.UPDATE_KNOWN
        }
        notifier.notifyUpdateAvailable(release)
        prefs.setLastNotifiedVersionCode(release.version.code)
        return BackgroundCheckResult.NOTIFIED
    }

    /**
     * Manueller Check: ohne Drossel, zeigt auch übersprungene Versionen (mit
     * Markierung), damit der Nutzer sie doch noch installieren kann.
     *
     * @throws UpdateException bei Fehlern.
     */
    suspend fun checkNow(): CheckOutcome {
        if (build.isDebug) return CheckOutcome.Disabled
        val release = source.fetchLatest()
        prefs.setLastCheckEpochMs(clock.millis())
        if (release == null || !UpdatePolicy.isNewer(release, build.versionCode)) return CheckOutcome.UpToDate
        val skipped = prefs.snapshot().skippedVersionCode == release.version.code
        return CheckOutcome.Available(release, skipped)
    }

    /** „Diese Version überspringen": keine Mitteilung mehr für genau diese Version. */
    suspend fun skip(release: ReleaseInfo) {
        prefs.setSkippedVersionCode(release.version.code)
        notifier.cancel()
    }
}
