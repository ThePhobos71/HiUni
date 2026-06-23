package de.transio.hiuni.core.notifications.data

/**
 * Quelle/Art einer geloggten Benachrichtigung. Steuert das Icon im Push-Center
 * und – sobald Features sie befüllen – die semantische Farbgebung.
 *
 * Storage: als TEXT via [Converters.notificationKindToString]. Unbekannte Werte
 * aus alten Builds fallen beim Lesen auf [SYSTEM] zurück.
 */
enum class NotificationKind {
    EVENT,
    EXAM,
    GRADE,
    MAIL,
    MENSA,
    MOVIE,
    SPORT,
    BIB,
    SYSTEM
}
