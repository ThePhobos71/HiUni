package de.transio.hiuni.core.update

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import de.transio.hiuni.core.sync.WorkerSyncScheduler
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Periodischer Update-Check. Drossel, Debug-Sperre und Auto-Schalter stecken im
 * [UpdateCheckService]; der Worker meldet immer Erfolg, ein Fehler wird beim
 * nächsten Intervall von selbst wiederholt (kein Retry-Sturm gegen GitHub).
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val service: UpdateCheckService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = service.backgroundCheck()
        Timber.i("UpdateCheckWorker: %s", result)
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "app_update_periodic_check"
        const val UNIQUE_ONCE_NAME = "app_update_check_once"
        const val INTERVAL_HOURS = 12
    }
}

@Singleton
class UpdateCheckScheduler @Inject constructor(
    @ApplicationContext context: Context
) : WorkerSyncScheduler<UpdateCheckWorker>(context) {
    override val workerClass = UpdateCheckWorker::class.java
    override val uniquePeriodicName = UpdateCheckWorker.UNIQUE_PERIODIC_NAME
    override val uniqueOnceName = UpdateCheckWorker.UNIQUE_ONCE_NAME
    override val logTag = "UpdateCheckScheduler"
}
