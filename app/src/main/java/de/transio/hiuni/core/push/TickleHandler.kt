package de.transio.hiuni.core.push

/**
 * Reine, Firebase-freie Entscheidungslogik fürs Tickle-Handling, bewusst von
 * [HiUniMessagingService] getrennt, damit sie ohne Firebase-Runtime als
 * JVM-Unit-Test läuft.
 *
 * Das Tickle-Modell: Der Server schickt nur ein inhaltsloses „schau mal nach"-
 * Signal, es enthält KEINE Nutzdaten. Die App entscheidet lokal, ob sie darauf
 * reagiert (Sync anstoßen) oder es still verwirft (Feature aus).
 *
 * Zwei Tickle-Typen:
 *  - [TYPE_MAIL_TICKLE] (`mail_tickle`): reiner Mail-Wecker (Legacy, alte
 *    Server-Deploys senden das). Ohne Mail-Konto gibt es nichts zu tun.
 *  - [TYPE_SYNC_TICKLE] (`sync_tickle`): generelles Sync-Signal. Stößt immer den
 *    [de.transio.hiuni.core.sync.PrefetchOrchestrator] an (Noten, Learnweb,
 *    Mensa, …) und zusätzlich den Mail-Refresh, falls ein Mail-Konto existiert.
 *    Die TTL-/Auth-/Offline-Gates des Orchestrators verhindern, dass alle
 *    15 min voll gescrapt wird.
 */
object TickleHandler {

    /** Message-Type-Key im FCM-Data-Payload. */
    const val KEY_TYPE = "type"

    /** Reiner Mail-Wecker (Legacy). */
    const val TYPE_MAIL_TICKLE = "mail_tickle"

    /** Generelles Sync-Signal: Feature-Prefetch, plus Mail-Refresh mit Mail-Konto. */
    const val TYPE_SYNC_TICKLE = "sync_tickle"

    /**
     * Entscheidung, was mit einer eingehenden FCM-Data-Message zu tun ist.
     */
    enum class Decision {
        /** Nur Mail-Sync anstoßen (mail_tickle). */
        SYNC_MAIL,

        /** Mail-Sync UND Feature-Prefetch (sync_tickle mit Mail-Konto). */
        SYNC_ALL,

        /** Nur Feature-Prefetch (sync_tickle ohne Mail-Konto). */
        PREFETCH_ONLY,

        /** Bekannter Typ, aber Vorbedingungen fehlen (Feature aus, bzw. mail_tickle ohne Konto): still ignorieren. */
        IGNORE_SILENTLY,

        /** Unbekannter Message-Type: nichts tun. */
        UNKNOWN_TYPE
    }

    /**
     * Vorbedingungen fürs Reagieren auf ein Tickle. Rein datenbasiert, damit der
     * Test alle Kombinationen ohne Firebase/Android durchspielen kann.
     *
     * @param pushEnabled     Master-Schalter aus den Settings (Push-Sync).
     * @param hasMailAccount  Ob IMAP-Zugangsdaten hinterlegt sind (CredentialsManager.hasCredentials()).
     */
    data class Preconditions(
        val pushEnabled: Boolean,
        val hasMailAccount: Boolean
    )

    /** Ob die Message ein Tickle ist, das wir kennen (unabhängig von Vorbedingungen). */
    fun isTickle(data: Map<String, String>): Boolean =
        data[KEY_TYPE] == TYPE_MAIL_TICKLE || data[KEY_TYPE] == TYPE_SYNC_TICKLE

    /**
     * Entscheidet anhand des Message-Types und der Vorbedingungen, was zu tun ist.
     *
     * - Unbekannter/fehlender Type → [Decision.UNKNOWN_TYPE].
     * - Feature aus → [Decision.IGNORE_SILENTLY].
     * - `mail_tickle`: mit Mail-Konto [Decision.SYNC_MAIL], sonst [Decision.IGNORE_SILENTLY].
     * - `sync_tickle`: mit Mail-Konto [Decision.SYNC_ALL], sonst [Decision.PREFETCH_ONLY].
     */
    fun decide(data: Map<String, String>, preconditions: Preconditions): Decision {
        val type = data[KEY_TYPE]
        if (!isTickle(data)) return Decision.UNKNOWN_TYPE
        if (!preconditions.pushEnabled) return Decision.IGNORE_SILENTLY
        return when (type) {
            TYPE_SYNC_TICKLE ->
                if (preconditions.hasMailAccount) Decision.SYNC_ALL else Decision.PREFETCH_ONLY
            else ->
                if (preconditions.hasMailAccount) Decision.SYNC_MAIL else Decision.IGNORE_SILENTLY
        }
    }

    /**
     * FCM stuft High-Priority-Messages herab, wenn sie aus Sicht des Systems zu
     * selten zu einer sichtbaren Notification führen. Erkennbar daran, dass die
     * gesendete Priorität ([originalPriority]) höher ist als die zugestellte
     * ([deliveredPriority]). Werte sind `RemoteMessage.PRIORITY_*`
     * (UNKNOWN = 0, HIGH = 1, NORMAL = 2).
     */
    fun isDeprioritized(originalPriority: Int, deliveredPriority: Int): Boolean =
        originalPriority == PRIORITY_HIGH && deliveredPriority != PRIORITY_HIGH

    /** Spiegel von `RemoteMessage.PRIORITY_HIGH`, damit die Logik Firebase-frei bleibt. */
    internal const val PRIORITY_HIGH = 1
}
