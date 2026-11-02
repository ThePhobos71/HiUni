package de.transio.hiuni.feature.bib.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Einstellungen des Raum-Alarms ([BibRoomAlarm]) im App-weiten Preferences-DataStore,
 * neben den Lieblings-Räumen aus [BibFavoritesStore].
 *
 * STANDARD/MIGRATION: Solange kein Modus gespeichert ist, gilt
 * [BibRoomAlarm.defaultModeFor] auf Basis der vorhandenen Favoriten (Bestandsnutzer
 * mit Favoriten: „Immer", sonst „Aus"). Damit dieser abgeleitete Wert nicht kippt,
 * sobald ein neuer Nutzer seinen ersten Favoriten markiert, schreibt
 * [BibFavoritesStore.toggle] ihn vor der ersten Änderung fest ([freezeDefaultMode]).
 *
 * „Nur heute" wird beim Lesen über [current] ausgewertet und an einem späteren Tag
 * auf „Aus" zurückgesetzt, damit auch der Screen danach „Aus" zeigt.
 */
@Singleton
class BibRoomAlarmStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    /** Gespeicherter Stand (ohne Tagesauswertung; „Nur heute" vom Vortag bleibt sichtbar bis [current]). */
    val alarm: Flow<BibRoomAlarm> = dataStore.data.map(::read)

    /**
     * Stand für [today]. Ein abgelaufenes „Nur heute" wird dabei persistent auf
     * „Aus" gesetzt und als „Aus" geliefert.
     */
    suspend fun current(today: LocalDate): BibRoomAlarm {
        val stored = read(dataStore.data.first())
        if (stored.mode != BibAlarmMode.TODAY_ONLY || stored.activatedOn == today) return stored
        dataStore.edit { prefs ->
            prefs[KEY_MODE] = BibAlarmMode.OFF.name
            prefs.remove(KEY_ACTIVATED_ON)
        }
        return stored.copy(mode = BibAlarmMode.OFF, activatedOn = null)
    }

    /** Setzt den Modus; „Nur heute" merkt sich [today] als Aktivierungstag. */
    suspend fun setMode(mode: BibAlarmMode, today: LocalDate) {
        dataStore.edit { prefs ->
            prefs[KEY_MODE] = mode.name
            if (mode == BibAlarmMode.TODAY_ONLY) {
                prefs[KEY_ACTIVATED_ON] = today.toString()
            } else {
                prefs.remove(KEY_ACTIVATED_ON)
            }
        }
    }

    /** Wunschdauer in Minuten; nur Werte aus [BibRoomAlarm.DURATION_OPTIONS]. */
    suspend fun setDuration(minutes: Int) {
        val value = minutes.takeIf { it in BibRoomAlarm.DURATION_OPTIONS }
            ?: BibRoomAlarm.DEFAULT_DURATION_MINUTES
        dataStore.edit { it[KEY_DURATION] = value }
    }

    suspend fun setFromHour(hour: Int) = updateWindow { it.withFromHour(hour) }

    suspend fun setToHour(hour: Int) = updateWindow { it.withToHour(hour) }

    private suspend fun updateWindow(change: (BibRoomAlarm) -> BibRoomAlarm) {
        dataStore.edit { prefs ->
            val next = change(read(prefs))
            prefs[KEY_FROM_HOUR] = next.fromHour
            prefs[KEY_TO_HOUR] = next.toHour
        }
    }

    companion object {
        internal val KEY_MODE = stringPreferencesKey("bib_alarm_mode")
        private val KEY_ACTIVATED_ON = stringPreferencesKey("bib_alarm_activated_on")
        private val KEY_DURATION = intPreferencesKey("bib_alarm_duration_min")
        private val KEY_FROM_HOUR = intPreferencesKey("bib_alarm_from_hour")
        private val KEY_TO_HOUR = intPreferencesKey("bib_alarm_to_hour")

        /**
         * Schreibt den abgeleiteten Standardmodus fest, falls noch keiner gespeichert
         * ist. Aufruf innerhalb derselben `edit`-Transaktion VOR der Änderung der
         * Favoriten (siehe [BibFavoritesStore.toggle]).
         */
        internal fun freezeDefaultMode(prefs: MutablePreferences, hasFavoritesBefore: Boolean) {
            if (prefs[KEY_MODE] == null) {
                prefs[KEY_MODE] = BibRoomAlarm.defaultModeFor(hasFavoritesBefore).name
            }
        }

        private fun read(prefs: Preferences): BibRoomAlarm {
            val hasFavorites = prefs[BibFavoritesStore.KEY_FAVORITES].orEmpty().isNotEmpty()
            val mode = prefs[KEY_MODE]
                ?.let { stored -> BibAlarmMode.entries.firstOrNull { it.name == stored } }
                ?: BibRoomAlarm.defaultModeFor(hasFavorites)
            val activatedOn = prefs[KEY_ACTIVATED_ON]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val duration = prefs[KEY_DURATION]?.takeIf { it in BibRoomAlarm.DURATION_OPTIONS }
                ?: BibRoomAlarm.DEFAULT_DURATION_MINUTES
            val base = BibRoomAlarm(mode = mode, activatedOn = activatedOn, durationMinutes = duration)
            val from = prefs[KEY_FROM_HOUR] ?: BibRoomAlarm.DEFAULT_FROM_HOUR
            val to = prefs[KEY_TO_HOUR] ?: BibRoomAlarm.DEFAULT_TO_HOUR
            // Über die Normalisierung laden, damit kaputte Werte nie ein leeres Fenster ergeben.
            return base.withToHour(to).withFromHour(from)
        }
    }
}
