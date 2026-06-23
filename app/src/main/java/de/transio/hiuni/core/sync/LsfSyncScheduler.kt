package de.transio.hiuni.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules den [LsfSyncWorker] periodisch und/oder einmalig.
 *
 * - [ensureScheduled] wird sowohl beim App-Start als auch beim Intervall-Wechsel
 *   in den Settings aufgerufen. Bei `intervalHours <= 0` wird der Periodic-Worker
 *   abgemeldet.
 * - [triggerNow] feuert einen einmaligen Sync (z.B. nach First-Login oder per
 *   Settings-Button). `ExistingWorkPolicy.KEEP` verhindert Doppel-Enqueues bei
 *   schnellem Re-Klick.
 */
@Singleton
class LsfSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    fun ensureScheduled(intervalHours: Int) {
        if (intervalHours <= 0) {
            Timber.i("LsfSyncScheduler: cancel periodic sync (interval=$intervalHours)")
            workManager.cancelUniqueWork(LsfSyncWorker.UNIQUE_PERIODIC_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request: PeriodicWorkRequest = PeriodicWorkRequestBuilder<LsfSyncWorker>(
            intervalHours.toLong(), TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // UPDATE damit Intervall-Changes wirksam re-scheduled werden, ohne den
        // Worker bei jedem App-Start neu zu starten.
        workManager.enqueueUniquePeriodicWork(
            LsfSyncWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Timber.i("LsfSyncScheduler: scheduled periodic sync every ${intervalHours}h")
    }

    fun triggerNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request: OneTimeWorkRequest = OneTimeWorkRequestBuilder<LsfSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // KEEP: wenn der User den Button kurz hintereinander mehrfach drückt
        // (oder First-Login + Manual-Sync gleichzeitig triggern), nehmen wir den
        // ersten Auftrag — kein doppelter Hit auf LSF.
        workManager.enqueueUniqueWork(
            LsfSyncWorker.UNIQUE_ONCE_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        Timber.i("LsfSyncScheduler: triggered one-time sync")
    }
}
