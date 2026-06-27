package de.transio.hiuni.core.sync

import de.transio.hiuni.core.common.DateTimeFormats
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationPresenter
import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.feature.lsf.data.ExamEntity
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plant lokale Reminder für LSF-Klausurtermine ein und feuert eine sofortige
 * "Neue Klausur eingetragen"-Notification fürs Push-Center, sobald der
 * [de.transio.hiuni.feature.lsf.data.LsfExamsRepository]-Sync neue Einträge
 * erkennt.
 *
 * ### Reminder-Slots
 *
 *  - Slot 0 = 7-Tage-vorher, 12:00 mittags.
 *  - Slot 1 = 1-Tag-vorher, 18:00 abends.
 *
 * Beide werden über [NotificationScheduler.schedule] mit
 * `kind = NotificationKind.EXAM` geplant — damit landet das Reminder-Event über
 * den [NotificationReceiver]/[NotificationPresenter]-Pfad sowohl im Push-Center
 * als auch (sofern Permission erteilt) als OS-Notification.
 *
 * ### ID-Schema
 *
 * Klausur-Reminder-IDs werden deterministisch aus der Room-`rowId` gebaut, mit
 * einem festen Offset, damit sie nicht mit Custom-Event-IDs kollidieren:
 *
 * ```
 * reminderId = EXAM_ID_OFFSET + rowId * 10 + slot
 * ```
 *
 * Praktisch:
 *  - Custom-Event-IDs sind positiv und bleiben weit unter 10^9 (Room
 *    AUTOINCREMENT für ein Event-Volumen, das im Hobby-Use realistisch ist).
 *  - Klausur-Slots starten erst bei 1_000_000_000 → kein Overlap.
 *  - PendingIntent identifiziert sich über `eventId.toInt()`. Bei rowId ≤ ~2.1e8
 *    bleibt das auch nach `* 10 + slot` im positiven Int-Bereich; das reicht
 *    locker für >> alle Klausuren die ein Studi je sehen wird.
 *
 * ### Cancel-Logik
 *
 * AlarmManager hat keine "list all"-API. Wir persistieren die aktuell aktive
 * Set<Long> an Reminder-IDs in [SettingsDataStore.scheduledExamReminderIds]
 * und canceln beim nächsten Sync genau die Differenz (alt − neu).
 */
