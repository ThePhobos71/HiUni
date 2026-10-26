package de.transio.hiuni.core.sync

import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.core.notifications.lecture.LiveLectureScheduler
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.learnweb.data.LearnwebAssignmentDao
import de.transio.hiuni.feature.lsf.data.ExamDao
import de.transio.hiuni.feature.todos.data.TodoReminderScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Zentraler Einstieg, der ALLE lokalen Reminder-Alarme aus dem DB-Bestand neu plant.
 *
 * ### Warum
 *
 * Android verwirft beim Reboot sämtliche AlarmManager-Alarme, nach einem App-Update
 * oder Force-Stop sind die Alarme der App ebenfalls weg. Die einzelnen Scheduler
 * planen aber nur beim Anlegen/Sync. Ohne dieses Netz kämen Reminder erst beim
 * nächsten Sync (Klausuren, Learnweb) oder gar nicht mehr (einmalige Termine).
 *
 * ### Aufrufer
 *
 *  - [de.transio.hiuni.HiUniApplication.onCreate]: bei jedem Prozessstart.
 *  - [de.transio.hiuni.core.notifications.ReminderRestoreReceiver]: nach
 *    `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, Zeit-/Zonenwechsel und erteilter
 *    Exakt-Alarm-Berechtigung.
 *
 * ### Reminder-Arten
 *
 *  1. Einmalige Kalender-Termine mit Reminder (eigene Termine, Sport-Pins,
 *     Mail-Einladungen, im Kalender bearbeitete Termine). Learnweb-Spiegelungen
 *     sind ausgenommen, siehe [MIRROR_SOURCES_WITHOUT_ALARM].
 *  2. Wiederkehrende Termine: [RecurringReminderRescheduler.rescheduleAll].
 *  3. Klausur-Reminder: [ExamReminderScheduler.syncReminders] gegen den
 *     kompletten Klausur-Bestand (ohne "Neue Klausur"-Push).
 *  4. Learnweb-Abgabe-Reminder: [LearnwebAssignmentReminderScheduler.syncReminders].
 *  5. Aufgaben-Reminder: [TodoReminderScheduler.rescheduleAll].
 *  6. Laufende-Vorlesung-Mitteilung: [LiveLectureScheduler.replan] zeigt eine gerade
 *     laufende Vorlesung sofort wieder an und stellt den Weck-Alarm zum nächsten Beginn.
 *
 * ### Garantien
 *
 *  - **Idempotent:** Jede Reminder-ID landet im selben PendingIntent-Slot
 *    (`FLAG_UPDATE_CURRENT`), erneutes Planen überschreibt statt zu duplizieren.
 *    Läufe werden zusätzlich per [Mutex] serialisiert (beim Boot laufen
 *    `onCreate` und Receiver praktisch gleichzeitig).
 *  - **Nichts Vergangenes:** Es werden nur Trigger in der Zukunft geplant, bereits
 *    gefeuerte Reminder kommen nicht noch einmal.
 *  - **Fehler-isoliert:** Eine kaputte Quelle stoppt die anderen Arten nicht.
 */
@Singleton
class ReminderRestorer @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val scheduler: NotificationScheduler,
    private val recurringReminderRescheduler: RecurringReminderRescheduler,
    private val examDao: ExamDao,
    private val examReminderScheduler: ExamReminderScheduler,
    private val learnwebAssignmentDao: LearnwebAssignmentDao,
    private val learnwebReminderScheduler: LearnwebAssignmentReminderScheduler,
    private val todoReminderScheduler: TodoReminderScheduler,
    private val liveLectureScheduler: LiveLectureScheduler
) {

    private val mutex = Mutex()

    /** Plant alle Reminder-Arten neu. Sicher mehrfach aufrufbar. */
    suspend fun restoreAll(now: Instant = Instant.now()) = mutex.withLock {
        val singleShot = step("Einzeltermine") { restoreSingleShotEvents(now) }
        step("Wiederkehrende Termine") { recurringReminderRescheduler.rescheduleAll(now) }
        step("Klausuren") {
            examReminderScheduler.syncReminders(allExams = examDao.findAll(), newlyAdded = emptyList())
        }
        step("Learnweb") {
            learnwebReminderScheduler.syncReminders(learnwebAssignmentDao.findUpcoming(now.toEpochMilli()))
        }
        step("Aufgaben") { todoReminderScheduler.rescheduleAll() }
        step("Laufende Vorlesung") { liveLectureScheduler.replan(now) }
        Timber.i("ReminderRestorer: Reminder neu geplant (%d Einzeltermine)", singleShot ?: 0)
    }

    /**
     * Einmalige Termine: Reminder = Start minus `reminderMinutesBefore`. Rückgabe:
     * Anzahl geplanter Alarme.
     */
    private suspend fun restoreSingleShotEvents(now: Instant): Int {
        var scheduled = 0
        for (event in calendarRepository.upcomingSingleShotWithReminder(now)) {
            // Wiederkehrende Master übernimmt der RecurringReminderRescheduler (nächste
            // Occurrence statt Master-Start).
            if (!event.recurrenceRule.isNullOrBlank()) continue
            if (event.sourceKind in MIRROR_SOURCES_WITHOUT_ALARM) continue
            val minutes = event.reminderMinutesBefore ?: continue
            val triggerAt = event.startTime.minus(Duration.ofMinutes(minutes.toLong()))
            if (!triggerAt.isAfter(now)) continue
            scheduler.schedule(event.id, event.title, triggerAt)
            scheduled++
        }
        return scheduled
    }

    /**
     * Führt einen Teilschritt fehler-isoliert aus. Abbruch (Timeout im Receiver)
     * wird durchgereicht statt verschluckt.
     */
    private suspend fun <T> step(name: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Timber.w(t, "ReminderRestorer: %s konnten nicht neu geplant werden", name)
        null
    }

    companion object {
        /**
         * Kalender-Spiegelungen, deren `reminderMinutesBefore` nur ein übernommener
         * Default ist und für die nie ein Termin-Alarm geplant wurde. Die
         * Abgabe-Erinnerungen dazu laufen über den
         * [LearnwebAssignmentReminderScheduler]; ein zusätzlicher Termin-Alarm wäre
         * eine doppelte Benachrichtigung für dieselbe Deadline.
         */
        val MIRROR_SOURCES_WITHOUT_ALARM: Set<String> = setOf(
            CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT,
            CustomEventEntity.SOURCE_LEARNWEB_ICAL
        )
    }
}
