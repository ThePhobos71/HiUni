package de.transio.hiuni.feature.bib.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lieblings-Gruppenräume plus die Baseline für die Frei-Meldungen, beides im
 * App-weiten Preferences-DataStore. Kein Room-Schema nötig: es sind höchstens vier
 * Raum-IDs und eine kleine Schlüsselmenge (Belegt-Slots von heute und morgen, siehe
 * [BibFreeSlotNotifier]).
 */
@Singleton
class BibFavoritesStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    /** Reaktive Menge der Lieblings-Raum-IDs (leer = keine Favoriten). */
    val favorites: Flow<Set<Int>> = dataStore.data.map { prefs ->
        prefs[KEY_FAVORITES].orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
    }

    suspend fun currentFavorites(): Set<Int> = favorites.first()

    /**
     * Schaltet [roomId] als Favorit um und liefert den neuen Zustand (`true` =
     * jetzt Favorit). Beim Entfernen fliegt auch die Baseline des Raums raus,
     * damit ein späteres Wieder-Markieren nicht gegen veraltete Belegung diffed.
     */
    suspend fun toggle(roomId: Int): Boolean {
        var nowFavorite = false
        dataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITES].orEmpty()
            val id = roomId.toString()
            nowFavorite = id !in current
            prefs[KEY_FAVORITES] = if (nowFavorite) current + id else current - id
            if (!nowFavorite) {
                val prefix = BibFreeSlotNotifier.roomKeyPrefix(roomId)
                prefs[KEY_BOOKED_BASELINE] = prefs[KEY_BOOKED_BASELINE].orEmpty()
                    .filterNot { it.startsWith(prefix) }
                    .toSet()
            }
        }
        return nowFavorite
    }

    /** Belegt-Slots der Lieblings-Räume beim letzten Check. */
    suspend fun bookedBaseline(): Set<String> =
        dataStore.data.first()[KEY_BOOKED_BASELINE].orEmpty()

    suspend fun setBookedBaseline(keys: Set<String>) {
        dataStore.edit { it[KEY_BOOKED_BASELINE] = keys }
    }

    private companion object {
        val KEY_FAVORITES = stringSetPreferencesKey("bib_favorite_rooms")
        val KEY_BOOKED_BASELINE = stringSetPreferencesKey("bib_favorite_booked_baseline")
    }
}
