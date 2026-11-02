package de.transio.hiuni.core.notifications

import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.feature.bib.data.BibConfig
import de.transio.hiuni.feature.todos.data.TodoReminderPlanner
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Ein Aktions-Button an einer OS-Notification. Rein deklarativ (Android-frei):
 * der [NotificationPresenter] baut daraus `NotificationCompat.Action`s, der
 * [NotificationActionReceiver] führt sie über den [NotificationActionHandler] aus.
 *
 * Die Nutzlast ist jeweils der stabile Schlüssel, unter dem der Empfänger das Ziel
 * später wiederfindet: IMAP-UID im Posteingang (Mail), Aufgaben-ID (Todo),
 * Event-ID aus `custom_events` (Kalender) bzw. Raum, Tag und Zeitraum (Bib).
 */
sealed interface NotificationActionSpec {
    /** Kurzes deutsches Button-Label. */
    val label: String

    /**
     * `true`: Android führt die Aktion vom Sperrbildschirm erst nach dem Entsperren
     * aus (`setAuthenticationRequired`, wirksam ab Android 12).
     */
    val requiresUnlock: Boolean

    /** Neue Mail(s) als gelesen markieren. Eine UID: „Als gelesen", mehrere: „Alle als gelesen". */
    data class MarkMailsRead(val uids: List<Long>) : NotificationActionSpec {
        override val label: String get() = if (uids.size == 1) "Als gelesen" else "Alle als gelesen"
        override val requiresUnlock: Boolean get() = true
    }

    /** Eine einzelne neue Mail ins Archiv verschieben. */
    data class ArchiveMail(val uid: Long) : NotificationActionSpec {
        override val label: String get() = "Archivieren"
        override val requiresUnlock: Boolean get() = true
    }

    /** Aufgabe abhaken (cancelt über das Repository auch ihren Reminder). */
    data class CompleteTodo(val todoId: Long) : NotificationActionSpec {
        override val label: String get() = "Erledigt"
        override val requiresUnlock: Boolean get() = false
    }

    /** Aufgaben-Erinnerung später erneut zeigen. */
    data class SnoozeTodo(val todoId: Long) : NotificationActionSpec {
        override val label: String get() = "Später"
        override val requiresUnlock: Boolean get() = false
    }

    /**
     * Termin-Erinnerung später erneut zeigen. Titel/Text reisen mit, weil der
     * Snooze-Alarm dieselbe Meldung erneut anzeigt.
     */
    data class SnoozeEvent(val eventId: Long, val title: String, val body: String?) : NotificationActionSpec {
        override val label: String get() = "Später"
        override val requiresUnlock: Boolean get() = false
    }

    /**
     * Lieblings-Gruppenraum direkt aus der Frei-Meldung buchen. Eine echte Buchung
     * unter dem Uni-Konto, deshalb nur auf entsperrtem Gerät.
     */
    data class BookBibRoom(
        val roomId: Int,
        val date: LocalDate,
        val start: LocalTime,
        val end: LocalTime
    ) : NotificationActionSpec {
        override val label: String get() = "Buchen"
        override val requiresUnlock: Boolean get() = true
    }
}

/**
 * Reine Entscheidungslogik: welche Aktionen eine Notification bekommt, wie lange
 * „Später" verschiebt und wie die PendingIntent-Request-Codes aussehen.
 */
object NotificationActionPlanner {

    /**
     * Aufgaben haben nur ein Fälligkeitsdatum, erinnert wird um 09:00. Eine Stunde
     * reicht für „nach der Vorlesung nochmal" und bleibt sicher am selben Tag.
     */
    val TODO_SNOOZE: Duration = Duration.ofHours(1)

    /**
     * Termin-Reminder feuern typischerweise 10 bis 30 Minuten vor Beginn; zehn
     * Minuten später liegt die zweite Meldung meist noch vor dem Start.
     */
    val EVENT_SNOOZE: Duration = Duration.ofMinutes(10)

    /**
     * Aktionen der „Neue E-Mail"-Meldung.
     *
     * Bei aktiver biometrischer Mail-Sperre gibt es bewusst gar keine Aktionen: Die
     * Sperre gilt pro App-Sitzung und verlangt einen eigenen BiometricPrompt, auch
     * wenn das Gerät schon entsperrt ist. `setAuthenticationRequired` verlangt nur
     * das Entsperren des Geräts und würde die Sperre damit umgehen.
     */
    fun mailActions(newUnreadUids: List<Long>, mailLockEnabled: Boolean): List<NotificationActionSpec> {
        if (mailLockEnabled || newUnreadUids.isEmpty()) return emptyList()
        return if (newUnreadUids.size == 1) {
            val uid = newUnreadUids.single()
            listOf(NotificationActionSpec.MarkMailsRead(listOf(uid)), NotificationActionSpec.ArchiveMail(uid))
        } else {
            listOf(NotificationActionSpec.MarkMailsRead(newUnreadUids))
        }
    }

