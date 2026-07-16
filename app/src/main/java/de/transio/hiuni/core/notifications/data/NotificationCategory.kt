package de.transio.hiuni.core.notifications.data

/**
 * Fachliche Benachrichtigungs-Kategorie. Fasst mehrere [NotificationKind]-Werte
 * zu einer nutzersichtbaren Gruppe zusammen — genau die Granularität, in der der
 * Nutzer im Push-Center filtert, in den App-Einstellungen togglet und in den
 * Android-Systemeinstellungen (via Notification-Channel) stummschaltet.
 *
 * Jede Kategorie besitzt genau EINEN Android-Notification-Channel ([channelId]).
 * Die Channel-IDs sind stabile String-Konstanten und dürfen sich nach Release NIE
 * mehr ändern — sonst legt Android einen neuen Channel an und der Nutzer verliert
 * seine Stumm-Einstellung. Neue Kategorien einfach hinten anhängen.
 *
 * Rein datengetrieben und Android-frei (keine `Context`/`R`-Referenz), damit die
 * Kind→Kategorie→Channel-Zuordnung als schneller JVM-Unit-Test abgesichert werden
 * kann. Anzeigenamen/Beschreibungen liegen als String-Resource-Keys in
 * `strings.xml` und werden erst beim Channel-Anlegen (HiUniApplication) aufgelöst.
 */
enum class NotificationCategory(
    /** Stabile Android-Notification-Channel-ID. NIE nach Release ändern. */
    val channelId: String
) {
    /** Kalender-/Termin-Erinnerungen (lokale AlarmManager-Reminder). */
    EVENTS("hiuni_event_reminders"),

    /** Klausuren: neue Klausur, Klausur-Reminder. */
    EXAMS("hiuni_exams"),

    /** Noten: neue/aktualisierte Leistung im Notenspiegel. */
    GRADES("hiuni_grades"),

    /** Kurse: neuer/geänderter Kurs im LSF. */
    COURSES("hiuni_courses"),

    /** Learnweb/Moodle: neue Aufgaben, Deadlines. */
    LEARNWEB("hiuni_learnweb"),

    /** E-Mail: neue Nachrichten im Uni-Postfach. */
    MAIL("hiuni_mail"),

    /**
     * Sammelkanal für alles Übrige (Mensa, Kino, Sport, Bib-Buchungen,
     * System-Meldungen). Fein genug für den Alltag; wächst eine dieser Quellen
     * zur eigenen Kategorie, bekommt sie einen eigenen Channel.
     */
    SYSTEM("hiuni_system");

    companion object {
        /**
         * Einzige Quelle der Wahrheit für die Kind→Kategorie-Zuordnung. Wird vom
         * [de.transio.hiuni.core.notifications.NotificationPresenter] (Channel-Wahl),
         * vom Push-Center-Filter und vom Settings-Toggle-Gate genutzt.
         */
        fun of(kind: NotificationKind): NotificationCategory = when (kind) {
            NotificationKind.EVENT -> EVENTS
            NotificationKind.EXAM -> EXAMS
            NotificationKind.GRADE -> GRADES
            NotificationKind.COURSE -> COURSES
            NotificationKind.LEARNWEB -> LEARNWEB
            NotificationKind.MAIL -> MAIL
            NotificationKind.MENSA,
            NotificationKind.MOVIE,
            NotificationKind.SPORT,
            NotificationKind.BIB,
            NotificationKind.SYSTEM -> SYSTEM
        }

        /** Der Android-Channel für ein [NotificationKind]. Kurz-Alias für `of(kind).channelId`. */
        fun channelIdFor(kind: NotificationKind): String = of(kind).channelId
    }
}
