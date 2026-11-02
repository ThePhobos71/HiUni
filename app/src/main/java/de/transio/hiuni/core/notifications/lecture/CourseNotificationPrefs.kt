package de.transio.hiuni.core.notifications.lecture

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import de.transio.hiuni.feature.courses.data.CourseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mitteilungs-Einstellungen eines Kurses für seine Vorlesungstermine im Stundenplan.
 *
 * @property liveEnabled Laufende-Vorlesung-Mitteilung mit Countdown. Standard: an,
 *   also das bisherige Verhalten. Wirkt nur, solange der globale Schalter
 *   „Laufende Vorlesung" an ist.
 * @property reminderMinutes Erinnerung so viele Minuten vor Beginn, `null` = aus
 *   (Standard). Erscheint als Termin-Erinnerung (Kategorie „Termin-Erinnerungen").
 *
 * Bewusst kein globaler Standard für neue Kurse: ein neu importierter Kurs verhält
 * sich wie bisher (Live an, keine Erinnerung). Wer weniger sehen will, schaltet
 * gezielt ab oder nutzt „Alle aus" in den Einstellungen.
 */
data class CourseNotificationPrefs(
    val liveEnabled: Boolean = true,
    val reminderMinutes: Int? = null
) {
    companion object {
        val DEFAULT = CourseNotificationPrefs()

        /** Auswählbare Vorläufe in Minuten (zusätzlich „Aus"). */
        val REMINDER_OPTIONS: List<Int> = listOf(5, 10, 15, 30)

        /** Größter möglicher Vorlauf, bestimmt das Lade-Fenster des Planers. */
        val MAX_REMINDER_MINUTES: Int = REMINDER_OPTIONS.max()

        /** Präfix für Kurse ohne LSF-ID; LSF-IDs sind rein numerisch, Kollisionen ausgeschlossen. */
        const val OWN_COURSE_PREFIX = "kurs:"

        /**
         * Schlüssel der Einstellungen: die LSF-ID, weil die Stundenplan-Termine genau
         * darüber ([de.transio.hiuni.feature.calendar.data.CustomEventEntity.courseLsfId])
         * am Kurs hängen und sie Kurs-Neuimporte übersteht. Eigene Kurse ohne LSF-ID
         * nutzen ihre Kurs-ID (sie haben keine Stundenplan-Termine, die Einstellung
         * bleibt aber erhalten).
         */
        fun keyFor(course: CourseEntity): String =
            course.lsfId?.takeIf(String::isNotBlank) ?: "$OWN_COURSE_PREFIX${course.id}"
    }
}

/** Einstellungen für [key], fehlender Eintrag (oder `null`-Schlüssel) ergibt [CourseNotificationPrefs.DEFAULT]. */
fun Map<String, CourseNotificationPrefs>.prefsFor(key: String?): CourseNotificationPrefs =
    key?.let(::get) ?: CourseNotificationPrefs.DEFAULT

/** Kurse, deren Live-Anzeige ausgeschaltet ist. */
fun Map<String, CourseNotificationPrefs>.liveDisabledKeys(): Set<String> =
    filterValues { !it.liveEnabled }.keys

/** Kurs → Erinnerungs-Vorlauf in Minuten, nur Kurse mit eingeschalteter Erinnerung. */
fun Map<String, CourseNotificationPrefs>.reminderMinutesByKey(): Map<String, Int> =
    mapNotNull { (key, prefs) -> prefs.reminderMinutes?.let { key to it } }.toMap()

/** Persistenz der [CourseNotificationPrefs], als Schnittstelle für schlanke Tests. */
interface CourseNotificationPrefsStore {
    /** Alle vom Standard abweichenden Einträge, Kurs-Schlüssel → Einstellungen. */
    fun observeAll(): Flow<Map<String, CourseNotificationPrefs>>

    suspend fun current(): Map<String, CourseNotificationPrefs>

    suspend fun setLiveEnabled(key: String, enabled: Boolean)

    /** `null` oder ein Wert <= 0 schaltet die Erinnerung aus. */
    suspend fun setReminderMinutes(key: String, minutes: Int?)

    /** Sammel-Schalter „Alle an" / „Alle aus" in einem Schreibvorgang. */
    suspend fun setLiveEnabledFor(keys: Collection<String>, enabled: Boolean)
}

/**
 * Legt die Einstellungen im gemeinsamen Preferences-DataStore ab. Je Kurs zwei
 * Schlüssel (`course_notif_live__<key>`, `course_notif_reminder__<key>`), gespeichert
 * wird nur, was vom Standard abweicht. Neue Kurse brauchen also keinen Eintrag, und
 * „zurück auf Standard" räumt den Schlüssel wieder weg.
 */
@Singleton
class DataStoreCourseNotificationPrefsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : CourseNotificationPrefsStore {

    override fun observeAll(): Flow<Map<String, CourseNotificationPrefs>> = dataStore.data.map(::parse)

    override suspend fun current(): Map<String, CourseNotificationPrefs> = parse(dataStore.data.first())

    override suspend fun setLiveEnabled(key: String, enabled: Boolean) {
        dataStore.edit { it.putLive(key, enabled) }
    }

    override suspend fun setReminderMinutes(key: String, minutes: Int?) {
        dataStore.edit { prefs ->
            val reminderKey = intPreferencesKey(REMINDER_PREFIX + key)
            val valid = minutes?.takeIf { it > 0 }
            if (valid == null) prefs.remove(reminderKey) else prefs[reminderKey] = valid
        }
    }

    override suspend fun setLiveEnabledFor(keys: Collection<String>, enabled: Boolean) {
        if (keys.isEmpty()) return
        dataStore.edit { prefs -> keys.forEach { prefs.putLive(it, enabled) } }
    }

    private fun MutablePreferences.putLive(key: String, enabled: Boolean) {
        val liveKey = booleanPreferencesKey(LIVE_PREFIX + key)
        if (enabled) remove(liveKey) else this[liveKey] = false
    }

    private fun parse(prefs: Preferences): Map<String, CourseNotificationPrefs> {
        val result = mutableMapOf<String, CourseNotificationPrefs>()
        for ((prefKey, value) in prefs.asMap()) {
            val name = prefKey.name
            when {
                name.startsWith(LIVE_PREFIX) && value is Boolean && !value -> {
                    val key = name.removePrefix(LIVE_PREFIX)
                    result[key] = result.prefsFor(key).copy(liveEnabled = false)
                }
                name.startsWith(REMINDER_PREFIX) && value is Int && value > 0 -> {
                    val key = name.removePrefix(REMINDER_PREFIX)
                    result[key] = result.prefsFor(key).copy(reminderMinutes = value)
                }
            }
        }
        return result
    }

    private companion object {
        const val LIVE_PREFIX = "course_notif_live__"
        const val REMINDER_PREFIX = "course_notif_reminder__"
    }
}
