package de.transio.hiuni.core.notifications.lecture

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import de.transio.hiuni.MainActivity
import de.transio.hiuni.R
import de.transio.hiuni.core.notifications.NotificationPresenter
import de.transio.hiuni.core.notifications.data.NotificationCategory
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Baut die Laufende-Vorlesung-Mitteilung.
 *
 * ### Aufbau
 *
 *  - Titel: Kursname, Inhalt: „F101 · bis 11:45".
 *  - Kopf: „läuft noch" plus Countdown-Chronometer bis zum Ende
 *    (`setUsesChronometer` + `setChronometerCountDown` + `setWhen(end)`). Der Countdown
 *    tickt im System, die App wird dafür nicht geweckt.
 *  - `setOngoing`, `setOnlyAlertOnce`, `setSilent`, Channel mit `IMPORTANCE_LOW`.
 *  - `setTimeoutAfter(end - now)`: das System entfernt die Mitteilung zum Ende.
 *  - Tap öffnet den Kalender, Wegwischen (Android 14+) merkt sich das Vorkommen.
 *  - Kein Fortschrittsbalken: der bräuchte regelmäßige Updates, der Countdown nicht.
 *
 * ### Android 16 Live Updates (Promotion)
 *
 * Laut SDK-Quellen (android-36: `Notification.hasPromotableCharacteristics`,
 * android-36.1: `Builder.setRequestPromotedOngoing`, `EXTRA_REQUEST_PROMOTED_ONGOING`,
 * Permission `POST_PROMOTED_NOTIFICATIONS`) wird eine Mitteilung nur hervorgehoben,
 * wenn sie ongoing ist, einen Titel hat, keine Gruppen-Zusammenfassung ist, keine
 * Custom Views nutzt, Standard-, BigText-, Call- oder Progress-Style hat, die
 * Hervorhebung anfragt, nicht colorized ist, der Channel nicht `IMPORTANCE_MIN` hat
 * und der Nutzer die Hervorhebung für die App erlaubt
 * (`NotificationManager.canPostPromotedNotifications`). Unsere Mitteilung erfüllt die
 * Form-Kriterien; die Anfrage setzen wir ab API 36 als Extra
 * (`android.requestPromotedOngoing`), weil androidx.core 1.13.1 noch kein
 * `setRequestPromotedOngoing` kennt und compileSdk 36 die 36.1-Konstante nicht hat.
 * Ältere Android-Versionen ignorieren das Extra und zeigen eine normale
 * fortlaufende Mitteilung.
 */
@Singleton
class AndroidLiveLectureNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) : LiveLectureNotifier {

    override fun show(occurrence: LiveLectureOccurrence, now: Instant) {
        if (!canPostNotifications()) {
            Timber.d("LiveLecture: POST_NOTIFICATIONS fehlt, keine Mitteilung")
            return
        }
        val id = LiveLecturePlanner.notificationId(occurrence.masterId)
        val builder = NotificationCompat.Builder(context, NotificationCategory.LIVE_LECTURE.channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(occurrence.title)
            .setContentText(LiveLecturePlanner.contentText(occurrence, ZoneId.systemDefault()))
            .setSubText(LiveLecturePlanner.SUB_TEXT)
            .setWhen(occurrence.end.toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setTimeoutAfter(LiveLecturePlanner.timeoutMillis(occurrence, now))
            .setContentIntent(contentIntent(id))
            .setDeleteIntent(LiveLectureReceiver.dismissIntent(context, id, occurrence.key))
        if (canRequestPromotion()) {
            builder.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
        }
        NotificationManagerCompat.from(context).notify(TAG, id, builder.build())
    }

    override fun cancel(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(TAG, notificationId)
    }

    override fun activeNotificationIds(): Set<Int> {
        val manager = context.getSystemService<NotificationManager>() ?: return emptySet()
        return runCatching {
            manager.activeNotifications.filter { it.tag == TAG }.map { it.id }.toSet()
        }.getOrElse {
            Timber.w(it, "LiveLecture: aktive Mitteilungen nicht lesbar")
            emptySet()
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Hervorhebung nur ab Android 16 und nur, wenn der Nutzer sie für HiUni nicht
     * abgeschaltet hat. Die API ist dort öffentlich, aber hinter einem Feature-Flag;
     * ein Fehler darf die Mitteilung selbst nie verhindern.
     */
    private fun canRequestPromotion(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        val manager = context.getSystemService<NotificationManager>() ?: return false
        return runCatching { manager.canPostPromotedNotifications() }.getOrDefault(false)
    }

    private fun contentIntent(id: Int): PendingIntent {
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationPresenter.EXTRA_NAV_TARGET, NotificationPresenter.NAV_TARGET_CALENDAR)
        }
        return PendingIntent.getActivity(
            context,
            id,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        /** Tag aller Laufende-Vorlesung-Mitteilungen; trennt sie sicher von allen anderen. */
        const val TAG = "hiuni_live_lecture"

        /**
         * Wert von `Notification.EXTRA_REQUEST_PROMOTED_ONGOING` (API 36.1). Als Literal,
         * weil compileSdk 36 die Konstante noch nicht kennt.
         */
        const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    }
}
