package de.transio.hiuni.feature.mensa.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistenter Cache für die Mensa-Zusatzdaten, die nicht in Room liegen: STW-Hinweise
 * (Schließtage, Ausgaben-Infos) und Öffnungszeiten. Beides lag vorher nur im Speicher
 * und erschien deshalb erst nach dem Netz-Refresh (oder nach einem Prozess-Neustart
 * innerhalb der 6-h-Drossel gar nicht). Jetzt rendern Header-Badge und Hinweise ab
 * dem ersten Frame aus diesem Cache.
 *
 * Gespeichert wird pro Location als JSON-String im gemeinsamen Preferences-DataStore;
 * die Datenmenge ist klein (wenige Hinweise, eine Handvoll Öffnungszeit-Blöcke).
 */
@Singleton
class MensaExtrasStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    fun observeAnnouncements(locationId: Int): Flow<List<Announcement>> =
        dataStore.data
            .map { prefs -> decode(prefs[announcementsKey(locationId)], announcementListSerializer) }
            .map { list -> list.mapNotNull { it.toDomain() } }
            .distinctUntilChanged()

    fun observeOpeningHours(locationId: Int): Flow<List<OpeningHourBlock>> =
        dataStore.data
            .map { prefs -> decode(prefs[openingHoursKey(locationId)], openingHourListSerializer) }
            .map { list -> list.mapNotNull { it.toDomain() } }
            .distinctUntilChanged()

    /** Ersetzt die Hinweise der Location komplett (auch durch eine leere Liste). */
    suspend fun saveAnnouncements(locationId: Int, announcements: List<Announcement>) {
        val encoded = json.encodeToString(
            announcementListSerializer,
            announcements.map { StoredAnnouncement.from(it) }
        )
        dataStore.edit { it[announcementsKey(locationId)] = encoded }
    }

    /**
     * Speichert die Öffnungszeiten der Location. Eine leere Liste überschreibt NICHT:
     * die API liefert die Zeiten nur über das erste Gericht mit, an Tagen ohne Speiseplan
     * (Ferien, Wochenende) käme sonst der Fallback statt der zuletzt bekannten Zeiten.
     */
    suspend fun saveOpeningHours(locationId: Int, blocks: List<OpeningHourBlock>) {
        if (blocks.isEmpty()) return
        val encoded = json.encodeToString(
            openingHourListSerializer,
            blocks.map { StoredOpeningHour.from(it) }
        )
        dataStore.edit { it[openingHoursKey(locationId)] = encoded }
    }

    private fun <T> decode(raw: String?, serializer: kotlinx.serialization.KSerializer<List<T>>): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrElse {
                Timber.w(it, "Mensa-Zusatzcache nicht lesbar, wird ignoriert")
                emptyList()
            }
    }

    @Serializable
    private data class StoredAnnouncement(
        val date: String,
        val text: String,
        val time: String,
        val lane: String? = null
    ) {
        fun toDomain(): Announcement? = runCatching {
            Announcement(
                date = LocalDate.parse(date),
                text = text,
                time = AnnouncementTime.valueOf(time),
                lane = lane
            )
        }.getOrNull()

        companion object {
            fun from(a: Announcement) = StoredAnnouncement(
                date = a.date.toString(),
                text = a.text,
                time = a.time.name,
                lane = a.lane
            )
        }
    }

    @Serializable
    private data class StoredOpeningHour(
        val time: String,
        val startDay: Int,
        val endDay: Int,
        val startTime: String,
        val endTime: String
    ) {
        fun toDomain(): OpeningHourBlock? = runCatching {
            OpeningHourBlock(
                time = time,
                startDay = startDay,
                endDay = endDay,
                startTime = LocalTime.parse(startTime),
                endTime = LocalTime.parse(endTime)
            )
        }.getOrNull()

        companion object {
            fun from(b: OpeningHourBlock) = StoredOpeningHour(
                time = b.time,
                startDay = b.startDay,
                endDay = b.endDay,
                startTime = b.startTime.toString(),
                endTime = b.endTime.toString()
            )
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val announcementListSerializer = ListSerializer(StoredAnnouncement.serializer())
        val openingHourListSerializer = ListSerializer(StoredOpeningHour.serializer())

        fun announcementsKey(locationId: Int) =
            stringPreferencesKey("mensa_announcements_$locationId")

        fun openingHoursKey(locationId: Int) =
            stringPreferencesKey("mensa_opening_hours_$locationId")
    }
}
