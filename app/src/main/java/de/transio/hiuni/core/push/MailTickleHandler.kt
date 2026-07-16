package de.transio.hiuni.core.push

/**
 * Reine, Firebase-freie Entscheidungslogik fürs Tickle-Handling — bewusst von
 * [HiUniMessagingService] getrennt, damit sie ohne Firebase-Runtime als
 * JVM-Unit-Test läuft.
 *
 * Das Tickle-Modell: Der Server schickt nur ein inhaltsloses „schau mal nach"-
 * Signal (`{"type":"mail_tickle"}`) — es enthält KEINE Mail-Daten. Die App
 * entscheidet lokal, ob sie darauf reagiert (Sync anstoßen) oder es still
 * verwirft (kein Login/Mail-Konto, Feature aus).
 */
object MailTickleHandler {

    /** Message-Type-Key im FCM-Data-Payload. */
    const val KEY_TYPE = "type"

    /** Der eine Data-Type, auf den wir reagieren. */
    const val TYPE_MAIL_TICKLE = "mail_tickle"

    /**
     * Entscheidung, was mit einer eingehenden FCM-Data-Message zu tun ist.
     */
    enum class Decision {
        /** Mail-Sync anstoßen (expedited Worker). */
        SYNC_MAIL,

        /** Bekannter Typ, aber Vorbedingungen fehlen (kein Login/Konto, Feature aus) — still ignorieren. */
        IGNORE_SILENTLY,

        /** Unbekannter Message-Type — nichts tun. */
        UNKNOWN_TYPE
    }

    /**
     * Vorbedingungen fürs Reagieren auf ein Tickle. Rein datenbasiert, damit der
     * Test alle Kombinationen ohne Firebase/Android durchspielen kann.
     *
     * @param pushEnabled     Master-Schalter aus den Settings (Mail-Push).
     * @param hasMailAccount  Ob IMAP-Zugangsdaten hinterlegt sind (CredentialsManager.hasCredentials()).
     */
    data class Preconditions(
        val pushEnabled: Boolean,
        val hasMailAccount: Boolean
    )

    /**
     * Entscheidet anhand des Message-Types und der Vorbedingungen, was zu tun ist.
     *
     * - Unbekannter/fehlender Type → [Decision.UNKNOWN_TYPE].
     * - `mail_tickle`, aber Feature aus oder kein Mail-Konto → [Decision.IGNORE_SILENTLY].
     * - `mail_tickle` mit erfüllten Vorbedingungen → [Decision.SYNC_MAIL].
     */
    fun decide(data: Map<String, String>, preconditions: Preconditions): Decision {
        val type = data[KEY_TYPE]
        if (type != TYPE_MAIL_TICKLE) return Decision.UNKNOWN_TYPE
        if (!preconditions.pushEnabled || !preconditions.hasMailAccount) {
            return Decision.IGNORE_SILENTLY
        }
        return Decision.SYNC_MAIL
    }
}
