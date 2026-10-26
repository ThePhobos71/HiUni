package de.transio.hiuni.core.notifications

import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.feature.email.data.EmailRepository
import de.transio.hiuni.feature.todos.data.TodoReminderScheduler
import de.transio.hiuni.feature.todos.data.TodosRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Ergebnis einer Notification-Aktion. */
sealed interface NotificationActionOutcome {
    /** Erledigt (oder nichts mehr zu tun, weil das Ziel schon weg ist). */
    data object Done : NotificationActionOutcome

    /** Fehlgeschlagen; [message] wird als kurze Ersatz-Notification angezeigt. */
    data class Failed(val message: String) : NotificationActionOutcome
}

/**
 * Führt eine [NotificationActionSpec] über die bestehenden Repository-Pfade aus:
 *
 *  - Mail: Ziel per IMAP-UID im lokalen Posteingang wiederfinden
 *    ([EmailRepository.findInboxMail]), dann [EmailRepository.markRead] bzw.
 *    [EmailRepository.archiveEmail] (IMAP-MOVE). Ist die Mail nicht mehr im
 *    Posteingang (schon archiviert, gelöscht, weggesynct), ist nichts zu tun.
 *  - Aufgabe: [TodosRepository.setDone] (cancelt den Reminder mit) bzw.
 *    [TodoReminderScheduler.snooze].
 *  - Termin: Snooze-Alarm über den [NotificationScheduler].
 *
 * Wirft nie: jede Exception wird geloggt und zu [NotificationActionOutcome.Failed].
 */
@Singleton
class NotificationActionHandler @Inject constructor(
    private val emailRepository: EmailRepository,
    private val settings: SettingsDataStore,
    private val todosRepository: TodosRepository,
    private val todoReminders: TodoReminderScheduler,
    private val scheduler: NotificationScheduler,
    private val clock: Clock
) {

    suspend fun handle(spec: NotificationActionSpec): NotificationActionOutcome = try {
        when (spec) {
            is NotificationActionSpec.MarkMailsRead -> markMailsRead(spec)
            is NotificationActionSpec.ArchiveMail -> archiveMail(spec)
            is NotificationActionSpec.CompleteTodo -> {
                todosRepository.setDone(spec.todoId, done = true, now = clock.instant())
                NotificationActionOutcome.Done
            }
            is NotificationActionSpec.SnoozeTodo -> {
                val until = NotificationActionPlanner.snoozeUntil(spec, clock.instant())!!
                val planned = todoReminders.snooze(spec.todoId, until)
                if (!planned) Timber.i("Später: Aufgabe %d erledigt oder gelöscht, kein Snooze", spec.todoId)
                NotificationActionOutcome.Done
            }
            is NotificationActionSpec.SnoozeEvent -> {
                scheduler.schedule(
                    eventId = spec.eventId,
                    title = spec.title,
                    triggerAt = NotificationActionPlanner.snoozeUntil(spec, clock.instant())!!,
                    kind = NotificationKind.EVENT,
                    body = spec.body,
                    snooze = true
                )
                NotificationActionOutcome.Done
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Timber.w(t, "Notification-Aktion %s fehlgeschlagen", spec::class.simpleName)
        NotificationActionOutcome.Failed(failureMessage(spec))
    }

    private suspend fun markMailsRead(spec: NotificationActionSpec.MarkMailsRead): NotificationActionOutcome {
        if (mailLocked()) return NotificationActionOutcome.Failed(MAIL_LOCKED)
        for (uid in spec.uids) {
            val mail = emailRepository.findInboxMail(uid) ?: continue
            emailRepository.markRead(mail.rowId, true)
        }
        return NotificationActionOutcome.Done
    }

    private suspend fun archiveMail(spec: NotificationActionSpec.ArchiveMail): NotificationActionOutcome {
        if (mailLocked()) return NotificationActionOutcome.Failed(MAIL_LOCKED)
        val mail = emailRepository.findInboxMail(spec.uid) ?: run {
            Timber.i("Archivieren: Mail uid=%d nicht mehr im Posteingang", spec.uid)
            return NotificationActionOutcome.Done
        }
        return when (val result = emailRepository.archiveEmail(mail)) {
            is AppResult.Success -> NotificationActionOutcome.Done
            is AppResult.Failure -> {
                Timber.w(result.error, "Archivieren aus der Notification fehlgeschlagen")
                NotificationActionOutcome.Failed(failureMessage(spec))
            }
        }
    }

    /**
     * Zweite Absicherung zur Planung in [NotificationActionPlanner.mailActions]: wurde
     * die Mail-Sperre erst nach dem Posten der Notification aktiviert, hängen dort noch
     * Buttons. Die führen dann nichts aus. Lässt sich die Einstellung nicht lesen,
     * gilt die Sperre als aktiv.
     */
    private suspend fun mailLocked(): Boolean =
        runCatching { settings.mailRequiresBiometric.first() }.getOrDefault(true)

    private fun failureMessage(spec: NotificationActionSpec): String = when (spec) {
        is NotificationActionSpec.MarkMailsRead -> "Als gelesen markieren fehlgeschlagen"
        is NotificationActionSpec.ArchiveMail -> "Archivieren fehlgeschlagen"
        is NotificationActionSpec.CompleteTodo -> "Aufgabe konnte nicht abgehakt werden"
        is NotificationActionSpec.SnoozeTodo,
        is NotificationActionSpec.SnoozeEvent -> "Erinnerung konnte nicht verschoben werden"
    }

    private companion object {
        const val MAIL_LOCKED = "Mail ist gesperrt, bitte in der App öffnen"
    }
}
