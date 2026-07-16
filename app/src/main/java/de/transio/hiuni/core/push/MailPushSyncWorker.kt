package de.transio.hiuni.core.push

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.security.CredentialsManager
import de.transio.hiuni.feature.email.data.EmailRepository
import kotlinx.coroutines.flow.first
import de.transio.hiuni.core.datastore.SettingsDataStore
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Expedited One-Time-Worker, den [HiUniMessagingService] bei einem
 * `mail_tickle`-FCM-Push feuert. Er ruft [EmailRepository.refresh] mit
 * `force = true` — die Repo-Logik pullt frische IMAP-Header und schreibt bei
 * neuen ungelesenen Mails selbstständig einen Eintrag ins bestehende
 * Push-Center (siehe `EmailRepositoryImpl.refresh`). Wir müssen die
 * Notification-Pipeline hier also NICHT nochmal anfassen.
 *
 * Doze-Tauglichkeit: Der Service enqueued den Request als *expedited*
 * (setExpedited) — dann darf er auch im Doze-Fenster kurz laufen.
 *
 * Robustheit:
 *   - Kein Mail-Konto / Feature aus → [Result.success] ohne Netz-Hit (Tickle
 *     wird still verworfen; sollte durch [MailTickleHandler] eh vorgefiltert
 *     sein, doppelt hält besser bei Race mit Logout).
 *   - Transienter Netzfehler → [Result.retry] (WorkManager-Backoff).
 *   - Sonstiges → [Result.failure], geloggt.
 */
@HiltWorker
class MailPushSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val emailRepository: EmailRepository,
    private val credentials: CredentialsManager,
    private val settings: SettingsDataStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Race-Schutz: zwischen Tickle-Entscheidung und Worker-Start kann der
        // User sich ausgeloggt oder das Feature abgeschaltet haben. Dann still
        // beenden, statt IMAP ohne Credentials anzufassen.
        val enabled = runCatching { settings.mailPushEnabled.first() }.getOrDefault(false)
        if (!enabled || !credentials.hasCredentials()) {
            Timber.i("MailPushSyncWorker: kein aktives Mail-Konto / Feature aus — skip")
            return Result.success()
        }

        Timber.i("MailPushSyncWorker: start (run-attempt=$runAttemptCount)")
        return when (val res = runCatching { emailRepository.refresh(force = true) }.getOrElse { AppResult.Failure(it) }) {
            is AppResult.Success -> {
                Timber.i("MailPushSyncWorker: refresh ok")
                Result.success()
            }
            is AppResult.Failure -> classifyToResult(res.error)
        }
    }

    private fun classifyToResult(t: Throwable): Result {
        val transient = t is IOException || t is UnknownHostException || t is SocketTimeoutException
        return if (transient) {
            Timber.w(t, "MailPushSyncWorker: transienter Fehler — retry")
            Result.retry()
        } else {
            Timber.e(t, "MailPushSyncWorker: Mail-Refresh fehlgeschlagen")
            Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "mail_push_sync"
    }
}
