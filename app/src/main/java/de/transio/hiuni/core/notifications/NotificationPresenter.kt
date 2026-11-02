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
import de.transio.hiuni.core.notifications.data.NotificationCategory
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.core.notifications.data.NotificationLogRepository
import de.transio.hiuni.core.notifications.data.NotificationSettingsStore
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
    private val notificationLog: NotificationLogRepository,
    private val settingsStore: NotificationSettingsStore
) {

    /**
     * @param actions optionale Aktions-Buttons (siehe [NotificationActionPlanner]).
     *   Jeder Button schickt einen Broadcast an den [NotificationActionReceiver].
     */
    suspend fun present(
        kind: NotificationKind,
        title: String,
        body: String? = null,
        refKey: String? = null,
        systemId: Int,
        actions: List<NotificationActionSpec> = emptyList()
    ) {
        // 0) In-App-Kategorie-Gate: eine vom Nutzer ausgeschaltete Kategorie
        //    unterdrückt ALLES (weder OS-Notification noch Push-Center-Eintrag).
        //    Liegt bewusst vor dem Log — sonst würden stummgeschaltete Kategorien
        //    im Center weiter auflaufen.
        if (!settingsStore.isEnabled(kind)) {
            Timber.d("Notification unterdrückt — Kategorie ${NotificationCategory.of(kind)} aus")
            return
        }

        // 1) Push-Center-Log — passiert immer (sofern Kategorie an), unabhängig
        //    von der OS-Permission.
        runCatching {
            notificationLog.log(kind = kind, title = title, body = body, refKey = refKey)
        }.onFailure { Timber.e(it, "Push-Center-Log fehlgeschlagen") }

        // 2) OS-Notification — nur wenn Permission erteilt (Android 13+).
        if (!canPostNotifications()) {
            Timber.d("OS-Notification übersprungen, POST_NOTIFICATIONS nicht erteilt")
            return
        }

        // Channel je Kategorie, so kann der Nutzer in den Android-Systemeinstellungen
        // z.B. „Noten" stummschalten, „Klausuren" aber laut lassen.
        val channelId = NotificationCategory.channelIdFor(kind)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body ?: context.getString(R.string.notification_event_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(kind, systemId))
        actions.forEach { builder.addAction(buildAction(it, systemId)) }

        NotificationManagerCompat.from(context).notify(systemId, builder.build())
    }

    /**
     * Ersatz-Notification, wenn eine Aktion aus [present] fehlschlägt (z.B. Archivieren
     * ohne Netz). Ersetzt die ursprüngliche Meldung unter derselben [systemId], hat
     * keine Aktionen und landet bewusst NICHT im Push-Center: dort steht die
     * ursprüngliche Meldung schon. Ein Tap öffnet das passende Ziel.
     */
    fun showActionFailure(kind: NotificationKind, message: String, systemId: Int) =
        showReplacement(kind, message, ACTION_FAILURE_HINT, systemId)

    /**
     * Bestätigung einer erfolgreichen Aktion mit sichtbarem Ergebnis (z.B. „F102
     * gebucht 14:00–16:00" nach „Buchen"). Ersetzt die ursprüngliche Meldung unter
     * derselben [systemId]; ein Tap öffnet das passende Ziel (Bib-Screen).
     */
    fun showActionConfirmation(kind: NotificationKind, message: String, systemId: Int) =
        showReplacement(kind, message, ACTION_CONFIRMATION_HINT, systemId)

    private fun showReplacement(kind: NotificationKind, title: String, text: String, systemId: Int) {
        if (!canPostNotifications()) return
        val notification = NotificationCompat.Builder(context, NotificationCategory.channelIdFor(kind))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(kind, systemId))
            .build()
        NotificationManagerCompat.from(context).notify(systemId, notification)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun contentIntent(kind: NotificationKind, systemId: Int): PendingIntent {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Signal an MainActivity: nach Start direkt zum passenden Ziel
            // navigieren (Push-Center bzw. Aufgaben). Ohne diesen Extra landet
            // der User bloß auf Home.
            putExtra(EXTRA_NAV_TARGET, navTargetFor(kind))
        }
        return PendingIntent.getActivity(
            context,
            systemId,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Aktions-Button als expliziter Broadcast an den [NotificationActionReceiver].
     * Request-Code und Data-URI sind je Notification und Aktion eindeutig, damit
     * sich die PendingIntents nicht gegenseitig überschreiben.
     */
    private fun buildAction(spec: NotificationActionSpec, systemId: Int): NotificationCompat.Action {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            NotificationActionPlanner.requestCode(systemId, spec),
            NotificationActionIntents.build(context, spec, systemId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(iconFor(spec), spec.label, pendingIntent)
            // Mail-Aktionen und Bib-Buchen erst nach dem Entsperren des Geräts
            // (Android 12+), damit niemand vom Sperrbildschirm aus fremde Mails
            // archiviert oder unter fremdem Konto einen Raum bucht.
            .setAuthenticationRequired(spec.requiresUnlock)
            .setShowsUserInterface(false)
            .build()
    }

    private fun iconFor(spec: NotificationActionSpec): Int = when (spec) {
        is NotificationActionSpec.MarkMailsRead -> R.drawable.ic_notification_action_read
        is NotificationActionSpec.ArchiveMail -> R.drawable.ic_notification_action_archive
        is NotificationActionSpec.CompleteTodo -> R.drawable.ic_notification_action_done
        is NotificationActionSpec.SnoozeTodo,
        is NotificationActionSpec.SnoozeEvent -> R.drawable.ic_notification_action_snooze
        is NotificationActionSpec.BookBibRoom -> R.drawable.ic_notification_action_book
    }

    companion object {
        /**
         * Negative IDs sind für synthetische Quellen reserviert (Test-Button,
         * SYSTEM-Meldungen), damit sie sich nicht mit Event-IDs aus der
         * `custom_events`-Tabelle überschneiden.
         */
        const val TEST_NOTIFICATION_ID = -1

        /** Zweite Zeile der Ersatz-Notification nach einer fehlgeschlagenen Aktion. */
        const val ACTION_FAILURE_HINT = "Zum Öffnen tippen."

        /** Zweite Zeile der Bestätigung nach einer erfolgreichen Aktion. */
        const val ACTION_CONFIRMATION_HINT = "Steht in deinem Kalender. Zum Öffnen tippen."

        /** Intent-Extra-Key — von [MainActivity] gelesen, um nach Notification-Tap das Push-Center zu öffnen. */
        const val EXTRA_NAV_TARGET = "hiuni_nav_target"
        const val NAV_TARGET_NOTIFICATIONS_CENTER = "notifications_center"
        const val NAV_TARGET_TODOS = "todos"
        const val NAV_TARGET_GRADES = "grades"
        const val NAV_TARGET_EMAIL = "email"
        const val NAV_TARGET_BIB = "bib"
        /** Ziel der Laufende-Vorlesung-Mitteilung (kein [NotificationKind], eigener Notifier). */
        const val NAV_TARGET_CALENDAR = "calendar"

        /**
         * Ziel beim Tap auf die OS-Notification. Aufgaben-Erinnerungen öffnen
         * direkt den Aufgaben-Screen (dort wird abgehakt), Neue-Note-Meldungen den
         * Noten-Screen, Neue-Mail-Meldungen das Postfach, Frei-Meldungen eines
         * Lieblings-Gruppenraums den Bib-Screen (dort wird gebucht), alles andere
         * das Push-Center.
         */
        fun navTargetFor(kind: NotificationKind): String = when (kind) {
            NotificationKind.TODO -> NAV_TARGET_TODOS
            NotificationKind.GRADE -> NAV_TARGET_GRADES
            NotificationKind.MAIL -> NAV_TARGET_EMAIL
            NotificationKind.BIB -> NAV_TARGET_BIB
            else -> NAV_TARGET_NOTIFICATIONS_CENTER
        }
    }
}
