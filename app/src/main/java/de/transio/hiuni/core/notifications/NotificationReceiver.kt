package de.transio.hiuni.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import de.transio.hiuni.MainActivity
import de.transio.hiuni.R
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.core.notifications.data.NotificationLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class NotificationReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationLog: NotificationLogRepository

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(NotificationScheduler.EXTRA_EVENT_ID, -1L)
        if (eventId == -1L) {
            Timber.w("NotificationReceiver invoked without EXTRA_EVENT_ID")
            return
        }
        val title = intent.getStringExtra(NotificationScheduler.EXTRA_EVENT_TITLE) ?: "HiUni"
        val body = context.getString(R.string.notification_event_body)

        // Push-Center-Log unabhängig von der OS-Benachrichtigung schreiben — wenn
        // POST_NOTIFICATIONS verweigert ist, sieht der User die Erinnerung sonst
        // gar nicht.
        logToPushCenter(
            kind = NotificationKind.EVENT,
            title = title,
            body = body,
            refKey = eventId.toString()
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Timber.d("Skipping notification for $eventId — POST_NOTIFICATIONS not granted")
                return
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.toInt(),
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID_EVENTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(eventId.toInt(), notification)
    }

    /**
     * Feuert die Log-Insert auf einen detached SupervisorScope ab — der Receiver
     * darf nicht blockieren (10s ANR-Limit), und `goAsync()` ist Overkill für
     * einen einzelnen Insert. Bei Crash leiser Log statt App-Tod.
     */
    private fun logToPushCenter(
        kind: NotificationKind,
        title: String,
        body: String?,
        refKey: String?
    ) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                notificationLog.log(kind = kind, title = title, body = body, refKey = refKey)
            }.onFailure { Timber.e(it, "Failed to log notification to push center") }
        }
    }
}
