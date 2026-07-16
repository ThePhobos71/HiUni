package de.transio.hiuni.core.push

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.security.CredentialsManager
import de.transio.hiuni.core.sync.PrefetchOrchestrator
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
 * Sync-Tickle: Kommt der Push als `sync_tickle` (Input-Data [KEY_RUN_PREFETCH]
 * `true`), stößt der Worker NACH dem Mail-Refresh zusätzlich den
 * [PrefetchOrchestrator] an. Das passiert bewusst aus dem Worker — nicht aus dem
 * [HiUniMessagingService] — weil der Service-Prozess kurzlebig ist und nach
 * `onMessageReceived` sofort gekillt werden kann, während der expedited Worker
 * seine Foreground-Garantie über das Doze-Fenster behält. So läuft der (fire-and-
 * forget im ApplicationScope startende, gestaffelte) Prefetch nicht ins Leere.
 * Der Orchestrator ist selbst TTL-/Auth-/Offline-gegated, hämmert LSF/Learnweb
 * also höchstens 1x pro TTL — trotz 15-min-Tickle-Takt.
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
    private val settings: SettingsDataStore,
    private val prefetchOrchestrator: PrefetchOrchestrator
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

        val runPrefetch = inputData.getBoolean(KEY_RUN_PREFETCH, false)
        Timber.i("MailPushSyncWorker: start (run-attempt=$runAttemptCount, prefetch=$runPrefetch)")
        val result = when (val res = runCatching { emailRepository.refresh(force = true) }.getOrElse { AppResult.Failure(it) }) {
            is AppResult.Success -> {
                Timber.i("MailPushSyncWorker: refresh ok")
                Result.success()
            }
            is AppResult.Failure -> classifyToResult(res.error)
        }

        // sync_tickle: nach dem Mail-Refresh den gestaffelten Feature-Prefetch
        // anstoßen. Auch bei transientem Mail-Fehler sinnvoll — die übrigen
        // Features (Noten/Kurse/Learnweb …) sind davon unabhängig. Der Prefetch
        // ist selbst TTL-/Auth-/Offline-gegated; ein Doppelaufruf im Retry-Fall
        // ist durch den running-AtomicBoolean idempotent.
        if (runPrefetch) {
            runCatching { prefetchOrchestrator.prefetch() }
                .onFailure { Timber.w(it, "MailPushSyncWorker: Prefetch-Anstoß fehlgeschlagen") }
        }
        return result
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

        /**
         * Input-Data-Flag: `true` → nach dem Mail-Refresh zusätzlich den
         * [PrefetchOrchestrator] anstoßen (sync_tickle). `false`/fehlend → nur
         * Mail (mail_tickle).
         */
        const val KEY_RUN_PREFETCH = "run_prefetch"
    }
}
