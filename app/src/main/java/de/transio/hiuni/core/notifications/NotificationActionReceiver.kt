package de.transio.hiuni.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import de.transio.hiuni.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * Empfängt die Aktions-Buttons der OS-Notifications („Als gelesen", „Archivieren",
 * „Erledigt", „Später", „Buchen"). Die Intents baut [NotificationActionIntents], die
 * eigentliche Arbeit macht der [NotificationActionHandler].
 *
 * Ablauf: Notification sofort entfernen (der Button soll spürbar reagieren und
 * kein zweites Mal getippt werden), dann die Aktion ausführen. Schlägt sie fehl
 * oder läuft in das Zeitlimit, ersetzt eine kurze Fehler-Notification unter
 * derselben ID die alte; ein Tap darauf öffnet den passenden Screen.
 *
 * `exported=false`: nur die eigenen PendingIntents erreichen den Receiver.
 * Arbeit läuft per `goAsync()` im App-Scope mit hartem Zeitlimit, wie beim
 * [ReminderRestoreReceiver].
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var handler: NotificationActionHandler
    @Inject lateinit var presenter: NotificationPresenter
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val parsed = NotificationActionIntents.parse(intent) ?: run {
            Timber.w("NotificationActionReceiver: unbekannte Aktion %s ignoriert", intent.action)
            return
        }
        val (spec, systemId) = parsed
        NotificationManagerCompat.from(context).cancel(systemId)

        val pending = goAsync()
        appScope.launch {
            try {
                val timeout = if (spec is NotificationActionSpec.BookBibRoom) BOOK_TIMEOUT_MS else TIMEOUT_MS
                val outcome = withTimeoutOrNull(timeout) { handler.handle(spec) }
                    ?: NotificationActionOutcome.Failed(TIMEOUT_MESSAGE).also {
                        Timber.w("NotificationActionReceiver: %s nach %d ms abgebrochen", spec::class.simpleName, timeout)
                    }
                when (outcome) {
                    is NotificationActionOutcome.Failed -> presenter.showActionFailure(
                        kind = NotificationActionPlanner.kindOf(spec),
                        message = outcome.message,
                        systemId = systemId
                    )
                    is NotificationActionOutcome.Confirmed -> presenter.showActionConfirmation(
                        kind = NotificationActionPlanner.kindOf(spec),
                        message = outcome.message,
                        systemId = systemId
                    )
                    NotificationActionOutcome.Done -> Unit
                }
            } catch (t: Throwable) {
                Timber.w(t, "NotificationActionReceiver: Aktion fehlgeschlagen")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /**
         * Obergrenze für die Arbeit nach `goAsync()`. Höher als beim Restore (9 s),
         * weil Archivieren eine IMAP-Verbindung samt MOVE braucht. Aktions-Broadcasts
         * aus Notifications laufen ohne `FLAG_RECEIVER_FOREGROUND` in der
         * Hintergrund-Queue, deren ANR-Grenze bei 60 s liegt.
         */
        const val TIMEOUT_MS = 20_000L

        /**
         * „Buchen" braucht ggf. Snapshot-Refresh (samt CAS-Re-Login), die Buchung
         * selbst und den anschließenden Refresh für den Kalender. Bleibt mit Abstand
         * unter der 60-s-ANR-Grenze der Hintergrund-Queue.
         */
        const val BOOK_TIMEOUT_MS = 45_000L
        const val TIMEOUT_MESSAGE = "Zeitüberschreitung, bitte in der App erneut versuchen"
    }
}
