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
 * `FirebaseMessagingService` wird vom Firebase-SDK selbst instanziiert — NICHT
 * von Hilt. Deshalb ziehen wir unsere Singletons über einen manuellen
 * [EntryPoint] via [EntryPointAccessors].
 *
 * onMessageReceived (`type=mail_tickle` / `type=sync_tickle`): Wir entscheiden
 * mit dem reinen [MailTickleHandler], ob wir reagieren (Feature an + Mail-Konto
 * vorhanden). Wenn ja, enqueuen wir einen *expedited* [MailPushSyncWorker] — der
 * darf auch im Doze kurz laufen und pullt die Mails übers bestehende
 * [EmailRepository], das seinerseits neue Mails in die Push-Center-/Notification-
 * Pipeline schreibt. Bei `sync_tickle` bekommt der Worker zusätzlich das Flag
 * [MailPushSyncWorker.KEY_RUN_PREFETCH], damit er nach dem Mail-Refresh den
 * gestaffelten Feature-Prefetch anstößt (siehe Architektur-Kommentar im Worker).
 *
 * onNewToken: Token-Re-Registrierung über den retry-fähigen
 * [PushRegistrationScheduler] — aber nur wenn das Feature aktiv ist.
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
        Timber.i("FCM onMessageReceived: keys=${data.keys}")

        // Vorbedingungen synchron lesen — onMessageReceived läuft schon auf einem
        // Firebase-Background-Thread, ein kurzes runBlocking auf DataStore ist ok.
        val decision = runCatching {
            runBlocking {
                val preconditions = MailTickleHandler.Preconditions(
                    pushEnabled = entryPoint.settings().mailPushEnabled.first(),
                    hasMailAccount = entryPoint.credentials().hasCredentials()
                )
                MailTickleHandler.decide(data, preconditions)
            }
        }.getOrElse {
            Timber.w(it, "FCM: Precondition-Read fehlgeschlagen — ignoriere Tickle")
            MailTickleHandler.Decision.IGNORE_SILENTLY
        }

        when (decision) {
            MailTickleHandler.Decision.SYNC_MAIL -> enqueueMailSync(runPrefetch = false)
            MailTickleHandler.Decision.SYNC_ALL -> enqueueMailSync(runPrefetch = true)
            MailTickleHandler.Decision.IGNORE_SILENTLY ->
                Timber.i("FCM: Tickle ohne aktives Mail-Konto/Feature — still ignoriert")
            MailTickleHandler.Decision.UNKNOWN_TYPE ->
                Timber.d("FCM: unbekannter Message-Type — ignoriert")
        }
    }

    override fun onNewToken(token: String) {
        Timber.i("FCM onNewToken")
        serviceScope.launch {
            val enabled = runCatching { entryPoint.settings().mailPushEnabled.first() }
                .getOrDefault(false)
            if (!enabled) {
                Timber.i("FCM onNewToken: Feature aus — keine Re-Registrierung")
                return@launch
            }
            entryPoint.registrationScheduler().enableAndRegister(knownToken = token)
        }
    }

    private fun enqueueMailSync(runPrefetch: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<MailPushSyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(MailPushSyncWorker.KEY_RUN_PREFETCH to runPrefetch))
            // Expedited: darf auch im Doze-Fenster kurz laufen. Fällt bei
            // erschöpftem Expedited-Quota auf einen normalen Job zurück.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        // KEEP: Bei einem Tickle-Burst reicht ein Sync — ein bereits laufender
        // Refresh holt die neuen Mails eh mit.
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            MailPushSyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        Timber.i("FCM: MailPushSyncWorker enqueued (expedited, prefetch=$runPrefetch)")
    }
}
