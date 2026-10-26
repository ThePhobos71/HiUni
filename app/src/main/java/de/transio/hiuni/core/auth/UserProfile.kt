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
    val matrikel: String?,       // "00403556"
    /**
     * eduPerson-Affiliations ("student", "employee", "staff", ...), falls CAS sie
     * mitliefert. Die Attributseite der Uni Hildesheim zeigt sie bisher nicht, dann
     * bleibt die Liste leer. Speist die Mensa-Preisgruppen-Erkennung.
     */
    val affiliations: List<String> = emptyList()
) {
    /** Erster Vorname (alles vor dem ersten Leerzeichen). */
    val firstName: String?
        get() = vorname?.trim()?.split(' ')?.firstOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        val EMPTY = UserProfile(null, null, null, null, null, null)
    }
}
