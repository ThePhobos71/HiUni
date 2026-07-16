package de.transio.hiuni.core.push

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.transio.hiuni.core.common.AppResult
import timber.log.Timber

/**
 * Führt eine (De-)Registrierung des FCM-Tokens beim Push-Server aus und
 * retryt bei transienten Fehlern über den WorkManager-Backoff. Der eigentliche
 * HTTP-Call + die Idempotenz liegen im [PushRegistrationManager]; dieser Worker
 * ist nur die robuste Ausführungs-Hülle.
 *
 * Input-Data:
 *   - [KEY_ACTION] = [ACTION_REGISTER] | [ACTION_UNREGISTER]
 *   - [KEY_TOKEN]  = FCM-Token (bei register Pflicht; bei unregister optional —
 *                    dann nimmt der Manager den zuletzt registrierten Token).
 */
@HiltWorker
class PushRegistrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val manager: PushRegistrationManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: ACTION_REGISTER
        val token = inputData.getString(KEY_TOKEN)

        val result: AppResult<Unit> = when (action) {
            ACTION_UNREGISTER -> manager.unregister(token)
            else -> {
                if (token.isNullOrBlank()) {
                    Timber.w("PushRegistrationWorker: register ohne Token — kein Retry")
                    return Result.failure()
                }
                manager.ensureRegistered(token)
            }
        }

        return when (result) {
            is AppResult.Success -> Result.success()
            is AppResult.Failure -> {
                // Netz-/HTTP-Fehler → Retry über Backoff; Config-Fehler (URL/Key
                // fehlt, IllegalArgument) sind nicht durch Warten heilbar → failure.
                val retryable = result.error is java.io.IOException
                if (retryable && runAttemptCount < MAX_ATTEMPTS) {
                    Timber.w(result.error, "PushRegistrationWorker: transient — retry (attempt=$runAttemptCount)")
                    Result.retry()
                } else {
                    Timber.w(result.error, "PushRegistrationWorker: $action fehlgeschlagen — kein weiterer Retry")
                    Result.failure()
                }
            }
        }
    }

    companion object {
        const val KEY_ACTION = "action"
        const val KEY_TOKEN = "token"
        const val ACTION_REGISTER = "register"
        const val ACTION_UNREGISTER = "unregister"
        const val UNIQUE_NAME = "push_registration"
        private const val MAX_ATTEMPTS = 5
    }
}
