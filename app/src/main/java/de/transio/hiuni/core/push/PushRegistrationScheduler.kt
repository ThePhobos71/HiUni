package de.transio.hiuni.core.push

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Stößt (De-)Registrierungen des FCM-Tokens als retry-fähige WorkManager-Jobs
 * an. Zentrale Einstiegspunkte fürs Feature:
 *
 *   - [enableAndRegister]: nach Toggle-On oder onNewToken. Holt (falls nötig)
 *     den aktuellen FCM-Token und enqueued einen Register-Job.
 *   - [unregister]: nach Toggle-Off. Enqueued einen Unregister-Job.
 *
 * `ExistingWorkPolicy.REPLACE` unter demselben Unique-Namen sorgt dafür, dass
 * ein schnelles On/Off/On den Server nicht mit widersprüchlichen Requests
 * flutet — der zuletzt gewünschte Zustand gewinnt.
 */
@Singleton
class PushRegistrationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /**
     * Holt den aktuellen FCM-Token und enqueued einen Register-Job. Wenn der
     * Token bereits übergeben wurde (z.B. aus `onNewToken`), wird er direkt
     * genutzt, sonst via [FirebaseMessaging.getToken] geholt.
     */
    suspend fun enableAndRegister(knownToken: String? = null) {
        val token = knownToken ?: runCatching { fetchFcmToken() }
            .onFailure { Timber.w(it, "PushRegistrationScheduler: FCM-Token-Fetch fehlgeschlagen") }
            .getOrNull()
        if (token.isNullOrBlank()) {
            Timber.w("PushRegistrationScheduler: kein Token verfügbar — Register verschoben")
            return
        }
        enqueue(PushRegistrationWorker.ACTION_REGISTER, token)
    }

    /** Enqueued einen Unregister-Job (nimmt den zuletzt registrierten Token). */
    fun unregister() {
        enqueue(PushRegistrationWorker.ACTION_UNREGISTER, token = null)
    }

    /**
     * Suspending-Wrapper um [FirebaseMessaging.getToken] ohne die
     * `kotlinx-coroutines-play-services`-Zusatz-Dependency — wir hängen einfach
     * einen Completion-Listener an den Task.
     */
    private suspend fun fetchFcmToken(): String =
        suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> if (cont.isActive) cont.resume(token) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }

    private fun enqueue(action: String, token: String?) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val data = Data.Builder()
            .putString(PushRegistrationWorker.KEY_ACTION, action)
            .apply { if (token != null) putString(PushRegistrationWorker.KEY_TOKEN, token) }
            .build()
        val request = OneTimeWorkRequestBuilder<PushRegistrationWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            PushRegistrationWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        Timber.i("PushRegistrationScheduler: enqueued $action")
    }
}
