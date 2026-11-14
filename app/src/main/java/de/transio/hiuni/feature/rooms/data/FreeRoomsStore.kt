package de.transio.hiuni.feature.rooms.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zwischenspeicher des Raumfinders im App-weiten Preferences-DataStore.
 *
 * Bewusst keine Room-Tabelle: Je Campus gibt es genau einen kleinen Stand (ein paar
 * Dutzend Räume mit ihren belegten Spannen), der immer als Ganzes gelesen und
 * ersetzt wird und nur bis Tagesende gilt. Gespeichert wird er als JSON unter
 * `rooms_snapshot_<CAMPUS>`; dazu der zuletzt gewählte Campus.
 */
@Singleton
class FreeRoomsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    json: Json
) {
    private val json: Json = Json(from = json) { ignoreUnknownKeys = true }

    /** Zuletzt gewählter Campus, `null` vor der ersten Wahl. */
    val selectedCampus: Flow<Campus?> = dataStore.data.map { Campus.fromName(it[KEY_CAMPUS]) }

    suspend fun setCampus(campus: Campus) {
        dataStore.edit { it[KEY_CAMPUS] = campus.name }
    }

    /** Gespeicherter Stand für [campus], egal von welchem Tag (der Aufrufer prüft das Datum). */
    suspend fun snapshot(campus: Campus): CampusSnapshot? {
        val raw = dataStore.data.first()[snapshotKey(campus)] ?: return null
        return runCatching { json.decodeFromString(CampusSnapshot.serializer(), raw) }
            .onFailure { Timber.w(it, "Raumfinder: gespeicherter Stand für %s unlesbar", campus) }
            .getOrNull()
    }

    /**
     * Speichert [snapshot] und räumt Stände anderer Campus von früheren Tagen weg,
     * damit der DataStore nicht mit alten Plänen wächst.
     */
    suspend fun save(snapshot: CampusSnapshot) {
        val encoded = json.encodeToString(CampusSnapshot.serializer(), snapshot)
        dataStore.edit { prefs ->
            prefs[snapshotKey(snapshot.campus)] = encoded
            for (other in Campus.entries) {
                if (other == snapshot.campus) continue
                val raw = prefs[snapshotKey(other)] ?: continue
                val date = runCatching { json.decodeFromString(CampusSnapshot.serializer(), raw).date }.getOrNull()
                if (date != snapshot.date) prefs.remove(snapshotKey(other))
            }
        }
    }

    companion object {
        private val KEY_CAMPUS = stringPreferencesKey("rooms_campus")
        private fun snapshotKey(campus: Campus) = stringPreferencesKey("rooms_snapshot_${campus.name}")
    }
}
