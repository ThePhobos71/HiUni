package de.transio.hiuni.core.update

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
import de.transio.hiuni.core.notifications.NotificationPresenter
import de.transio.hiuni.core.notifications.data.NotificationCategory
import de.transio.hiuni.core.notifications.data.NotificationSettingsStore
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mitteilung „HiUni X.Y.Z verfügbar" im leisen Kanal „App-Updates". Läuft wie die
 * Laufende-Vorlesung-Mitteilung am Push-Center vorbei (ein Update ist keine
 * Uni-Meldung), respektiert aber den In-App-Schalter der Kategorie. Ein Tap
 * öffnet den Update-Screen in den Einstellungen.
 */
@Singleton
class AndroidUpdateNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: NotificationSettingsStore
) : UpdateNotifier {

    override suspend fun notifyUpdateAvailable(release: ReleaseInfo) {
        if (!settingsStore.isEnabled(NotificationCategory.APP_UPDATES)) {
            Timber.d("Update-Mitteilung unterdrückt, Kategorie aus")
            return
        }
        if (!canPostNotifications()) return
        val title = "HiUni ${release.version.name} verfügbar"
        val text = "Zum Ansehen und Installieren tippen."
        val changelog = release.changelog.take(CHANGELOG_PREVIEW_CHARS)
        val notification = NotificationCompat.Builder(context, NotificationCategory.APP_UPDATES.channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (changelog.isBlank()) text else "$text\n\n$changelog"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    override fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun contentIntent(): PendingIntent {
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationPresenter.EXTRA_NAV_TARGET, NotificationPresenter.NAV_TARGET_APP_UPDATES)
        }
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        /** Synthetische Quelle: negativ, kollidiert nicht mit Event-IDs. */
        const val NOTIFICATION_ID = -1001
        const val CHANGELOG_PREVIEW_CHARS = 600
    }
}
