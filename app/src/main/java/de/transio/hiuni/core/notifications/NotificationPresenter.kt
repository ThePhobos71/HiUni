package de.transio.hiuni.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.transio.hiuni.MainActivity
import de.transio.hiuni.R
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.core.notifications.data.NotificationLogRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Einheitlicher Pfad zum Anzeigen einer Mitteilung: schreibt einen Eintrag
 * ins Push-Center-Log UND zeigt die OS-Notification an. Beide Quellen
 * (Kalender-Reminder via [NotificationReceiver] und manuelle Test-Mitteilungen
 * aus den Einstellungen) gehen durch den gleichen Code, damit das User-Erleben
 * identisch ist.
 *
 * Wenn `POST_NOTIFICATIONS` auf Android 13+ verweigert ist, wird die System-
 * Notification übersprungen — der Log-Eintrag landet trotzdem im Center, damit
 * der User die Mitteilung dort einsehen kann.
 */
@Singleton
class NotificationPresenter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationLog: NotificationLogRepository
) {

    suspend fun present(
        kind: NotificationKind,
        title: String,
        body: String? = null,
        refKey: String? = null,
        systemId: Int
    ) {
        // 1) Push-Center-Log — passiert immer, unabhängig von der OS-Permission.
        runCatching {
            notificationLog.log(kind = kind, title = title, body = body, refKey = refKey)
        }.onFailure { Timber.e(it, "Push-Center-Log fehlgeschlagen") }

        // 2) OS-Notification — nur wenn Permission erteilt (Android 13+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Timber.d("OS-Notification übersprungen — POST_NOTIFICATIONS nicht erteilt")
                return
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            systemId,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID_EVENTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body ?: context.getString(R.string.notification_event_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(systemId, notification)
    }

    companion object {
        /**
         * Negative IDs sind für synthetische Quellen reserviert (Test-Button,
         * SYSTEM-Meldungen), damit sie sich nicht mit Event-IDs aus der
         * `custom_events`-Tabelle überschneiden.
         */
        const val TEST_NOTIFICATION_ID = -1
    }
}
