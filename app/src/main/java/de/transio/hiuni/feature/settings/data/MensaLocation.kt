package de.transio.hiuni.feature.settings.data

/**
 * Bekannte STW-ON Standorte rund um Hildesheim. IDs verifiziert via
 * `GET https://sls.api.stw-on.de/v1/location`.
 * Phase 4 könnte das live aus der API ziehen; für jetzt reicht eine Konstante.
 */
data class MensaLocation(
    val id: Int,
    val name: String,
    val description: String
)

val HildesheimLocations: List<MensaLocation> = listOf(
    MensaLocation(
        id = 150,
        name = "Mensa Uni Hildesheim",
        description = "Universitätsplatz 1 · Hauptmensa"
    ),
    MensaLocation(
        id = 152,
        name = "Cafeteria Uni Hildesheim",
        description = "Universitätsplatz 1 · Kleine Snacks und Kaffee"
    ),
    MensaLocation(
        id = 153,
        name = "Bistro Bühler",
        description = "Lübecker Straße 3 · Campus Bühler"
    )
)

fun locationById(id: Int): MensaLocation? = HildesheimLocations.firstOrNull { it.id == id }
