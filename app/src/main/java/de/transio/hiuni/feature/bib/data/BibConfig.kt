package de.transio.hiuni.feature.bib.data

/**
 * Endpunkte des UB-Hildesheim Gruppenraumbuchungs-Tools (Lars-Heuer-Stack auf
 * `ubwww`). Login geht über CAS-SSO — `index.php?login` redirected zu
 * `/sso/login?service=<self>?login` → ST → `?login&ticket=…` setzt PHPSESSID
 * für die eigentliche App.
 *
 * AJAX-Endpunkte erwarten cookie-basierte PHP-Sessions und verstehen
 * comma-separated `value`-Parameter. Beispiele:
 *   - `set_data.php?action=book_room&value=YYYYMMDD,HHMM,HHMM,ROOM,`
 *   - `set_data.php?action=delete&value=YYYYMMDD,HHMM,ROOM`
 *   - `get_data.php?action=get_end_times&value=YYYYMMDD,HHMM,ROOM`
 */
object BibConfig {
    const val HOST = "ubwww.uni-hildesheim.de"
    const val BASE_URL = "https://$HOST/gruppenraumbuchung"

    /** Roh-HTML mit Belegungs-Grid; ohne Login lesbar (anonyme Sicht). */
    const val INDEX_URL = "$BASE_URL/index.php"

    /**
     * Authentifizierte Belegungs-Sicht. ubwww nutzt den `?login`-Trailer
     * nicht nur als CAS-Service-Callback, sondern auch als View-Switch:
     * eigene Buchungen werden nur dann mit `#999999` gerendert. Ohne den
     * Parameter sieht selbst eine User-PHPSESSID identisch zur anonymen
     * Sicht aus → OWN_BOOKING-Detection schlägt fehl.
     */
    const val INDEX_URL_AUTHENTICATED = "$BASE_URL/index.php?login"

    /** Service-URL für CAS-SSO. Trigger-Wert ist der `?login`-Trailer. */
    const val LOGIN_SERVICE = "$BASE_URL/index.php?login"

    const val AJAX_BASE = "$BASE_URL/ajax_php"
    const val BOOKINGS_URL = "$AJAX_BASE/bookings.php"
    const val SET_DATA_URL = "$AJAX_BASE/set_data.php"
    const val GET_DATA_URL = "$AJAX_BASE/get_data.php"

    /** Raum-IDs wie sie das Backend versteht. */
    val ROOM_IDS = listOf(101, 102, 103, 105)

    /**
     * Raum-Label + Kapazität + Ausstattung (siehe Footer-Liste auf der index.php
     * und Aushang der UB). F101, F102, F103 haben Bildschirme (HDMI/Wireless
     * gegen Pfand an der Leihtheke); F105 nur Whiteboard.
     */
    val ROOM_META: Map<Int, RoomMeta> = mapOf(
        101 to RoomMeta(label = "F101", capacityMin = 3, capacityMax = 5, hasScreen = true),
        102 to RoomMeta(label = "F102", capacityMin = 4, capacityMax = 6, hasScreen = true),
        103 to RoomMeta(label = "F103", capacityMin = 6, capacityMax = 10, hasScreen = true),
        105 to RoomMeta(label = "F105", capacityMin = 4, capacityMax = 6, hasScreen = false)
    )

    /** Buchungs-Limit pro Reservierung: max. 2 h = 4 × 30-Min-Slots. */
    const val MAX_SLOTS_PER_BOOKING = 4

    data class RoomMeta(
        val label: String,
        val capacityMin: Int,
        val capacityMax: Int,
        val hasScreen: Boolean
    )
}