@Singleton
class ExamReminderScheduler @Inject constructor(
    private val scheduler: NotificationScheduler,
    private val presenter: NotificationPresenter,
    private val settings: SettingsDataStore
) {

    /**
     * Wird vom [de.transio.hiuni.feature.lsf.data.LsfExamsRepository] am Ende
     * jedes Sync-Laufs gerufen. `allExams` ist die finale Soll-Liste (alle
     * Einträge im Semester nach Upsert + Prune), `newlyAdded` nur die in diesem
     * Lauf neu erkannten Einträge (Diff via `veranstaltungsNumber + semesterCode`).
     */
    suspend fun syncReminders(
        allExams: List<ExamEntity>,
        newlyAdded: List<ExamEntity>
    ) {
        // 1) Neue Reminder-Sollmenge berechnen. Wir schedulen nur Slots, deren
        //    Trigger-Zeitpunkt noch in der Zukunft liegt — der Scheduler würde
        //    das zwar selbst loggen + skippen, aber wir wollen die ID auch
        //    nicht im persistierten "aktiv"-Set führen, sonst würden wir sie
        //    beim nächsten Sync sinnlos canceln.
        val now = java.time.Instant.now()
        val zone = ZoneId.systemDefault()
        val newScheduledIds = mutableSetOf<Long>()

        for (exam in allExams) {
            val date = exam.examDate ?: continue
            val body = formatBody(exam, slot = SLOT_7_DAYS)
            val sevenDaysTrigger = date.minusDays(7)
                .atTime(REMINDER_HOUR_7D, 0)
                .atZone(zone)
                .toInstant()
            if (sevenDaysTrigger.isAfter(now)) {
                val id = reminderId(exam.rowId, SLOT_7_DAYS)
                scheduler.schedule(
                    eventId = id,
                    title = formatTitle(exam, slot = SLOT_7_DAYS),
                    triggerAt = sevenDaysTrigger,
                    kind = NotificationKind.EXAM,
                    body = body
                )
                newScheduledIds += id
            }

            val oneDayTrigger = date.minusDays(1)
                .atTime(REMINDER_HOUR_1D, 0)
                .atZone(zone)
                .toInstant()
            if (oneDayTrigger.isAfter(now)) {
                val id = reminderId(exam.rowId, SLOT_1_DAY)
                scheduler.schedule(
                    eventId = id,
                    title = formatTitle(exam, slot = SLOT_1_DAY),
                    triggerAt = oneDayTrigger,
                    kind = NotificationKind.EXAM,
                    body = formatBody(exam, slot = SLOT_1_DAY)
                )
                newScheduledIds += id
            }
        }

        // 2) Diff gegen den persistierten letzten Stand → verwaiste IDs canceln.
        //    Quellen für Verwaisung: LSF hat die Klausur zurückgezogen, Datum
        //    wurde gelöscht (examDate ist nun null), oder das Datum ist bereits
        //    vergangen (Reminder war eh sinnlos).
        val previousIds = runCatching { settings.scheduledExamReminderIds.first() }
            .getOrElse { emptySet() }
        val toCancel = previousIds - newScheduledIds
        for (id in toCancel) {
            scheduler.cancel(id)
        }
        if (toCancel.isNotEmpty()) {
            Timber.d("ExamReminderScheduler: canceled ${toCancel.size} stale reminder(s)")
        }

        // 3) Persistieren — nächster Sync diffed dagegen.
        runCatching { settings.setScheduledExamReminderIds(newScheduledIds) }
            .onFailure { Timber.w(it, "ExamReminderScheduler: konnte Reminder-IDs nicht persistieren") }

        // 4) "Neue Klausur eingetragen"-Push für jeden frisch erkannten Eintrag
        //    mit terminierter Datumsangabe. Ohne Datum ist die News meh ("Termin
        //    noch offen") und wir lassen sie weg, damit der User nicht für jede
        //    POS-Anmeldung beim Login eine Notif bekommt.
        for (exam in newlyAdded) {
            if (exam.examDate == null) continue
            val systemId = reminderId(exam.rowId, SLOT_NEW_EXAM_PUSH).toInt()
            runCatching {
                presenter.present(
                    kind = NotificationKind.EXAM,
                    title = "Neue Klausur eingetragen",
                    body = formatNewExamBody(exam),
                    refKey = "exam:${exam.rowId}",
                    systemId = systemId
                )
            }.onFailure { Timber.w(it, "ExamReminderScheduler: Neue-Klausur-Push fehlgeschlagen") }
        }

        Timber.i(
            "ExamReminderScheduler: ${newScheduledIds.size} Reminder aktiv, " +
                "${toCancel.size} verwaiste gecancelt, " +
                "${newlyAdded.count { it.examDate != null }} Neu-Pushs ausgelöst"
        )
    }

    /** Sichtbare Headline. "Morgen Klausur: …" bzw. "Klausur in 7 Tagen: …". */
    private fun formatTitle(exam: ExamEntity, slot: Int): String {
        val module = exam.moduleName.ifBlank { exam.pruefungstext }
        val date = exam.examDate
        val datePart = date?.format(SHORT_DATE) ?: ""
        val room = exam.rooms.firstOrNull().orEmpty()
        val tail = listOfNotNull(
            datePart.ifBlank { null },
            room.ifBlank { null }
        ).joinToString(" · ")
        val prefix = when (slot) {
            SLOT_7_DAYS -> "Klausur in 7 Tagen"
            SLOT_1_DAY -> "Morgen Klausur"
            else -> "Klausur"
        }
        return if (tail.isBlank()) "$prefix: $module" else "$prefix: $module · $tail"
    }

    /**
     * Body-Format: `in 7 Tagen · Di 21. Jul, 10:00 · SC.A.0.09`.
     * Bei fehlender Time/Räume fallen die Felder weg.
     */
    private fun formatBody(exam: ExamEntity, slot: Int): String {
        val leading = when (slot) {
            SLOT_7_DAYS -> "in 7 Tagen"
            SLOT_1_DAY -> "morgen"
            else -> ""
        }
        val date = exam.examDate
        val datePart = date?.let { LONG_DATE.format(it) }
        val timePart = exam.examTime?.format(SHORT_TIME)
        val dateTimePart = listOfNotNull(datePart, timePart).joinToString(", ").ifBlank { null }
        val roomPart = exam.rooms.joinToString(", ").ifBlank { null }
        return listOfNotNull(
            leading.ifBlank { null },
            dateTimePart,
            roomPart
        ).joinToString(" · ")
    }

    /** "DBS3 · Di 21. Jul · SC.A.0.09" — knapper als der Reminder-Body. */
    private fun formatNewExamBody(exam: ExamEntity): String {
        val module = exam.moduleName.ifBlank { exam.pruefungstext }
        val datePart = exam.examDate?.let { LONG_DATE.format(it) }
        val roomPart = exam.rooms.firstOrNull()
        return listOfNotNull(
            module.ifBlank { null },
            datePart,
            roomPart
        ).joinToString(" · ")
    }

    private fun reminderId(rowId: Long, slot: Int): Long =
        EXAM_ID_OFFSET + rowId * 10 + slot

    companion object {
        /**
         * Trennt Klausur-Reminder-IDs vom Custom-Event-ID-Space. Custom-Events
         * sind positive AUTOINCREMENT-Longs, in der Praxis weit unter 10^9.
         * Klausur-IDs starten bei 10^9 und bleiben damit überschneidungsfrei.
         */
        const val EXAM_ID_OFFSET = 1_000_000_000L

        /** Slot-Offsets innerhalb eines Exam-Row-Buckets von 10 Slots. */
        const val SLOT_7_DAYS = 0
        const val SLOT_1_DAY = 1
        /** Nur für die sofortige "Neue Klausur eingetragen"-Push-System-ID — kein Alarm. */
        const val SLOT_NEW_EXAM_PUSH = 2

        private const val REMINDER_HOUR_7D = 12 // 12:00 mittags
        private const val REMINDER_HOUR_1D = 18 // 18:00 abends

        /** "Di 21. Jul" — kurz aber mit Wochentag, damit's auf einen Blick passt. */
        private val LONG_DATE: DateTimeFormatter = DateTimeFormats.dayShortNoComma
        /** "21.07." für die Title-Zeile, kompakt. */
        private val SHORT_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN)
        /** "10:00". */
        private val SHORT_TIME: DateTimeFormatter = DateTimeFormats.time24
    }
}
