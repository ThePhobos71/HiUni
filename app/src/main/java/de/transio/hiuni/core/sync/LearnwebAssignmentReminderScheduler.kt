package de.transio.hiuni.core.sync

import de.transio.hiuni.core.common.DateTimeFormats
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.feature.learnweb.data.LearnwebAssignment
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plant lokale Reminder für Learnweb-Assignment-Deadlines ein. Wird vom
 * [de.transio.hiuni.feature.learnweb.data.LearnwebRepository] am Ende jedes
 * Sync-Laufs aufgerufen.
 *
 * ### Reminder-Slots
 *
 *  - Slot 0 = 3 Tage vorher, 18:00
 *  - Slot 1 = 1 Tag vorher, 18:00
 *  - Slot 2 = am Tag der Abgabe, 2h vor `dueEpoch` (oder direkt jetzt, falls
 *    `dueEpoch - 2h` bereits in der Vergangenheit liegt — dann skippt der
 *    Scheduler den Eintrag automatisch).
 *
 * Wir schedulen nur Reminder für Assignments, deren Abgabe in den nächsten
 * 14 Tagen liegt — alles drüber ist „zu weit weg" und wird beim nächsten Sync
 * (oder bei Annäherung an die Deadline) nachgereicht.
 *
 * ### ID-Schema
 *
 * ```
 * reminderId = LEARNWEB_ID_OFFSET + rowId * 10 + slot
 * ```
 *
 * `LEARNWEB_ID_OFFSET` ist `2_000_000_000L`, damit es nicht mit
 * Custom-Events (positiv, klein) und Exam-Reminders (Offset `1_000_000_000L`)
 * kollidiert.
 */
@Singleton
class LearnwebAssignmentReminderScheduler @Inject constructor(
    private val scheduler: NotificationScheduler,
    private val settings: SettingsDataStore
) {

    /**
     * Synct die Reminder-Soll-Menge mit dem AlarmManager. `allAssignments` ist
     * der Snapshot nach dem Sync (deduplikiert, vollständig). Verwaiste IDs
     * (alte Sollmenge minus neue) werden gecancelt.
     */
    suspend fun syncReminders(allAssignments: List<LearnwebAssignment>) {
        val now = Instant.now()
        val nowMillis = now.toEpochMilli()
        val cutoffMillis = nowMillis + WINDOW_DAYS * 24L * 60 * 60 * 1000
        val zone = ZoneId.systemDefault()
        val newScheduledIds = mutableSetOf<Long>()

        for (a in allAssignments) {
            if (a.dueEpoch <= nowMillis) continue
            if (a.dueEpoch > cutoffMillis) continue

            val due = Instant.ofEpochMilli(a.dueEpoch)

            // Slot 0: 3 Tage vorher, 18:00
            val threeDaysBefore = due.atZone(zone)
                .minusDays(3)
                .withHour(REMINDER_HOUR)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant()
            if (threeDaysBefore.isAfter(now)) {
                val id = reminderId(a.rowId, SLOT_3_DAYS)
                scheduler.schedule(
                    eventId = id,
                    title = formatTitle(a, SLOT_3_DAYS),
                    triggerAt = threeDaysBefore,
                    kind = NotificationKind.EVENT,
                    body = formatBody(a, SLOT_3_DAYS)
                )
                newScheduledIds += id
            }

            // Slot 1: 1 Tag vorher, 18:00
            val oneDayBefore = due.atZone(zone)
                .minusDays(1)
                .withHour(REMINDER_HOUR)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant()
            if (oneDayBefore.isAfter(now)) {
                val id = reminderId(a.rowId, SLOT_1_DAY)
                scheduler.schedule(
                    eventId = id,
                    title = formatTitle(a, SLOT_1_DAY),
                    triggerAt = oneDayBefore,
                    kind = NotificationKind.EVENT,
                    body = formatBody(a, SLOT_1_DAY)
                )
                newScheduledIds += id
            }

            // Slot 2: 2h vorher (Last-Minute-Push)
            val twoHoursBefore = due.minusSeconds(2L * 60 * 60)
            if (twoHoursBefore.isAfter(now)) {
                val id = reminderId(a.rowId, SLOT_2_HOURS)
                scheduler.schedule(
                    eventId = id,
                    title = formatTitle(a, SLOT_2_HOURS),
                    triggerAt = twoHoursBefore,
                    kind = NotificationKind.EVENT,
                    body = formatBody(a, SLOT_2_HOURS)
                )
                newScheduledIds += id
            }
        }

        // Diff gegen den persistierten letzten Stand → verwaiste IDs canceln.
        val previousIds = runCatching { settings.scheduledLearnwebReminderIds.first() }
            .getOrElse { emptySet() }
        val toCancel = previousIds - newScheduledIds
        for (id in toCancel) {
            scheduler.cancel(id)
        }
        if (toCancel.isNotEmpty()) {
            Timber.d("LearnwebAssignmentReminderScheduler: canceled ${toCancel.size} stale reminder(s)")
        }

        runCatching { settings.setScheduledLearnwebReminderIds(newScheduledIds) }
            .onFailure {
                Timber.w(
                    it,
                    "LearnwebAssignmentReminderScheduler: konnte Reminder-IDs nicht persistieren"
                )
            }

        Timber.i(
            "LearnwebAssignmentReminderScheduler: ${newScheduledIds.size} aktiv, " +
                "${toCancel.size} gecancelt"
        )
    }

    private fun formatTitle(assignment: LearnwebAssignment, slot: Int): String {
        val cleaned = assignment.title.removeSuffix(" ist fällig.").trim()
        val prefix = when (slot) {
            SLOT_3_DAYS -> "Abgabe in 3 Tagen"
            SLOT_1_DAY -> "Abgabe morgen"
            SLOT_2_HOURS -> "Abgabe in 2 Stunden"
            else -> "Abgabe"
        }
        return "$prefix: $cleaned"
    }

    private fun formatBody(assignment: LearnwebAssignment, slot: Int): String {
        val zone = ZoneId.systemDefault()
        val due = Instant.ofEpochMilli(assignment.dueEpoch).atZone(zone)
        val datePart = LONG_DATE.format(due)
        val timePart = TIME_FORMAT.format(due)
        val leading = when (slot) {
            SLOT_3_DAYS -> "in 3 Tagen"
            SLOT_1_DAY -> "morgen"
            SLOT_2_HOURS -> "in 2 Stunden"
            else -> ""
        }
        return listOfNotNull(
            leading.ifBlank { null },
            "$datePart, $timePart Uhr"
        ).joinToString(" · ")
    }

    private fun reminderId(rowId: Long, slot: Int): Long =
        LEARNWEB_ID_OFFSET + rowId * 10 + slot

    companion object {
        /**
         * Trennt Learnweb-Reminder-IDs vom Exam-Reminder-ID-Space (1e9) und
         * Custom-Event-ID-Space (positiv, klein). Bei rowId ≤ ~10^8 bleibt das
         * sicher überschneidungsfrei.
         */
        const val LEARNWEB_ID_OFFSET = 2_000_000_000L

        const val SLOT_3_DAYS = 0
        const val SLOT_1_DAY = 1
        const val SLOT_2_HOURS = 2

        /** Reminder am Tag selbst (3d/1d-Slots) feuern um 18:00. */
        private const val REMINDER_HOUR = 18

        /** Nur Assignments innerhalb dieses Fensters bekommen Reminder. */
        private const val WINDOW_DAYS = 14L

        private val LONG_DATE: DateTimeFormatter = DateTimeFormats.dayShortNoComma
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormats.time24
    }
}
