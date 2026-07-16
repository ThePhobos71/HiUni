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

    /**
     * Neuer/geänderter Kurs im LSF. Ersetzt den früheren notdürftigen
     * [SYSTEM]-Fallback für „Neuer Kurs"-Pushes. Alte Log-Einträge, die noch als
     * `SYSTEM` persistiert sind, bleiben unberührt (kein Rewrite) — sie rendern
     * weiterhin mit dem System-Icon, neue Pushes bekommen das Kurs-Icon.
     */
    COURSE,

    /** Learnweb/Moodle: neue Aufgabe, Deadline-Reminder, Kursmaterial. */
    LEARNWEB,

    SYSTEM
}
