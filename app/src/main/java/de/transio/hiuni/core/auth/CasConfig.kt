package de.transio.hiuni.core.auth

/**
 * Apereo CAS Server für Uni Hildesheim, validiert via Spike (2026-05-24).
 *
 * Beobachtetes Flow für Service-Ticket-Acquisition:
 *   GET  https://www.uni-hildesheim.de/sso/login?service=<urlencoded>
 *        → HTML mit Hidden Field "execution" (langer JWT-Token)
 *   POST https://www.uni-hildesheim.de/sso/login?service=<urlencoded>
 *        Body: username, password, execution, _eventId=submit, geolocation=
 *        → 302 Location: <service>?ticket=ST-…cas-p01
 *        → Set-Cookie: TGC=<JWT>; Path=/sso; HttpOnly; Secure; SameSite=None
 *
 * Wichtig: Cookie-Path ist `/sso`, NICHT `/` — bei der CookieManager-Extraction
 * im WebView brauchen wir die URL mit `/sso` Path-Prefix.
 */
object CasConfig {
    const val DEFAULT_CAS_BASE_URL = "https://www.uni-hildesheim.de/sso"

    const val LOGIN_PATH = "/login"
    const val LOGOUT_PATH = "/logout"

    /** Cookie-Name des Ticket-Granting-Cookies. */
    const val TGC_COOKIE_NAME = "TGC"

    /**
     * Detection: solange wir noch auf der `/sso/login`-Page sind, ist Login noch nicht durch.
     * Nach Login springt CAS via 302 zu `<service>?ticket=…` — dann sehen wir eine andere URL
     * und können das TGC aus dem CookieManager ziehen.
     */
    fun isLoginUrl(url: String?): Boolean =
        url != null && url.contains("/sso/login")
}
