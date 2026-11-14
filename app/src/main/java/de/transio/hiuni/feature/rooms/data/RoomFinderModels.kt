package de.transio.hiuni.feature.rooms.data

import kotlinx.serialization.Serializable

/**
 * Standorte, die der Raumfinder anbietet. Der Campus steckt im Präfix des
 * LSF-Raumnamens (`SC.A.0.09` liegt am Samelsonplatz). Das Leesterhaus (`WL`)
 * fehlt bewusst: dort gibt es nur Musik-Übungsräume der Musikschule.
 */
enum class Campus(val prefix: String, val label: String) {
    SAMELSON("SC", "Samelsonplatz"),
    HAUPTCAMPUS("HC", "Hauptcampus"),
    BUEHLER("BC", "Bühler-Campus"),
    KULTURCAMPUS("KC", "Kulturcampus"),
    SCHUETZENALLEE("SH", "Schützenallee");

    companion object {
        fun fromPrefix(prefix: String): Campus? = entries.firstOrNull { it.prefix == prefix }
        fun fromName(name: String?): Campus? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Ein Raum aus der LSF-Raumliste.
 *
 * @property rgid  LSF-Raum-ID (`raum.rgid`), Schlüssel für den Wochenplan.
 * @property code  Raumname wie `SC.A.0.09` oder `HC.E.0.Hörsaal 3`.
 * @property type  Raumart wie `Großer Seminarraum` oder `Hörsaal`.
 */
@Serializable
data class LsfRoom(
    val rgid: Int,
    val code: String,
    val type: String,
    val campus: Campus
)

/**
 * Belegte Zeitspanne eines Tages in Minuten ab Mitternacht, halboffen
 * `[startMinute, endMinute)`. `endMinute` darf 1440 sein (bis Tagesende).
 */
@Serializable
data class BusySpan(val startMinute: Int, val endMinute: Int)

/** Belegung eines Raums an einem Tag. */
@Serializable
data class RoomDay(
    val room: LsfRoom,
    val busy: List<BusySpan>
)

/**
 * Zwischengespeicherter Stand eines Campus für genau einen Tag.
 *
 * @property date       Tag im Format `yyyy-MM-dd`, für den die Belegung gilt.
 * @property fetchedAt  Abrufzeit (Epoch-Millis) für die Anzeige "Stand 13:42".
 * @property rooms      Erfolgreich geladene Räume.
 * @property missing    Räume, deren Plan nicht geladen werden konnte.
 */
@Serializable
data class CampusSnapshot(
    val campus: Campus,
    val date: String,
    val fetchedAt: Long,
    val rooms: List<RoomDay>,
    val missing: List<LsfRoom> = emptyList()
)
