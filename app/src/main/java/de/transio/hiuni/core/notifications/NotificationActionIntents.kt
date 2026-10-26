package de.transio.hiuni.core.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Übersetzt eine [NotificationActionSpec] in den expliziten Broadcast-Intent zum
 * [NotificationActionReceiver] und zurück.
 *
 * Jeder Intent trägt eine Data-URI aus Aktionstyp und System-ID der Notification.
 * `Intent.filterEquals` berücksichtigt die URI (Extras nicht), damit sind die
 * PendingIntents verschiedener Aktionen und Notifications sicher getrennt und
 * `FLAG_UPDATE_CURRENT` überschreibt nur die Aktion derselben Notification.
 */
object NotificationActionIntents {

    const val ACTION_MARK_MAILS_READ = "de.transio.hiuni.notification.action.MARK_MAILS_READ"
    const val ACTION_ARCHIVE_MAIL = "de.transio.hiuni.notification.action.ARCHIVE_MAIL"
    const val ACTION_COMPLETE_TODO = "de.transio.hiuni.notification.action.COMPLETE_TODO"
    const val ACTION_SNOOZE_TODO = "de.transio.hiuni.notification.action.SNOOZE_TODO"
    const val ACTION_SNOOZE_EVENT = "de.transio.hiuni.notification.action.SNOOZE_EVENT"

    private const val EXTRA_SYSTEM_ID = "hiuni_action_system_id"
    private const val EXTRA_MAIL_UIDS = "hiuni_action_mail_uids"
    private const val EXTRA_TARGET_ID = "hiuni_action_target_id"
    private const val EXTRA_TITLE = "hiuni_action_title"
    private const val EXTRA_BODY = "hiuni_action_body"

    private const val URI_SCHEME = "hiuni-notification-action"

    /** Ergebnis von [parse]: die Aktion und die System-ID der auslösenden Notification. */
    data class Parsed(val spec: NotificationActionSpec, val systemId: Int)

    fun build(context: Context, spec: NotificationActionSpec, systemId: Int): Intent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(actionFor(spec))
            .setData(
                Uri.Builder()
                    .scheme(URI_SCHEME)
                    .authority(NotificationActionPlanner.typeIndex(spec).toString())
                    .appendPath(systemId.toString())
                    .build()
            )
            .putExtra(EXTRA_SYSTEM_ID, systemId)
        when (spec) {
            is NotificationActionSpec.MarkMailsRead -> intent.putExtra(EXTRA_MAIL_UIDS, spec.uids.toLongArray())
            is NotificationActionSpec.ArchiveMail -> intent.putExtra(EXTRA_MAIL_UIDS, longArrayOf(spec.uid))
            is NotificationActionSpec.CompleteTodo -> intent.putExtra(EXTRA_TARGET_ID, spec.todoId)
            is NotificationActionSpec.SnoozeTodo -> intent.putExtra(EXTRA_TARGET_ID, spec.todoId)
            is NotificationActionSpec.SnoozeEvent -> intent
                .putExtra(EXTRA_TARGET_ID, spec.eventId)
                .putExtra(EXTRA_TITLE, spec.title)
                .putExtra(EXTRA_BODY, spec.body)
        }
        return intent
    }

    /** `null` bei fremder Action oder fehlenden Pflicht-Extras. */
    fun parse(intent: Intent): Parsed? {
        if (!intent.hasExtra(EXTRA_SYSTEM_ID)) return null
        val systemId = intent.getIntExtra(EXTRA_SYSTEM_ID, 0)
        val targetId = intent.getLongExtra(EXTRA_TARGET_ID, -1L).takeIf { it > 0L }
        val uids = intent.getLongArrayExtra(EXTRA_MAIL_UIDS)?.toList()?.takeIf { it.isNotEmpty() }
        val spec: NotificationActionSpec = when (intent.action) {
            ACTION_MARK_MAILS_READ -> NotificationActionSpec.MarkMailsRead(uids ?: return null)
            ACTION_ARCHIVE_MAIL -> NotificationActionSpec.ArchiveMail(uids?.singleOrNull() ?: return null)
            ACTION_COMPLETE_TODO -> NotificationActionSpec.CompleteTodo(targetId ?: return null)
            ACTION_SNOOZE_TODO -> NotificationActionSpec.SnoozeTodo(targetId ?: return null)
            ACTION_SNOOZE_EVENT -> NotificationActionSpec.SnoozeEvent(
                eventId = targetId ?: return null,
                title = intent.getStringExtra(EXTRA_TITLE) ?: return null,
                body = intent.getStringExtra(EXTRA_BODY)
            )
            else -> return null
        }
        return Parsed(spec, systemId)
    }

    private fun actionFor(spec: NotificationActionSpec): String = when (spec) {
        is NotificationActionSpec.MarkMailsRead -> ACTION_MARK_MAILS_READ
        is NotificationActionSpec.ArchiveMail -> ACTION_ARCHIVE_MAIL
        is NotificationActionSpec.CompleteTodo -> ACTION_COMPLETE_TODO
        is NotificationActionSpec.SnoozeTodo -> ACTION_SNOOZE_TODO
        is NotificationActionSpec.SnoozeEvent -> ACTION_SNOOZE_EVENT
    }
}
