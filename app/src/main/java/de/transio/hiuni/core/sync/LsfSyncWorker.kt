package de.transio.hiuni.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.feature.lsf.data.LsfMyCoursesRepository
import de.transio.hiuni.feature.lsf.data.LsfStundenplanRepository
import kotlinx.coroutines.delay
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
    private val settings: SettingsDataStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.i("LsfSyncWorker: start (run-attempt=$runAttemptCount)")

        // 1) MyCourses zuerst — der Stundenplan-Repo nutzt die LSF-Kurse als
        //    Lookup für die courseLsfId-Verknüpfung von VEVENTs.
        when (val courses = runCatchingSync { myCourses.sync() }) {
            is SyncOutcome.AuthFailure -> {
                Timber.w("LsfSyncWorker: CAS-Login abgelaufen — kein Retry")
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
                Timber.w("LsfSyncWorker: CAS-Login abgelaufen vor Stundenplan-Sync — kein Retry")
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

        settings.setLastLsfSyncEpoch(Instant.now().toEpochMilli())
        Timber.i("LsfSyncWorker: success")
        return Result.success()
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
        val msg = t.message.orEmpty()
        val isAuth = msg.contains("Login erforderlich", ignoreCase = true) ||
            msg.contains("CAS-Login abgelaufen", ignoreCase = true) ||
            msg.contains("erneut anmelden", ignoreCase = true)
        if (isAuth) return SyncOutcome.AuthFailure

        val isTransient = t is IOException ||
            t is UnknownHostException ||
            t is SocketTimeoutException
        return if (isTransient) SyncOutcome.Transient(t) else SyncOutcome.Fatal(t)
    }

    companion object {
        /** Pause zwischen MyCourses- und Stundenplan-Sync — schonend für LSF. */
        private const val THROTTLE_BETWEEN_PHASES_MS = 400L

        const val UNIQUE_PERIODIC_NAME = "lsf_periodic_sync"
        const val UNIQUE_ONCE_NAME = "lsf_once"
    }
}
