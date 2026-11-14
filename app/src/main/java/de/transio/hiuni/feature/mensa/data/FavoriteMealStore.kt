package de.transio.hiuni.feature.mensa.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistenz des Lieblingsgericht-Alarms: die gemerkten Stichwörter (in der
 * Reihenfolge des Hinzufügens) und die Schlüssel bereits gemeldeter Treffer
 * (siehe [FavoriteMealNotifier.hitKey]).
 *
 * Liegt im gemeinsamen Preferences-DataStore. Die Stichwörter stehen als
 * JSON-Liste in einem String, weil ein String-Set die Reihenfolge verlöre.
 * Unlesbarer Inhalt gilt als leere Liste.
 */
@Singleton
class FavoriteMealStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    fun observeKeywords(): Flow<List<String>> =
        dataStore.data.map { decode(it[KEYWORDS]) }.distinctUntilChanged()

    suspend fun currentKeywords(): List<String> = decode(dataStore.data.first()[KEYWORDS])

    /**
     * Merkt ein Stichwort. Abgelehnt (`false`) wird, was kürzer als
     * [FavoriteMealNotifier.MIN_KEYWORD_LENGTH] oder länger als [MAX_KEYWORD_LENGTH]
     * ist, was es (ohne Beachtung der Großschreibung) schon gibt, und alles über
     * [MAX_KEYWORDS] Einträge hinaus.
     */
    suspend fun addKeyword(raw: String): Boolean {
        val keyword = raw.trim().replace(WHITESPACE, " ")
        if (keyword.length < FavoriteMealNotifier.MIN_KEYWORD_LENGTH) return false
        if (keyword.length > MAX_KEYWORD_LENGTH) return false
        var added = false
        dataStore.edit { prefs ->
            val current = decode(prefs[KEYWORDS])
            val duplicate = current.any { it.equals(keyword, ignoreCase = true) }
            if (!duplicate && current.size < MAX_KEYWORDS) {
                prefs[KEYWORDS] = json.encodeToString(serializer, current + keyword)
                added = true
            }
        }
        return added
    }

    suspend fun removeKeyword(keyword: String) {
        val target = keyword.trim()
        dataStore.edit { prefs ->
            val current = decode(prefs[KEYWORDS])
            prefs[KEYWORDS] = json.encodeToString(
                serializer,
                current.filterNot { it.equals(target, ignoreCase = true) }
            )
        }
    }

    suspend fun notifiedKeys(): Set<String> = dataStore.data.first()[NOTIFIED].orEmpty()

    /** Ersetzt die Menge der gemeldeten Treffer vollständig. */
    suspend fun setNotifiedKeys(keys: Set<String>) {
        dataStore.edit { it[NOTIFIED] = keys }
    }

    private fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrElse {
                Timber.w(it, "Lieblingsgerichte nicht lesbar, werden ignoriert")
                emptyList()
            }
    }

    companion object {
        const val MAX_KEYWORD_LENGTH = 40
        const val MAX_KEYWORDS = 20

        private val KEYWORDS = stringPreferencesKey("mensa_favorite_keywords")
        private val NOTIFIED = stringSetPreferencesKey("mensa_favorite_notified")
        private val WHITESPACE = Regex("\\s+")
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(String.serializer())
    }
}
