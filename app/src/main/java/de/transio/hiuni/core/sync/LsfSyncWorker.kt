package de.transio.hiuni.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.AuthRequiredException
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.core.notifications.data.NotificationLogRepository
import de.transio.hiuni.feature.lsf.data.LsfExamsRepository
import de.transio.hiuni.feature.lsf.data.LsfMyCoursesRepository
import de.transio.hiuni.feature.lsf.data.LsfStundenplanRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant

/**
 * Periodischer + ein-malig getriggerter Sync für LSF-Kurse und LSF-Stundenplan.
 *
 * Reihenfolge: erst MyCourses (damit der Stundenplan-Sync seinen Kurs-Lookup
 * gegen frische Daten fährt), kurze Drossel-Pause, dann Stundenplan.
 *
 * Fehlerbehandlung:
 *   - CAS abgelaufen / Login nötig → [Result.failure] OHNE Retry (würde LSF nur
 *     hämmern; der User muss aktiv neu einloggen, was den OneTime-Sync neu triggert).
 *   - Transienter Netzwerk-Fehler → [Result.retry] (WorkManager handhabt Backoff).
 *   - Sonstiges → [Result.failure], geloggt via Timber.
 */
@HiltWorker
class LsfSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val myCourses: LsfMyCoursesRepository,
    private val stundenplan: LsfStundenplanRepository,
    private val exams: LsfExamsRepository,
    private val settings: SettingsDataStore,
    private val notificationLog: NotificationLogRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.i("LsfSyncWorker: start (run-attempt=$runAttemptCount)")

        // 1) MyCourses zuerst — der Stundenplan-Repo nutzt die LSF-Kurse als
        //    Lookup für die courseLsfId-Verknüpfung von VEVENTs.
        when (val courses = runCatchingSync { myCourses.sync() }) {
            is SyncOutcome.AuthFailure -> {
                if (runAttemptCount < AUTH_RETRY_THRESHOLD) {
                    Timber.w("LsfSyncWorker: Auth-Fail bei MyCourses, run=$runAttemptCount — retry für CAS-Renewal")
                    return Result.retry()
                }
                Timber.w("LsfSyncWorker: CAS-Login abgelaufen — kein Retry nach $runAttemptCount Versuchen")
                logAuthFailure("MyCourses")
                return Result.failure()
            }
            is SyncOutcome.Transient -> {
                Timber.w(courses.cause, "LsfSyncWorker: transienter Fehler beim MyCourses-Sync — retry")
                return Result.retry()
            }
            is SyncOutcome.Fatal -> {
                Timber.e(courses.cause, "LsfSyncWorker: MyCourses-Sync fehlgeschlagen")
                return Result.failure()
            }
            SyncOutcome.Ok -> Unit
        }

        // 2) Kurze Drossel zwischen den beiden Sync-Phasen.
        delay(THROTTLE_BETWEEN_PHASES_MS)

        // 3) Stundenplan.
        when (val plan = runCatchingSync { stundenplan.sync() }) {
            is SyncOutcome.AuthFailure -> {
                if (runAttemptCount < AUTH_RETRY_THRESHOLD) {
                    Timber.w("LsfSyncWorker: Auth-Fail bei Stundenplan, run=$runAttemptCount — retry")
                    return Result.retry()
                }
                Timber.w("LsfSyncWorker: CAS-Login abgelaufen vor Stundenplan-Sync")
                logAuthFailure("Stundenplan")
                return Result.failure()
            }
            is SyncOutcome.Transient -> {
                Timber.w(plan.cause, "LsfSyncWorker: transienter Fehler beim Stundenplan-Sync — retry")
                return Result.retry()
            }
            is SyncOutcome.Fatal -> {
                Timber.e(plan.cause, "LsfSyncWorker: Stundenplan-Sync fehlgeschlagen")
                return Result.failure()
            }
            SyncOutcome.Ok -> Unit
        }

        // 4) Drossel + Klausurtermine.
        delay(THROTTLE_BETWEEN_PHASES_MS)
        when (val ex = runCatchingSync { exams.refresh(force = true) }) {
            is SyncOutcome.AuthFailure -> {
                if (runAttemptCount < AUTH_RETRY_THRESHOLD) {
                    Timber.w("LsfSyncWorker: Auth-Fail bei Exams, run=$runAttemptCount — retry")
                    return Result.retry()
                }
                Timber.w("LsfSyncWorker: CAS-Login abgelaufen vor Exams-Sync")
                logAuthFailure("Klausuren")
                return Result.failure()
            }
            is SyncOutcome.Transient -> {
                Timber.w(ex.cause, "LsfSyncWorker: transienter Fehler beim Exams-Sync — retry")
                return Result.retry()
            }
            is SyncOutcome.Fatal -> {
                // ScrapeException landet hier — Tabelle hat unerwartete Struktur. Wir
                // schreiben das Log, brechen aber den Gesamt-Sync nicht ab; MyCourses
                // und Stundenplan waren schon erfolgreich. Exams-Timestamp bleibt alt.
                // Zusätzlich melden wir den Nutzer dezent übers Push-Center, damit er
                // nicht unbemerkt auf veralteten Klausurdaten sitzt (gleicher Pfad wie
                // die Auth-Fehler-Meldung).
                Timber.e(ex.cause, "LsfSyncWorker: Exams-Sync fatal — überspringe Timestamp-Update")
                logExamsScrapeFailure()
            }
            SyncOutcome.Ok -> {
                settings.setLastLsfExamsRefreshEpoch(Instant.now().toEpochMilli())
            }
        }

        settings.setLastLsfSyncEpoch(Instant.now().toEpochMilli())
        Timber.i("LsfSyncWorker: success")
        return Result.success()
    }

    /**
     * Meldet einen fatalen Exams-Scrape-Fehler (z.B. ScrapeException nach einer
     * LSF-HTML-Änderung) dezent übers Push-Center — sonst sähe der Nutzer
     * unbemerkt veraltete Klausurdaten. Kein Spam: Wir posten nur, wenn in den
     * jüngsten Einträgen noch keine ungelesene Meldung mit demselben refKey
     * steht. So bleibt es bei höchstens einer Meldung pro Fehlerserie, bis der
     * Nutzer sie im Push-Center gelesen (oder gelöscht) hat.
     */
    private suspend fun logExamsScrapeFailure() {
        runCatching {
            val alreadyPending = notificationLog.observeRecent(limit = 50).first()
                .any { it.refKey == EXAMS_SCRAPE_REF_KEY && !it.isRead }
            if (alreadyPending) {
                Timber.i("LsfSyncWorker: Exams-Scrape-Fehler bereits gemeldet — kein erneuter Push")
                return@runCatching
            }
            notificationLog.log(
                kind = NotificationKind.SYSTEM,
                title = "Klausurtermine veraltet",
                body = "Die Klausurdaten konnten nicht aktualisiert werden — die LSF-Seite hat sich vermutlich geändert. Die angezeigten Termine könnten veraltet sein.",
                refKey = EXAMS_SCRAPE_REF_KEY
            )
        }.onFailure { Timber.w(it, "Push-Center-Log (Exams-Scrape) fehlgeschlagen") }
    }

    /**
     * Loggt eine SYSTEM-Mitteilung ins Push-Center, damit der User merkt, dass
     * der Hintergrund-Sync wegen abgelaufenem CAS-Login stehengeblieben ist —
     * und weiß, dass er sich neu einloggen muss.
     */
    private suspend fun logAuthFailure(phase: String) {
        runCatching {
            notificationLog.log(
                kind = NotificationKind.SYSTEM,
                title = "LSF-Login abgelaufen",
                body = "Der Hintergrund-Sync ($phase) konnte sich nicht anmelden. Bitte in den Einstellungen neu einloggen.",
                refKey = "lsf_auth_$phase"
            )
        }.onFailure { Timber.w(it, "Push-Center-Log fehlgeschlagen") }
    }

    private sealed interface SyncOutcome {
        object Ok : SyncOutcome
        object AuthFailure : SyncOutcome
        data class Transient(val cause: Throwable) : SyncOutcome
        data class Fatal(val cause: Throwable) : SyncOutcome
    }

    private suspend fun runCatchingSync(block: suspend () -> AppResult<*>): SyncOutcome {
        return try {
            when (val res = block()) {
                is AppResult.Success<*> -> SyncOutcome.Ok
                is AppResult.Failure -> classify(res.error)
            }
        } catch (t: Throwable) {
            classify(t)
        }
    }

    private fun classify(t: Throwable): SyncOutcome {
        // Typisierte Auth-Erkennung — bricht nicht mehr bei Edits an deutschen
        // Fehler-Strings. Cause-Kette mit prüfen, falls die Exception in einem
        // RuntimeException-Wrapper steckt.
        var cursor: Throwable? = t
        while (cursor != null) {
            if (cursor is AuthRequiredException) return SyncOutcome.AuthFailure
            cursor = cursor.cause
        }

        val isTransient = t is IOException ||
            t is UnknownHostException ||
            t is SocketTimeoutException
        return if (isTransient) SyncOutcome.Transient(t) else SyncOutcome.Fatal(t)
    }

    companion object {
        /** Pause zwischen MyCourses- und Stundenplan-Sync — schonend für LSF. */
        private const val THROTTLE_BETWEEN_PHASES_MS = 400L

        /**
         * Bis zu wie viele runAttemptCount Auth-Fails wir noch als „Silent-Renewal
         * läuft gerade durch"-Race interpretieren — erst danach posten wir die
         * „Login abgelaufen"-Notification. WorkManager-Backoff (30s → 60s → 2min …)
         * gibt dem CAS-Session-Layer Zeit, nach einem frischen Login die TGT-
         * Propagation und Silent-Renewal abzuschließen.
         */
        private const val AUTH_RETRY_THRESHOLD = 2

        /**
         * Stabiler refKey für die „Klausurtermine veraltet"-Meldung. Dient als
         * Dedup-Anker, damit ein wiederholter Scrape-Fehler nicht bei jedem
         * periodischen Sync eine neue Notification erzeugt.
         */
        private const val EXAMS_SCRAPE_REF_KEY = "lsf_exams_scrape_failure"

        const val UNIQUE_PERIODIC_NAME = "lsf_periodic_sync"
        const val UNIQUE_ONCE_NAME = "lsf_once"
    }
}
