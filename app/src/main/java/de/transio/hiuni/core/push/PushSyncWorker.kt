package de.transio.hiuni.core.push

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.security.CredentialsManager
import de.transio.hiuni.core.sync.PrefetchOrchestrator
import de.transio.hiuni.feature.email.data.EmailRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Expedited One-Time-Worker, den [HiUniMessagingService] bei einem Tickle
 * feuert. Zwei Teile, per Input-Data gesteuert:
 *
 *  - [KEY_RUN_MAIL]: [EmailRepository.refresh] mit `force = true`. Die
 *    Repo-Logik pullt frische IMAP-Header und schreibt bei neuen ungelesenen
 *    Mails selbstständig einen Eintrag ins Push-Center (siehe
 *    `EmailRepositoryImpl.refresh`). Läuft nur mit hinterlegtem Mail-Konto.
 *  - [KEY_RUN_PREFETCH]: danach den [PrefetchOrchestrator] anstoßen und auf
 *    dessen Job warten (max. [PREFETCH_TIMEOUT_MS]). Das Warten ist wichtig:
 *    der Prefetch läuft im ApplicationScope, und nur solange der expedited
 *    Worker läuft, hält das System den Prozess im Doze-Fenster wach. Der
 *    Orchestrator ist selbst TTL-/Auth-/Offline-gegated, hämmert LSF/Learnweb
 *    also höchstens 1x pro TTL, trotz 15-min-Tickle-Takt.
 *
 * Doze-Tauglichkeit: Der Service enqueued den Request als *expedited*
 * (setExpedited), dann darf er auch im Doze-Fenster kurz laufen.
 *
 * Robustheit:
 *   - Feature aus → [Result.success] ohne Netz-Hit (Race mit Toggle-Off).
 *   - Transienter Mail-Netzfehler → [Result.retry] (WorkManager-Backoff).
 *   - Sonstiger Mail-Fehler → [Result.failure], geloggt.
 *   - Prefetch-Fehler beeinflussen das Ergebnis nicht (Orchestrator loggt selbst).
 */
@HiltWorker
class PushSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val emailRepository: EmailRepository,
    private val credentials: CredentialsManager,
    private val settings: SettingsDataStore,
    private val prefetchOrchestrator: PrefetchOrchestrator
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Race-Schutz: zwischen Tickle-Entscheidung und Worker-Start kann der
        // User das Feature abgeschaltet haben. Dann still beenden.
        val enabled = runCatching { settings.pushSyncEnabled.first() }.getOrDefault(false)
        if (!enabled) {
            Timber.i("PushSyncWorker: Feature aus, skip")
            return Result.success()
        }

        val runMail = inputData.getBoolean(KEY_RUN_MAIL, false) && credentials.hasCredentials()
        val runPrefetch = inputData.getBoolean(KEY_RUN_PREFETCH, false)
        Timber.i("PushSyncWorker: start (run-attempt=$runAttemptCount, mail=$runMail, prefetch=$runPrefetch)")

        val result = if (runMail) refreshMail() else Result.success()

        // Auch bei transientem Mail-Fehler sinnvoll: die übrigen Features (Noten,
        // Kurse, Learnweb, …) sind davon unabhängig. Ein Doppelaufruf im Retry-Fall
        // ist durch den running-AtomicBoolean des Orchestrators idempotent.
        if (runPrefetch) awaitPrefetch()
        return result
    }

    private suspend fun refreshMail(): Result =
        when (val res = runCatching { emailRepository.refresh(force = true) }.getOrElse { AppResult.Failure(it) }) {
            is AppResult.Success -> {
                Timber.i("PushSyncWorker: Mail-Refresh ok")
                Result.success()
            }
            is AppResult.Failure -> classifyToResult(res.error)
        }

    private suspend fun awaitPrefetch() {
        val job = runCatching { prefetchOrchestrator.prefetch() }
            .onFailure { Timber.w(it, "PushSyncWorker: Prefetch-Anstoß fehlgeschlagen") }
            .getOrNull()
            // null = es läuft bereits ein Warmup (z.B. vom App-Start), der holt die Daten.
            ?: return
        val finished = withTimeoutOrNull(PREFETCH_TIMEOUT_MS) { job.join() }
        if (finished == null) {
            Timber.w("PushSyncWorker: Prefetch nach ${PREFETCH_TIMEOUT_MS / 1000}s nicht fertig, läuft im Hintergrund weiter")
        }
    }

    private fun classifyToResult(t: Throwable): Result {
        val transient = t is IOException || t is UnknownHostException || t is SocketTimeoutException
        return if (transient) {
            Timber.w(t, "PushSyncWorker: transienter Fehler, retry")
            Result.retry()
        } else {
            Timber.e(t, "PushSyncWorker: Mail-Refresh fehlgeschlagen")
            Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "push_sync"

        /** Input-Data-Flag: `true` → Mail-Refresh (nur wenn ein Mail-Konto existiert). */
        const val KEY_RUN_MAIL = "run_mail"

        /** Input-Data-Flag: `true` → gestaffelten Feature-Prefetch anstoßen und abwarten. */
        const val KEY_RUN_PREFETCH = "run_prefetch"

        /**
         * Obergrenze fürs Warten auf den Prefetch. Expedited Jobs sollen nach
         * wenigen Minuten fertig sein; ein voller Warmup (7 Features à 3s
         * Staffelung plus Netz) liegt deutlich darunter.
         */
        const val PREFETCH_TIMEOUT_MS = 3L * 60 * 1000
    }
}
