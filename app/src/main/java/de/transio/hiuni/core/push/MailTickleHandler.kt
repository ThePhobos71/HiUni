package de.transio.hiuni.core.push

/**
 * Reine, Firebase-freie Entscheidungslogik fürs Tickle-Handling — bewusst von
 * [HiUniMessagingService] getrennt, damit sie ohne Firebase-Runtime als
 * JVM-Unit-Test läuft.
 *
 * Das Tickle-Modell: Der Server schickt nur ein inhaltsloses „schau mal nach"-
 * Signal — es enthält KEINE Nutzdaten. Die App entscheidet lokal, ob sie darauf
 * reagiert (Sync anstoßen) oder es still verwirft (kein Login/Mail-Konto, Feature
 * aus).
 *
 * Zwei Tickle-Typen:
 *  - [TYPE_MAIL_TICKLE] (`mail_tickle`): reiner Mail-Wecker. Nur ein expedited
 *    Mail-Refresh. Bleibt für Abwärtskompatibilität mit alten Server-Deploys
 *    unverändert erhalten.
 *  - [TYPE_SYNC_TICKLE] (`sync_tickle`): generelles Sync-Signal. Macht ALLES, was
 *    `mail_tickle` macht (Mail-Refresh), UND stößt danach den
 *    [de.transio.hiuni.core.sync.PrefetchOrchestrator] an. Dessen TTL-/Auth-/
 *    Offline-Gates verhindern, dass alle 15 min voll gescrapt wird.
 */
object MailTickleHandler {

    /** Message-Type-Key im FCM-Data-Payload. */
    const val KEY_TYPE = "type"

    /** Reiner Mail-Wecker (Legacy — alte Server-Deploys senden das). */
    const val TYPE_MAIL_TICKLE = "mail_tickle"

    /** Generelles Sync-Signal: Mail-Refresh + gestaffelter Prefetch aller Features. */
    const val TYPE_SYNC_TICKLE = "sync_tickle"

    /**
     * Entscheidung, was mit einer eingehenden FCM-Data-Message zu tun ist.
     */
    enum class Decision {
        /** Nur Mail-Sync anstoßen (expedited Worker). */
        SYNC_MAIL,

        /** Mail-Sync anstoßen UND danach den Feature-Prefetch triggern. */
        SYNC_ALL,

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
     * - `mail_tickle`/`sync_tickle`, aber Feature aus oder kein Mail-Konto →
     *   [Decision.IGNORE_SILENTLY].
     * - `mail_tickle` mit erfüllten Vorbedingungen → [Decision.SYNC_MAIL].
     * - `sync_tickle` mit erfüllten Vorbedingungen → [Decision.SYNC_ALL].
     */
    fun decide(data: Map<String, String>, preconditions: Preconditions): Decision {
        val type = data[KEY_TYPE]
        if (type != TYPE_MAIL_TICKLE && type != TYPE_SYNC_TICKLE) return Decision.UNKNOWN_TYPE
        if (!preconditions.pushEnabled || !preconditions.hasMailAccount) {
            return Decision.IGNORE_SILENTLY
        }
        return if (type == TYPE_SYNC_TICKLE) Decision.SYNC_ALL else Decision.SYNC_MAIL
    }
}
