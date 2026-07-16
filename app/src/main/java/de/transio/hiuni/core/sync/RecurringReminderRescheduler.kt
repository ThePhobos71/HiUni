package de.transio.hiuni.core.sync

import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.calendar.data.RecurrenceExpander
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hält die Reminder wiederkehrender Kalender-Events lebendig.
 *
 * ### Problem
 *
 * Ein Recurring-Event (z. B. "jeden Mittwoch 10:00") existiert in der DB als EIN
 * Master mit [CustomEventEntity.recurrenceRule]; die einzelnen Termine werden nur
 * in-memory expandiert ([RecurrenceExpander]), NICHT persistiert. Der
 * [NotificationScheduler] kann pro `eventId` immer nur EINEN AlarmManager-Alarm
 * halten. Beim Anlegen plant das CalendarViewModel den Reminder für die nächste
 * Occurrence — aber nach dem Feuern gibt es keinen Alarm mehr für die übernächste.
 * Ohne dieses Re-Scheduling feuert der Reminder also genau einmal.
 *
 * ### Lösung
 *
 * - [rescheduleAfterFire]: Wird vom [de.transio.hiuni.core.notifications.NotificationReceiver]
 *   direkt nach dem Feuern gerufen. Berechnet die FOLGE-Occurrence (strikt nach der
 *   gerade gefeuerten) und plant deren Reminder ein.
 * - [rescheduleAll]: Sicherheitsnetz beim App-Start. AlarmManager verliert exakte
 *   Alarme bei Reboot / Force-Stop / aggressivem Doze. Beim Start planen wir für
 *   jeden Recurring-Master mit Reminder den nächsten fälligen Reminder erneut ein
 *   (idempotent dank `FLAG_UPDATE_CURRENT` im Scheduler).
 *
 * Es werden KEINE Occurrences persistiert — alles wird on-the-fly aus der Regel
 * gerechnet.
 */
@Singleton
class RecurringReminderRescheduler @Inject constructor(
    private val repository: CalendarRepository,
    private val scheduler: NotificationScheduler
) {

    /**
     * Zeitzone für die Occurrence-Berechnung. `var` nur, damit Unit-Tests eine feste
     * Zone (Europe/Berlin) setzen können, ohne von der CI-Zone (meist UTC) abhängig
     * zu sein. In Produktion immer die System-Default-Zone.
     */
    internal var zone: ZoneId = ZoneId.systemDefault()

    /**
     * Plant nach dem Feuern eines Recurring-Reminders den Reminder für die nächste
     * Occurrence ein.
     *
     * @param eventId Master-id (== `EXTRA_EVENT_ID` des gefeuerten Alarms).
     * @param firedAt Zeitpunkt des Feuerns (praktisch: der Trigger-Zeitpunkt, also
     *   `occStart - reminderMinutes`). Wir rekonstruieren daraus die gefeuerte
     *   Occurrence und suchen strikt danach die nächste.
     */
    suspend fun rescheduleAfterFire(eventId: Long, firedAt: Instant) {
        val master = repository.findById(eventId) ?: return
        // Nur wiederkehrende Events mit Reminder brauchen ein Re-Scheduling. Single-shot
        // feuert per Definition genau einmal, das ist korrekt.
        if (master.recurrenceRule.isNullOrBlank()) return
        val minutes = master.reminderMinutesBefore ?: return

        // Der Reminder feuert `minutes` VOR der Occurrence, die Occurrence liegt zur
        // Feuer-Zeit also noch minimal in der Zukunft. Wir rekonstruieren ihren Start
        // (firedAt + minutes) und suchen die erste Occurrence STRIKT danach — sonst
        // würden wir dieselbe Occurrence erneut planen.
        val firedOccurrenceStart = firedAt.plus(Duration.ofMinutes(minutes.toLong()))
        scheduleNextAfter(master, minutes, after = firedOccurrenceStart)
    }

    /**
     * App-Start-Sicherheitsnetz: für jeden wiederkehrenden Event mit Reminder den
     * jetzt fälligen (nächsten zukünftigen) Reminder erneut planen. Robust gegen
     * Reboot/Force-Stop, wo AlarmManager exakte Alarme verworfen hat.
     */
    suspend fun rescheduleAll(now: Instant = Instant.now()) {
        val masters = runCatching { repository.recurringMastersWithReminder() }
            .getOrElse {
                Timber.w(it, "RecurringReminderRescheduler: Master-Query fehlgeschlagen")
                return
            }
        var scheduled = 0
        for (master in masters) {
            val minutes = master.reminderMinutesBefore ?: continue
            // Nächste Occurrence ≥ now als Referenz (inklusiv) — wir wollen die
            // unmittelbar bevorstehende Occurrence, nicht die übernächste.
            if (scheduleNextInclusive(master, minutes, from = now)) scheduled++
        }
        if (scheduled > 0) {
            Timber.d("RecurringReminderRescheduler: %d Recurring-Reminder (re)geplant", scheduled)
        }
    }

    /** Plant den Reminder für die erste Occurrence mit Start > [after]. */
    private fun scheduleNextAfter(master: CustomEventEntity, minutes: Int, after: Instant) {
        val nextStart = RecurrenceExpander
            .firstOccurrenceStartStrictlyAfter(master, after, zone) ?: run {
                Timber.d("RecurringReminderRescheduler: keine Folge-Occurrence für id=%d", master.id)
                return
            }
        scheduleFor(master, minutes, nextStart)
    }

    /** Plant den Reminder für die erste Occurrence mit Start ≥ [from]. Rückgabe: geplant? */
    private fun scheduleNextInclusive(master: CustomEventEntity, minutes: Int, from: Instant): Boolean {
        val nextStart = RecurrenceExpander.nextOccurrenceAfter(master, from, zone) ?: return false
        return scheduleFor(master, minutes, nextStart)
    }

    private fun scheduleFor(master: CustomEventEntity, minutes: Int, occurrenceStart: Instant): Boolean {
        val triggerAt = occurrenceStart.minus(Duration.ofMinutes(minutes.toLong()))
        // Der Scheduler skippt Vergangenheits-Trigger selbst; wir prüfen trotzdem, um
        // ehrlich `false` (nichts geplant) zurückzugeben.
        if (!triggerAt.isAfter(Instant.now())) return false
        scheduler.schedule(master.id, master.title, triggerAt)
        return true
    }
}
