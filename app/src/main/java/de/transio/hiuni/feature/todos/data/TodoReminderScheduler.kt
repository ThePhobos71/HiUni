package de.transio.hiuni.feature.todos.data

import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.core.notifications.data.NotificationKind
import timber.log.Timber
import java.time.Clock
import java.time.Instant
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
 * [rescheduleAll] ist das Sicherheitsnetz, aufgerufen vom
 * [de.transio.hiuni.core.sync.ReminderRestorer] (App-Start, Reboot, App-Update,
 * Zeit-/Zonenwechsel): AlarmManager verwirft Alarme bei Reboot/Force-Stop/Update,
 * dann werden alle offenen Aufgaben mit künftigem Reminder idempotent neu geplant.
 *
 * Die Kategorie-Einstellung (Aufgaben an/aus) greift beim Feuern im
 * [de.transio.hiuni.core.notifications.NotificationPresenter].
 */
@Singleton
class TodoReminderScheduler @Inject constructor(
    private val scheduler: NotificationScheduler,
    private val dao: TodoDao
) {

    /**
     * Uhr/Zone nur für Unit-Tests überschreibbar. In Produktion `null`: dann gilt bei
     * jedem Aufruf die Systemuhr mit der AKTUELLEN System-Zone. Nicht beim Bau des
     * Singletons einfrieren (`Clock.systemDefaultZone()` merkt sich die Zone), sonst
     * würde das Neu-Planen nach einem Zonenwechsel noch 09:00 in der alten Zone rechnen.
     */
    internal var clock: Clock? = null
    private fun currentClock(): Clock = clock ?: Clock.systemDefaultZone()

    /**
     * Bringt den Alarm einer persistierten Aufgabe auf den aktuellen Stand:
     * vorhandenen Alarm canceln, dann (falls noch sinnvoll) neu planen.
     * Rückgabe: wurde ein Reminder geplant?
     */
    fun sync(todo: TodoEntity): Boolean {
        val id = TodoReminderPlanner.reminderId(todo.id) ?: return false
        scheduler.cancel(id)
        val clock = currentClock()
        val triggerAt = TodoReminderPlanner.triggerAt(todo, clock.instant(), clock.zone) ?: return false
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

    /**
     * „Später" aus der Notification: zeigt die Erinnerung der Aufgabe [todoId] zum
     * Zeitpunkt [until] erneut. Läuft als eigener Snooze-Alarm neben dem regulären
     * (siehe [NotificationScheduler.schedule] mit `snooze = true`), Abhaken,
     * Bearbeiten und Löschen räumen ihn über [sync]/[cancel] mit ab. Nach einem
     * Reboot ist ein laufender Snooze weg; das ist bei einer Stunde vertretbar.
     *
     * Rückgabe: wurde geplant? `false` für erledigte oder gelöschte Aufgaben.
     */
    suspend fun snooze(todoId: Long, until: Instant): Boolean {
        val id = TodoReminderPlanner.reminderId(todoId) ?: return false
        val todo = dao.findById(todoId) ?: return false
        if (todo.isDone) return false
        scheduler.schedule(
            eventId = id,
            title = TodoReminderPlanner.title(todo),
            triggerAt = until,
            kind = NotificationKind.TODO,
            body = TodoReminderPlanner.body(todo),
            snooze = true
        )
        return true
    }

    /** Restore-Sicherheitsnetz: alle offenen Aufgaben mit Datum neu planen. */
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
