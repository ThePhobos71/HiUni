package de.transio.hiuni.core.notifications.lecture

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merkt sich weggewischte Laufende-Vorlesung-Mitteilungen ([LiveLectureOccurrence.key]).
 *
 * Seit Android 14 lassen sich auch `ongoing`-Mitteilungen wegwischen. Ohne Merker
 * käme die Mitteilung beim nächsten Planen (App-Start, Sync) für dieselbe Vorlesung
 * zurück. Merker vergangener Vorlesungen werden beim Lesen aufgeräumt, die Menge
 * bleibt also winzig.
 */
@Singleton
class LiveLectureDismissStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    /** Aktuelle Merker, abgelaufene werden dabei entfernt. */
    suspend fun current(now: Instant): Set<String> {
        val stored = dataStore.data.first()[KEY].orEmpty()
        val pruned = LiveLecturePlanner.pruneDismissed(stored, now)
        if (pruned != stored) dataStore.edit { it[KEY] = pruned }
        return pruned
    }

    /** Aufgeräumt wird beim nächsten [current], hier zählt nur das Merken. */
    suspend fun add(key: String) {
        dataStore.edit { prefs -> prefs[KEY] = prefs[KEY].orEmpty() + key }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("live_lecture_dismissed")
    }
}
