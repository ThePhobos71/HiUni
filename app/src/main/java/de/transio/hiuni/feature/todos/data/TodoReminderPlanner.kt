package de.transio.hiuni.feature.todos.data

import de.transio.hiuni.core.common.DateTimeFormats
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Reine Entscheidungslogik für Aufgaben-Erinnerungen (Android-frei, als JVM-Test
 * abgesichert). Der [TodoReminderScheduler] übersetzt die Ergebnisse in
 * AlarmManager-Aufrufe.
 *
 * ### Regel
 *
 * Eine Aufgabe hat nur ein Fälligkeits-DATUM (keine Uhrzeit). Erinnert wird
 * deshalb einmalig am Fälligkeitstag um [REMINDER_TIME] (09:00 Uhr), also früh
 * genug, um die Aufgabe am selben Tag noch zu erledigen. Die globale Einstellung
 * „Minuten vorher" aus den Kalender-Remindern passt ohne Uhrzeit nicht und wird
 * bewusst nicht verwendet.
 *
 * Kein Reminder, wenn
 *  - die Aufgabe erledigt ist,
 *  - kein Fälligkeitsdatum gesetzt ist,
 *  - der Trigger-Zeitpunkt nicht mehr in der Zukunft liegt (Datum vergangen
 *    oder heute schon nach 09:00).
 *
 * ### ID-Schema
 *
 * ```
 * reminderId = TODO_ID_BASE - todoId    (also negativ)
 * ```
 *
 * Der [de.transio.hiuni.core.notifications.NotificationScheduler] nutzt die ID
 * (als Int) sowohl als PendingIntent-Request-Code als auch als System-ID der
 * Notification. Die belegten Räume sind:
 *  - Kalender-Events: positiv, klein (Room AUTOINCREMENT)
 *  - Klausur-Reminder: ab 1_000_000_000, Learnweb-Reminder: ab 2_000_000_000
 *  - Noten-/Kurs-Pushes: zwischen ca. -470_000_000 und -100_000_000
 *  - Test-Mitteilung: -1
 *
 * Aufgaben liegen ab -1_000_000_001 abwärts und sind damit von allen anderen
 * Räumen getrennt. IDs, die unter `Int.MIN_VALUE` fallen würden, liefern `null`
 * (Skip statt stiller Kollision).
 */
object TodoReminderPlanner {

    /** Uhrzeit der Erinnerung am Fälligkeitstag. */
    val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

    /** Basis des (negativen) ID-Raums für Aufgaben-Reminder. */
    const val TODO_ID_BASE = -1_000_000_000L

    /**
     * Zeitpunkt, zu dem für [todo] erinnert werden soll, oder `null`, wenn kein
     * Reminder (mehr) fällig ist.
     */
    fun triggerAt(todo: TodoEntity, now: Instant, zone: ZoneId): Instant? {
        if (todo.isDone) return null
        val due = todo.dueDate ?: return null
        val trigger = due.atTime(REMINDER_TIME).atZone(zone).toInstant()
        return trigger.takeIf { it.isAfter(now) }
    }

    /**
     * Reminder-/Notification-ID für eine Aufgabe, oder `null`, wenn die ID nicht
     * in den Int-Bereich passt bzw. die Aufgabe noch nicht persistiert ist (id 0).
     */
    fun reminderId(todoId: Long): Long? {
        if (todoId <= 0L) return null
        val id = TODO_ID_BASE - todoId
        return id.takeIf { it >= Int.MIN_VALUE }
    }

    /** Überschrift der Notification, z.B. "Heute fällig: Übungsblatt 3". */
    fun title(todo: TodoEntity): String = "Heute fällig: ${todo.title}"

    /** Body-Zeile, z.B. "Offene Aufgabe · Mo 27. Okt". */
    fun body(todo: TodoEntity): String {
        val datePart = todo.dueDate?.format(DateTimeFormats.dayShortNoComma)
        return listOfNotNull("Offene Aufgabe", datePart).joinToString(" · ")
    }
}
