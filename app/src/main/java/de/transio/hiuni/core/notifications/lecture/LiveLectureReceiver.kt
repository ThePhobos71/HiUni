package de.transio.hiuni.core.notifications.lecture

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.AndroidEntryPoint
import de.transio.hiuni.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * Empfängt den Weck-Alarm der Laufende-Vorlesung-Mitteilung ([ACTION_WAKE]: neu
 * planen, laufende Vorlesung zeigen, nächsten Beginn stellen) und das Wegwischen
 * einer solchen Mitteilung ([ACTION_DISMISSED]: Vorkommen merken).
 *
 * `exported=false`: nur die eigenen PendingIntents erreichen ihn.
 */
@AndroidEntryPoint
class LiveLectureReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: LiveLectureScheduler
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_WAKE && action != ACTION_DISMISSED) {
            Timber.w("LiveLectureReceiver: unerwartete Aktion %s ignoriert", action)
            return
        }
        val dismissedKey = intent.getStringExtra(EXTRA_KEY)
        val pending = goAsync()
        appScope.launch {
            try {
                withTimeoutOrNull(TIMEOUT_MS) {
                    if (action == ACTION_WAKE) {
                        scheduler.replan()
                    } else if (dismissedKey != null) {
                        scheduler.onDismissed(dismissedKey)
                    }
                } ?: Timber.w("LiveLectureReceiver: %s nach %d ms abgebrochen", action, TIMEOUT_MS)
            } catch (t: Throwable) {
                Timber.w(t, "LiveLectureReceiver: %s fehlgeschlagen", action)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_WAKE = "de.transio.hiuni.live_lecture.WAKE"
        const val ACTION_DISMISSED = "de.transio.hiuni.live_lecture.DISMISSED"
        private const val EXTRA_KEY = "hiuni_live_lecture_key"

        /**
         * Request-Code des Weck-Alarms. Liegt im ID-Bereich der Laufenden Vorlesung,
         * aber außerhalb der Mitteilungs-IDs (die enden bei
         * [LiveLecturePlanner.NOTIFICATION_ID_MAX]).
         */
        private const val WAKE_REQUEST_CODE = LiveLecturePlanner.NOTIFICATION_ID_MAX + 1

        /** Knapp unter dem 10-s-Fenster eines Broadcast-Receivers. */
        private const val TIMEOUT_MS = 9_000L

        /** Fester Slot: jeder neue Weck-Alarm ersetzt den vorherigen. */
        fun wakeIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            WAKE_REQUEST_CODE,
            Intent(context, LiveLectureReceiver::class.java).setAction(ACTION_WAKE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        /** Delete-Intent je Mitteilung; die Data-URI hält die PendingIntents getrennt. */
        fun dismissIntent(context: Context, notificationId: Int, key: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                Intent(context, LiveLectureReceiver::class.java)
                    .setAction(ACTION_DISMISSED)
                    .setData(Uri.parse("hiuni://live-lecture/$key"))
                    .putExtra(EXTRA_KEY, key),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
    }
}
