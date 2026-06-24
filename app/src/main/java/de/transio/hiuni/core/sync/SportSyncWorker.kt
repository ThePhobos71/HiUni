package de.transio.hiuni.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.feature.sport.data.SportRepository
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Periodischer Hochschulsport-Sync. supersaas ist eine öffentliche Seite —
 * kein Auth-Pfad. Transienter Netzwerk-Fehler = Retry, alles andere = Failure.
 *
 * Die 6h-Throttle steckt im Repository, nicht hier — der Worker ruft mit
 * `force = false` und wird so still no-op, falls er von WorkManager früher
 * angesprungen wird als das Intervall vermuten lässt.
 */
@HiltWorker
class SportSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sportRepository: SportRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.i("SportSyncWorker: start (run-attempt=$runAttemptCount)")
        return try {
            when (val res = sportRepository.refresh(force = false)) {
                is AppResult.Success<*> -> {
                    Timber.i("SportSyncWorker: success")
                    Result.success()
                }
                is AppResult.Failure -> classify(res.error)
            }
        } catch (t: Throwable) {
            classify(t)
        }
    }

    private fun classify(t: Throwable): Result {
        val isTransient = t is IOException ||
            t is UnknownHostException ||
            t is SocketTimeoutException
        return if (isTransient) {
            Timber.w(t, "SportSyncWorker: transienter Fehler — retry")
            Result.retry()
        } else {
            Timber.e(t, "SportSyncWorker: fataler Fehler")
            Result.failure()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "sport_periodic_sync"
        const val UNIQUE_ONCE_NAME = "sport_once"
    }
}
