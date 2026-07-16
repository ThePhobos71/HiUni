package de.transio.hiuni.core.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wie [LsfSyncScheduler], aber für den supersaas-Sportplan. Aktuell gibt es
 * kein User-Setting fürs Intervall — der Aufrufer in HiUniApplication setzt
 * fest auf 6h. Die gesamte WorkManager-Mechanik liegt in [WorkerSyncScheduler].
 */
@Singleton
class SportSyncScheduler @Inject constructor(
    @ApplicationContext context: Context
) : WorkerSyncScheduler<SportSyncWorker>(context) {
    override val workerClass = SportSyncWorker::class.java
    override val uniquePeriodicName = SportSyncWorker.UNIQUE_PERIODIC_NAME
    override val uniqueOnceName = SportSyncWorker.UNIQUE_ONCE_NAME
    override val logTag = "SportSyncScheduler"
}
