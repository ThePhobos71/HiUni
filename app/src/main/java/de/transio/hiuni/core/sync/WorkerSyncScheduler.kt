package de.transio.hiuni.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Gemeinsame Basis für die strukturell identischen WorkManager-Scheduler
 * ([LsfSyncScheduler], [SportSyncScheduler]). Beide schedulen exakt gleich:
 *
 *  - [ensureScheduled]: Periodic-Work mit `NetworkType.CONNECTED`-Constraint,
 *    exponentiellem Backoff ab 30s und `ExistingPeriodicWorkPolicy.UPDATE`
 *    (damit Intervall-Changes greifen, ohne bei jedem App-Start neu zu starten).
 *    Bei `intervalHours <= 0` wird der Periodic-Worker abgemeldet.
 *  - [triggerNow]: One-Time-Work mit denselben Constraints und
 *    `ExistingWorkPolicy.KEEP` (schneller Doppel-Klick → nur ein Auftrag).
 *
 * Subklassen liefern nur den Worker-Typ und die beiden Unique-Namen. Die
 * öffentliche API bleibt identisch zu den bisherigen Einzel-Schedulern
 * (`ensureScheduled(Int)` / `triggerNow()`), damit Callsites wie der
 * [PrefetchOrchestrator] unverändert bleiben.
 */
abstract class WorkerSyncScheduler<W : ListenableWorker>(
    private val context: Context,
) {

    /** Worker-Klasse für die Request-Builder. */
    protected abstract val workerClass: Class<W>

    /** Unique-Name des Periodic-Jobs (siehe Worker-Companion). */
    protected abstract val uniquePeriodicName: String

    /** Unique-Name des One-Time-Jobs (siehe Worker-Companion). */
    protected abstract val uniqueOnceName: String

    /** Log-Prefix, damit die Logs pro Scheduler unterscheidbar bleiben. */
    protected abstract val logTag: String

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    private fun connectedConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    fun ensureScheduled(intervalHours: Int) {
        if (intervalHours <= 0) {
            Timber.i("%s: cancel periodic sync (interval=%d)", logTag, intervalHours)
            workManager.cancelUniqueWork(uniquePeriodicName)
            return
        }
        val request: PeriodicWorkRequest = PeriodicWorkRequest.Builder(
            workerClass, intervalHours.toLong(), TimeUnit.HOURS
        )
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // UPDATE damit Intervall-Changes wirksam re-scheduled werden, ohne den
        // Worker bei jedem App-Start neu zu starten.
        workManager.enqueueUniquePeriodicWork(
            uniquePeriodicName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Timber.i("%s: scheduled periodic sync every %dh", logTag, intervalHours)
    }

    fun triggerNow() {
        val request: OneTimeWorkRequest = OneTimeWorkRequest.Builder(workerClass)
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // KEEP: mehrfaches schnelles Triggern (Button-Doppelklick oder
        // First-Login + Manual-Sync gleichzeitig) nimmt nur den ersten Auftrag.
        workManager.enqueueUniqueWork(
            uniqueOnceName,
            ExistingWorkPolicy.KEEP,
            request
        )
        Timber.i("%s: triggered one-time sync", logTag)
    }
}
