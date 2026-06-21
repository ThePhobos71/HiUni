package de.transio.hiuni.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun schedule(eventId: Long, title: String, triggerAt: Instant) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val triggerMillis = triggerAt.toEpochMilli()
        if (triggerMillis <= System.currentTimeMillis()) {
            Timber.d("Skipping schedule for past event $eventId at $triggerAt")
            return
        }

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_EVENT_TITLE, title)
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

    fun cancel(eventId: Long) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val intent = Intent(context, NotificationReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        ) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    companion object {
        const val EXTRA_EVENT_ID = "hiuni_event_id"
        const val EXTRA_EVENT_TITLE = "hiuni_event_title"
        const val CHANNEL_ID_EVENTS = "hiuni_event_reminders"
    }
}
