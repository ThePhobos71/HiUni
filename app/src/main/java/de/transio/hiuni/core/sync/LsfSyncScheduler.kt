package de.transio.hiuni.core.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules den [LsfSyncWorker] periodisch und/oder einmalig — die komplette
 * WorkManager-Mechanik liegt in [WorkerSyncScheduler].
 *
 * - [WorkerSyncScheduler.ensureScheduled] wird sowohl beim App-Start als auch
 *   beim Intervall-Wechsel in den Settings aufgerufen. Bei `intervalHours <= 0`
 *   wird der Periodic-Worker abgemeldet.
 * - [WorkerSyncScheduler.triggerNow] feuert einen einmaligen Sync (z.B. nach
 *   First-Login, per Settings-Button oder aus dem [PrefetchOrchestrator]).
 */
@Singleton
class LsfSyncScheduler @Inject constructor(
    @ApplicationContext context: Context
) : WorkerSyncScheduler<LsfSyncWorker>(context) {
    override val workerClass = LsfSyncWorker::class.java
    override val uniquePeriodicName = LsfSyncWorker.UNIQUE_PERIODIC_NAME
    override val uniqueOnceName = LsfSyncWorker.UNIQUE_ONCE_NAME
    override val logTag = "LsfSyncScheduler"
}
