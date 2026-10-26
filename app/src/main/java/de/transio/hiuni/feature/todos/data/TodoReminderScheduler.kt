package de.transio.hiuni.feature.todos.data

import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.core.notifications.data.NotificationKind
import timber.log.Timber
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plant die lokale Erinnerung einer Aufgabe über den [NotificationScheduler]
 * (AlarmManager). Die Regel (wann, welche ID) steckt in [TodoReminderPlanner].
 *
 * Aufgerufen wird ausschließlich aus dem [TodosRepositoryImpl], damit jeder
 * Schreibpfad (Aufgaben-Screen, Home-Vorschau, Home-Screen-Widget) automatisch
 * mitzieht:
 *  - Anlegen/Bearbeiten/Wieder-Öffnen → [sync] plant (neu) ein,
 *  - Abhaken → [sync] cancelt (Planner liefert für erledigte Aufgaben nichts),
 *  - Löschen → [cancel].
 *
 * [rescheduleAll] ist das Sicherheitsnetz beim App-Start (wie beim
 * [de.transio.hiuni.core.sync.RecurringReminderRescheduler]): AlarmManager
 * verwirft Alarme bei Reboot/Force-Stop, beim nächsten Prozessstart werden alle
 * offenen Aufgaben mit künftigem Reminder idempotent neu geplant.
 *
 * Die Kategorie-Einstellung (Aufgaben an/aus) greift beim Feuern im
 * [de.transio.hiuni.core.notifications.NotificationPresenter].
 */
@Singleton
class TodoReminderScheduler @Inject constructor(
    private val scheduler: NotificationScheduler,
    private val dao: TodoDao
) {

    /** Uhr/Zone nur für Unit-Tests überschreibbar; in Produktion die Systemuhr. */
    internal var clock: Clock = Clock.systemDefaultZone()
    private val zone: ZoneId get() = clock.zone

    /**
     * Bringt den Alarm einer persistierten Aufgabe auf den aktuellen Stand:
     * vorhandenen Alarm canceln, dann (falls noch sinnvoll) neu planen.
     * Rückgabe: wurde ein Reminder geplant?
     */
    fun sync(todo: TodoEntity): Boolean {
        val id = TodoReminderPlanner.reminderId(todo.id) ?: return false
        scheduler.cancel(id)
        val triggerAt = TodoReminderPlanner.triggerAt(todo, clock.instant(), zone) ?: return false
        scheduler.schedule(
            eventId = id,
            title = TodoReminderPlanner.title(todo),
            triggerAt = triggerAt,
            kind = NotificationKind.TODO,
            body = TodoReminderPlanner.body(todo)
        )
        return true
    }

    /** Entfernt den Alarm einer Aufgabe (z.B. vor dem Löschen). */
    fun cancel(todoId: Long) {
        val id = TodoReminderPlanner.reminderId(todoId) ?: return
        scheduler.cancel(id)
    }

    /** App-Start-Sicherheitsnetz: alle offenen Aufgaben mit Datum neu planen. */
    suspend fun rescheduleAll() {
        val todos = runCatching { dao.findOpenWithDueDate() }.getOrElse {
            Timber.w(it, "TodoReminderScheduler: Query offener Aufgaben fehlgeschlagen")
            return
        }
        val scheduled = todos.count { sync(it) }
        if (scheduled > 0) {
            Timber.d("TodoReminderScheduler: %d Aufgaben-Reminder (re)geplant", scheduled)
        }
    }
}
