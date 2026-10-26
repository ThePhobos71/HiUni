package de.transio.hiuni.core.push

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.security.CredentialsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Empfängt FCM-Data-Messages (Tickle-Modell).
 *
 * `FirebaseMessagingService` wird vom Firebase-SDK selbst instanziiert, NICHT
 * von Hilt. Deshalb ziehen wir unsere Singletons über einen manuellen
 * [EntryPoint] via [EntryPointAccessors].
 *
 * onMessageReceived (`type=mail_tickle` / `type=sync_tickle`): Wir merken uns
 * den Empfang (und ob FCM die Priorität herabgestuft hat, siehe
 * [TickleHandler.isDeprioritized]) und entscheiden mit dem reinen
 * [TickleHandler], ob wir reagieren. Wenn ja, enqueuen wir einen *expedited*
 * [PushSyncWorker], der auch im Doze kurz laufen darf. Je nach Entscheidung
 * holt er Mails übers [EmailRepository] und/oder stößt den gestaffelten
 * Feature-Prefetch an (siehe Architektur-Kommentar im Worker).
 *
 * onNewToken: Token-Re-Registrierung über den retry-fähigen
 * [PushRegistrationScheduler], aber nur wenn das Feature aktiv ist.
 */
class HiUniMessagingService : FirebaseMessagingService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PushEntryPoint {
        fun settings(): SettingsDataStore
        fun credentials(): CredentialsManager
        fun registrationScheduler(): PushRegistrationScheduler
    }

    private val entryPoint: PushEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, PushEntryPoint::class.java)
    }

    // Kurzlebiger Scope nur für onNewToken (fire-and-forget). Der Service-
    // Lifecycle ist kurz; die Arbeit selbst läuft im WorkManager-Job weiter.
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val deprioritized = TickleHandler.isDeprioritized(message.originalPriority, message.priority)
        Timber.i("FCM onMessageReceived: keys=${data.keys} deprioritized=$deprioritized")

        // Vorbedingungen synchron lesen: onMessageReceived läuft schon auf einem
        // Firebase-Background-Thread, ein kurzes runBlocking auf DataStore ist ok.
        val decision = runCatching {
            runBlocking {
                val settings = entryPoint.settings()
                if (TickleHandler.isTickle(data)) {
                    settings.recordTickle(System.currentTimeMillis(), deprioritized)
                }
                val preconditions = TickleHandler.Preconditions(
                    pushEnabled = settings.pushSyncEnabled.first(),
                    hasMailAccount = entryPoint.credentials().hasCredentials()
                )
                TickleHandler.decide(data, preconditions)
            }
        }.getOrElse {
            Timber.w(it, "FCM: Precondition-Read fehlgeschlagen, ignoriere Tickle")
            TickleHandler.Decision.IGNORE_SILENTLY
        }

        when (decision) {
            TickleHandler.Decision.SYNC_MAIL -> enqueueSync(runMail = true, runPrefetch = false)
            TickleHandler.Decision.SYNC_ALL -> enqueueSync(runMail = true, runPrefetch = true)
            TickleHandler.Decision.PREFETCH_ONLY -> enqueueSync(runMail = false, runPrefetch = true)
            TickleHandler.Decision.IGNORE_SILENTLY ->
                Timber.i("FCM: Tickle ohne aktives Feature bzw. Mail-Konto, still ignoriert")
            TickleHandler.Decision.UNKNOWN_TYPE ->
                Timber.d("FCM: unbekannter Message-Type, ignoriert")
        }
    }

    override fun onNewToken(token: String) {
        Timber.i("FCM onNewToken")
        serviceScope.launch {
            val enabled = runCatching { entryPoint.settings().pushSyncEnabled.first() }
                .getOrDefault(false)
            if (!enabled) {
                Timber.i("FCM onNewToken: Feature aus, keine Re-Registrierung")
                return@launch
            }
            entryPoint.registrationScheduler().enableAndRegister(knownToken = token)
        }
    }

    private fun enqueueSync(runMail: Boolean, runPrefetch: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<PushSyncWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    PushSyncWorker.KEY_RUN_MAIL to runMail,
                    PushSyncWorker.KEY_RUN_PREFETCH to runPrefetch
                )
            )
            // Expedited: darf auch im Doze-Fenster kurz laufen. Fällt bei
            // erschöpftem Expedited-Quota auf einen normalen Job zurück.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        // KEEP: Bei einem Tickle-Burst reicht ein Sync, ein bereits laufender
        // Durchgang holt die neuen Daten eh mit.
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            PushSyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        Timber.i("FCM: PushSyncWorker enqueued (expedited, mail=$runMail, prefetch=$runPrefetch)")
    }
}
