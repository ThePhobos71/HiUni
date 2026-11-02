package de.transio.hiuni.core.notifications.lecture

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.core.notifications.data.NotificationKind
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stellt die Alarme der Vorlesungs-Erinnerungen über den [NotificationScheduler] und
 * räumt nicht mehr geplante ab.
 *
 * AlarmManager kann geplante Alarme nicht auflisten, deshalb merkt sich ein kleines
 * Register (`id@triggerMillis`) die zuletzt gestellten. Bei [sync] wird jede ID
 * abgeräumt, die nicht mehr im Plan steht (Kurs abgeschaltet, Termin verschoben oder
 * gelöscht), aber nur, solange ihr Trigger noch in der Zukunft lag. Bereits gefeuerte
 * Erinnerungen bleiben unangetastet: sonst würde ein „Später" darauf (gleiche ID,
 * eigener Snooze-Alarm) beim nächsten Planen wieder gelöscht.
 */
@Singleton
class LectureReminderAlarms @Inject constructor(
    private val scheduler: NotificationScheduler,
    private val dataStore: DataStore<Preferences>
) {

    /** Gleicht die gestellten Alarme mit [reminders] ab. Idempotent. */
    suspend fun sync(reminders: List<LectureReminder>, now: Instant) {
        val previous = dataStore.data.first()[KEY].orEmpty().mapNotNull(::parseEntry)
        val planned = reminders.map { it.id }.toSet()
        val nowMillis = now.toEpochMilli()
        previous
            .filter { (id, triggerMillis) -> id !in planned && triggerMillis > nowMillis }
            .forEach { (id, _) -> scheduler.cancel(id) }
        reminders.forEach {
            scheduler.schedule(
                eventId = it.id,
                title = it.title,
                triggerAt = it.triggerAt,
                kind = NotificationKind.EVENT,
                body = it.body
            )
        }
        val entries = reminders.map { "${it.id}@${it.triggerAt.toEpochMilli()}" }.toSet()
        dataStore.edit { prefs ->
            if (entries.isEmpty()) prefs.remove(KEY) else prefs[KEY] = entries
        }
    }

    private fun parseEntry(entry: String): Pair<Long, Long>? {
        val id = entry.substringBefore('@').toLongOrNull() ?: return null
        val trigger = entry.substringAfter('@', "").toLongOrNull() ?: return null
        return id to trigger
    }

    private companion object {
        val KEY = stringSetPreferencesKey("lecture_reminders_scheduled")
    }
}