    /**
     * Aktionen eines über den [NotificationScheduler] gefeuerten Reminders.
     *
     *  - TODO: „Erledigt" und „Später"; die Aufgaben-ID steckt in der Reminder-ID
     *    (siehe [TodoReminderPlanner.todoIdFromReminderId]).
     *  - EVENT mit positiver ID (eigener Termin, Sport-/Kino-Pin): „Später".
     *  - alles andere (Klausur, Learnweb, synthetische IDs): keine Aktionen.
     */
    fun reminderActions(
        kind: NotificationKind,
        eventId: Long,
        title: String,
        body: String?
    ): List<NotificationActionSpec> = when (kind) {
        NotificationKind.TODO -> TodoReminderPlanner.todoIdFromReminderId(eventId)
            ?.let { listOf(NotificationActionSpec.CompleteTodo(it), NotificationActionSpec.SnoozeTodo(it)) }
            .orEmpty()
        NotificationKind.EVENT ->
            if (eventId > 0L && eventId <= Int.MAX_VALUE) {
                listOf(NotificationActionSpec.SnoozeEvent(eventId, title, body))
            } else {
                emptyList()
            }
        else -> emptyList()
    }

    /**
     * Zu buchender Zeitraum für einen freien Block `[blockStart, blockEnd)`: ab dem
     * ersten Slot-Beginn im Block (30-Minuten-Raster der Bib), Länge
     * `min(Wunschdauer, Blocklänge, 2 h)`, abgerundet auf ganze Slots. `null`, wenn
     * nicht einmal ein ganzer Slot hineinpasst.
     */
    fun bibBookingRange(
        blockStart: LocalTime,
        blockEnd: LocalTime,
        desiredMinutes: Int
    ): Pair<LocalTime, LocalTime>? {
        val slot = BibConfig.SLOT_MINUTES
        val startMinute = blockStart.toSecondOfDay().let { (it + slot * 60 - 1) / (slot * 60) } * slot
        val endMinute = blockEnd.toSecondOfDay() / 60
        val maxMinutes = BibConfig.MAX_SLOTS_PER_BOOKING * slot
        val length = minOf(desiredMinutes, endMinute - startMinute, maxMinutes) / slot * slot
        if (length < slot || startMinute + length > MINUTES_PER_DAY - 1) return null
        val start = LocalTime.of(startMinute / 60, startMinute % 60)
        return start to start.plusMinutes(length.toLong())
    }

    /**
     * Aktionen der „Lieblings-Raum ist frei"-Meldung: „Buchen" für den Zeitraum aus
     * [bibBookingRange], aber nur wenn
     *  - eine CAS-Session besteht ([authenticated]; anonym kann die Bib nicht buchen),
     *  - am Tag noch keine eigene Buchung existiert (Bib-Regel: eine Buchung pro Tag),
     *  - der Zeitraum noch in der Zukunft beginnt.
     * Sonst keine Aktion; ein Tap öffnet weiterhin den Bib-Screen.
     */
    fun bibFreeRoomActions(
        roomId: Int,
        date: LocalDate,
        blockStart: LocalTime,
        blockEnd: LocalTime,
        desiredMinutes: Int,
        authenticated: Boolean,
        hasOwnBookingOnDate: Boolean,
        now: LocalDateTime
    ): List<NotificationActionSpec> {
        if (!authenticated || hasOwnBookingOnDate) return emptyList()
        val (start, end) = bibBookingRange(blockStart, blockEnd, desiredMinutes) ?: return emptyList()
        if (!date.atTime(start).isAfter(now)) return emptyList()
        return listOf(NotificationActionSpec.BookBibRoom(roomId, date, start, end))
    }

    /** Neuer Trigger-Zeitpunkt für „Später", oder `null` für Nicht-Snooze-Aktionen. */
    fun snoozeUntil(spec: NotificationActionSpec, now: Instant): Instant? = when (spec) {
        is NotificationActionSpec.SnoozeTodo -> now.plus(TODO_SNOOZE)
        is NotificationActionSpec.SnoozeEvent -> now.plus(EVENT_SNOOZE)
        else -> null
    }

    /** Kategorie, unter der eine Fehlermeldung zur Aktion angezeigt wird. */
    fun kindOf(spec: NotificationActionSpec): NotificationKind = when (spec) {
        is NotificationActionSpec.MarkMailsRead, is NotificationActionSpec.ArchiveMail -> NotificationKind.MAIL
        is NotificationActionSpec.CompleteTodo, is NotificationActionSpec.SnoozeTodo -> NotificationKind.TODO
        is NotificationActionSpec.SnoozeEvent -> NotificationKind.EVENT
        is NotificationActionSpec.BookBibRoom -> NotificationKind.BIB
    }

    /** Laufende Nummer je Aktionstyp, Basis für Request-Code und Intent-URI. */
    fun typeIndex(spec: NotificationActionSpec): Int = when (spec) {
        is NotificationActionSpec.MarkMailsRead -> 0
        is NotificationActionSpec.ArchiveMail -> 1
        is NotificationActionSpec.CompleteTodo -> 2
        is NotificationActionSpec.SnoozeTodo -> 3
        is NotificationActionSpec.SnoozeEvent -> 4
        is NotificationActionSpec.BookBibRoom -> 5
    }

    /**
     * Request-Code des PendingIntents. Je Notification und Aktionstyp verschieden.
     * Kollisionen über Notifications hinweg sind trotzdem ausgeschlossen, weil der
     * Intent zusätzlich eine eindeutige Data-URI trägt (siehe [NotificationActionIntents]).
     */
    fun requestCode(systemId: Int, spec: NotificationActionSpec): Int =
        systemId * ACTION_TYPE_COUNT + typeIndex(spec)

    private const val ACTION_TYPE_COUNT = 8

    private const val MINUTES_PER_DAY = 24 * 60
}
