package de.transio.hiuni.feature.email

/**
 * Aktionen die der User auf einer Mail-Liste per Swipe auslösen kann. Pro
 * Richtung konfigurierbar in den Settings — wer das aggressive "Delete-rechts"-
 * Standard-Mapping nicht mag, kann beide Swipes auch auf harmlosere Aktionen
 * legen (Sternen / als gelesen markieren / aus).
 */
enum class MailSwipeAction(val storageKey: String, val displayLabel: String) {
    ARCHIVE("archive", "Archivieren"),
    DELETE("delete", "Löschen"),
    TOGGLE_STAR("star", "Sternen"),
    MARK_READ("read", "Als gelesen"),
    NONE("none", "Aus");

    companion object {
        fun fromKey(key: String?): MailSwipeAction =
            entries.firstOrNull { it.storageKey == key } ?: ARCHIVE

        /** Standard für „nach rechts wischen" (StartToEnd). */
        val DEFAULT_RIGHT = ARCHIVE
        /** Standard für „nach links wischen" (EndToStart). */
        val DEFAULT_LEFT = DELETE
    }
}
