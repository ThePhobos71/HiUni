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

    /**
     * Initial-Login-URL für WebView. Mit `service=` Parameter damit CAS nach Success
     * auf die Service-Page (z.B. LSF) redirected — sonst bleibt CAS auf `/sso/login`
     * mit "Sie sind angemeldet"-Page und unsere URL-basierte Detection greift nicht.
     */
    const val BOOTSTRAP_SERVICE_URL = "https://lsf.uni-hildesheim.de/qisserver/rds?state=user&type=1"

    fun initialLoginUrl(baseUrl: String): String {
        val encoded = java.net.URLEncoder.encode(BOOTSTRAP_SERVICE_URL, "UTF-8")
        return "$baseUrl$LOGIN_PATH?service=$encoded"
    }
}
