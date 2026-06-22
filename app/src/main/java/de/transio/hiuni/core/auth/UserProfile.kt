package de.transio.hiuni.core.auth

/**
 * Aus der CAS-Attribute-Page extrahierte User-Daten (siehe Spike 2026-05-24:
 * `GET https://www.uni-hildesheim.de/sso/login` ohne service-Param zeigt eine
 * Tabelle mit "Vorname", "Nachname", "Name", "Mail", "mtknr", "uid", etc.).
 */
data class UserProfile(
    val uid: String?,            // "karstens"
    val vorname: String?,        // "Kjell Heinrich" (kann mehrere Vornamen enthalten)
    val nachname: String?,       // "Karstens"
    val fullName: String?,       // "Kjell Heinrich Karstens"
    val mail: String?,           // "karstens@uni-hildesheim.de"
    val matrikel: String?        // "00403556"
) {
    /** Erster Vorname (alles vor dem ersten Leerzeichen). */
    val firstName: String?
        get() = vorname?.trim()?.split(' ')?.firstOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        val EMPTY = UserProfile(null, null, null, null, null, null)
    }
}
