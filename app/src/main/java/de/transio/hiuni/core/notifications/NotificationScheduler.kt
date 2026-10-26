package de.transio.hiuni.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import de.transio.hiuni.core.notifications.data.NotificationKind
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Plant einen lokalen Reminder per [AlarmManager].
     *
     * - `kind` steuert das Icon/Filter im Push-Center. Default `EVENT` für
     *   Backward-Compat mit den ursprünglichen Kalender-Reminder-Callsites.
     * - `body` ist optional; wenn null, fällt der Receiver auf die statische
     *   Default-Body-String-Ressource zurück.
     * - PendingIntent.FLAG_UPDATE_CURRENT sorgt dafür, dass mehrfach
     *   geschedulte Reminder für die gleiche `eventId` den vorhergehenden
     *   Alarm überschreiben (statt doppelt zu feuern).
     * - `snooze = true` („Später" aus der Notification) plant einen eigenen
     *   Alarm neben dem regulären derselben `eventId`: der Intent trägt die
     *   Action [ACTION_SNOOZE] und ist damit für AlarmManager ein anderer
     *   PendingIntent. So überschreibt ein Snooze nicht den bereits geplanten
     *   Reminder der nächsten Serien-Occurrence. Angezeigt wird trotzdem unter
     *   derselben System-ID; [cancel] räumt beide Varianten ab.
     */
    fun schedule(
        eventId: Long,
        title: String,
        triggerAt: Instant,
        kind: NotificationKind = NotificationKind.EVENT,
        body: String? = null,
        snooze: Boolean = false
    ) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val triggerMillis = triggerAt.toEpochMilli()
        if (triggerMillis <= System.currentTimeMillis()) {
            Timber.d("Skipping schedule for past event $eventId at $triggerAt")
            return
        }

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            if (snooze) {
                action = ACTION_SNOOZE
                putExtra(EXTRA_SNOOZED, true)
            }
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_EVENT_TITLE, title)
            putExtra(EXTRA_KIND, kind.name)
            if (body != null) putExtra(EXTRA_BODY, body)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        }
    }

    /** Cancelt den regulären Alarm und einen eventuell laufenden Snooze derselben ID. */
    fun cancel(eventId: Long) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val variants = listOf(
            Intent(context, NotificationReceiver::class.java),
            Intent(context, NotificationReceiver::class.java).setAction(ACTION_SNOOZE)
        )
        for (intent in variants) {
            val pending = PendingIntent.getBroadcast(
                context,
                eventId.toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            ) ?: continue
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "hiuni_event_id"
        const val EXTRA_EVENT_TITLE = "hiuni_event_title"
        /** Optionaler Extra: Name eines [NotificationKind]; Fallback `EVENT`. */
        const val EXTRA_KIND = "hiuni_event_kind"
        /** Optionaler Extra: Body-Zeile der Notification. Wenn fehlend, nimmt der Receiver den statischen Default-String. */
        const val EXTRA_BODY = "hiuni_event_body"

        /** Intent-Action eines Snooze-Alarms; trennt ihn vom regulären Alarm derselben ID. */
        const val ACTION_SNOOZE = "de.transio.hiuni.notification.SNOOZE"

        /** `true` bei einem Snooze-Alarm: der Receiver plant danach keine Folge-Occurrence. */
        const val EXTRA_SNOOZED = "hiuni_event_snoozed"

        /**
         * Channel-ID der Termin-Erinnerungen. Historisch hier definiert; die
         * kanonische Quelle ist jetzt [de.transio.hiuni.core.notifications.data.NotificationCategory.EVENTS].
         * Wert unverändert gelassen, damit bestehende Channels erhalten bleiben.
         */
        const val CHANNEL_ID_EVENTS = "hiuni_event_reminders"
    }
}
