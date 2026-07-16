package de.transio.hiuni.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.transio.hiuni.R
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.core.sync.RecurringReminderRescheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class NotificationReceiver : BroadcastReceiver() {

    @Inject lateinit var presenter: NotificationPresenter
    @Inject lateinit var recurringReminderRescheduler: RecurringReminderRescheduler

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(NotificationScheduler.EXTRA_EVENT_ID, -1L)
        if (eventId == -1L) {
            Timber.w("NotificationReceiver invoked without EXTRA_EVENT_ID")
            return
        }
        val title = intent.getStringExtra(NotificationScheduler.EXTRA_EVENT_TITLE) ?: "HiUni"
        // EXTRA_KIND ist optional — alte Reminder ohne diesen Extra (z.B. nach
        // App-Update mit noch nicht erneuertem Alarm) fallen auf EVENT zurück.
        val kind = intent.getStringExtra(NotificationScheduler.EXTRA_KIND)
            ?.let { name -> runCatching { NotificationKind.valueOf(name) }.getOrNull() }
            ?: NotificationKind.EVENT
        // EXTRA_BODY ist optional — wenn null, nimmt der Receiver den statischen
        // Default-String, damit alte EVENT-Reminder ohne Body weiterhin sinnvoll
        // gerendert werden.
        val body = intent.getStringExtra(NotificationScheduler.EXTRA_BODY)
            ?: context.getString(R.string.notification_event_body)

        // Feuer-Zeitpunkt für das Recurrence-Re-Scheduling. Praktisch ≈ Trigger-Zeit
        // (occStart - reminderMinutes); der Rescheduler rekonstruiert daraus die
        // gefeuerte Occurrence und plant die nächste ein.
        val firedAt = Instant.now()

        // Detached SupervisorScope, weil BroadcastReceiver nicht suspenden darf
        // (10s-ANR-Limit) und goAsync() für einen Notify-Call zu schwer wäre.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                presenter.present(
                    kind = kind,
                    title = title,
                    body = body,
                    refKey = eventId.toString(),
                    systemId = eventId.toInt()
                )
            }.onFailure { Timber.e(it, "Notification-Präsentation fehlgeschlagen") }

            // Nach dem Feuern die nächste Occurrence eines wiederkehrenden Events
            // neu planen. No-op für Single-shot-Events (kein recurrenceRule).
            runCatching {
                recurringReminderRescheduler.rescheduleAfterFire(eventId, firedAt)
            }.onFailure { Timber.e(it, "Recurrence-Reminder-Re-Scheduling fehlgeschlagen") }
        }
    }
}
