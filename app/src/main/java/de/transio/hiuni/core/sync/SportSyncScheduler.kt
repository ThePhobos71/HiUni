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
 * Wie [LsfSyncScheduler], aber für den supersaas-Sportplan. Aktuell gibt es
 * kein User-Setting fürs Intervall — der Aufrufer in HiUniApplication setzt
 * fest auf 6h.
 */
@Singleton
class SportSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    fun ensureScheduled(intervalHours: Int) {
        if (intervalHours <= 0) {
            Timber.i("SportSyncScheduler: cancel periodic sync (interval=$intervalHours)")
            workManager.cancelUniqueWork(SportSyncWorker.UNIQUE_PERIODIC_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request: PeriodicWorkRequest = PeriodicWorkRequestBuilder<SportSyncWorker>(
            intervalHours.toLong(), TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            SportSyncWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Timber.i("SportSyncScheduler: scheduled periodic sync every ${intervalHours}h")
    }

    fun triggerNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request: OneTimeWorkRequest = OneTimeWorkRequestBuilder<SportSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            SportSyncWorker.UNIQUE_ONCE_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        Timber.i("SportSyncScheduler: triggered one-time sync")
    }
}
