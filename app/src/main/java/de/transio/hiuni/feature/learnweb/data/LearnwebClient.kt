package de.transio.hiuni.feature.learnweb.data

import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.common.Semester
import de.transio.hiuni.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Status-Probe für die Learnweb-Anbindung. `ok = true` bedeutet, dass das HTML
 * nach dem ST-Login einen authentifizierten Footer enthält (Abmelde-Link bzw.
 * den Text „Sie sind angemeldet als"). `displayedUsername` wird best-effort aus
 * der Status-Zeile extrahiert, ist also rein informativ.
 */
data class LearnwebConnectionInfo(
    val ok: Boolean,
    val statusCode: Int,
    val displayedUsername: String?,
    val errorMessage: String?
)

/**
 * End-to-End-Client für die Learnweb-Instanz der Uni Hildesheim (Moodle).
 *
 * Auth-Flow:
 * 1. CAS-Service-Ticket für die Learnweb-Login-Endpoint-URL holen
 * 2. GET `<loginServiceUrl>?ticket=…` — Moodle validiert das ST, setzt
 *    `MoodleSession`-Cookie im OkHttp-CookieJar und redirected zur Dashboard-
 *    Seite. OkHttp folgt automatisch.
 * 3. Body als HTML zurückgeben.
 *
 * Die Kurslisten-Extraktion macht der separate [LearnwebScraper].
 */
@Singleton
class LearnwebClient @Inject constructor(
    private val casSession: CasSession,
    private val httpClient: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    /**
     * Probt End-to-End: ST holen → GET mit Ticket → prüfe ob Footer
     * authentifiziert wirkt. Wirft NICHT — wir packen Fehler in das Info-Objekt,
     * damit die UI sie als Snackbar/Status darstellen kann.
     */
    suspend fun testConnection(): LearnwebConnectionInfo = withContext(io) {
        try {
            val (statusCode, html) = fetchDashboardInternal()
            if (statusCode !in 200..299) {
                return@withContext LearnwebConnectionInfo(
                    ok = false,
                    statusCode = statusCode,
                    displayedUsername = null,
                    errorMessage = "Learnweb antwortete mit HTTP $statusCode"
                )
            }
            val authed = looksAuthenticated(html)
            val displayedUsername = extractDisplayedUsername(html)
            Timber.i("Learnweb testConnection ok=$authed user=$displayedUsername status=$statusCode")
            LearnwebConnectionInfo(
                ok = authed,
                statusCode = statusCode,
                displayedUsername = displayedUsername,
                errorMessage = if (authed) null else "Login-Probe fehlgeschlagen — kein Abmelden-Link im HTML"
            )
        } catch (t: Throwable) {
            Timber.w(t, "Learnweb testConnection failed")
            LearnwebConnectionInfo(
                ok = false,
                statusCode = -1,
                displayedUsername = null,
                errorMessage = t.message ?: "Unbekannter Fehler"
            )
        }
    }

    /**
     * Liefert das vollständige Dashboard-HTML nach erfolgreichem SSO-Login.
     * Wirft, wenn der ST nicht akzeptiert wurde oder das HTTP-Ergebnis nicht
     * erfolgreich ist.
     */
    suspend fun fetchDashboardHtml(): String = withContext(io) {
        val (code, html) = fetchDashboardInternal()
        check(code in 200..299) { "Learnweb antwortete mit HTTP $code" }
        html
    }

    private suspend fun fetchDashboardInternal(): Pair<Int, String> = withContext(io) {
        val loginUrl = loginServiceUrl()
        val ticket = casSession.getServiceTicket(loginUrl)
        // Glue: ST als Query-Param. loginServiceUrl ist parameterlos
        // (login/index.php), also einfaches "?" reicht. OkHttp folgt 302
        // automatisch zur Dashboard-URL und übernimmt dabei den
        // MoodleSession-Cookie via globalem CookieJar.
        val ticketedUrl = "$loginUrl?ticket=$ticket"
        Timber.d("Learnweb fetchDashboard ST acquired, hitting $ticketedUrl")
        val initial = httpClient.newCall(Request.Builder().url(ticketedUrl).build()).execute()
        val (firstCode, firstHtml) = initial.use { it.code to it.body?.string().orEmpty() }
        // Manche Moodle-Installationen landen nach ST-Redirect auf `/login/index.php`
        // selbst (nur ein Verweis aufs Dashboard); ein zweiter GET auf die Base-URL
        // bringt dann die echten Kurs-Listen.
        if (firstCode in 200..299 && (looksAuthenticated(firstHtml) || hasCourseList(firstHtml))) {
            return@withContext firstCode to firstHtml
        }
        Timber.d("Learnweb first response code=$firstCode looksAuth=${looksAuthenticated(firstHtml)} — refetching base")
        val baseResp = httpClient.newCall(Request.Builder().url("${baseUrl()}/").build()).execute()
        baseResp.use { it.code to it.body?.string().orEmpty() }
    }

    /**
     * Robuster Auth-Indicator. Moodle-Footer trägt den Abmelde-Link mit
     * `logout.php?sesskey=…` wenn der User angemeldet ist; zusätzlich erscheint
     * im Sidebar/Header gelegentlich „Sie sind angemeldet als". Treffer auf
     * eines der beiden ⇒ authentifiziert.
     */
    private fun looksAuthenticated(html: String): Boolean {
        if (html.isBlank()) return false
        return html.contains("logout.php", ignoreCase = true) ||
            html.contains("Sie sind angemeldet als", ignoreCase = true)
    }

    private fun hasCourseList(html: String): Boolean {
        return html.contains("calendar-course-filter", ignoreCase = true) ||
            html.contains("type_course", ignoreCase = true)
    }

    /**
     * Best-Effort-Extraktion des angezeigten Usernamens aus dem
     * „Sie sind angemeldet als …"-String. Wird ausschließlich für
     * Diagnose-/UI-Anzeigen verwendet — fehlt häufig in modernen Moodle-
     * Layouts, deshalb darf das Ergebnis ruhig null bleiben.
     */
    private fun extractDisplayedUsername(html: String): String? {
        val match = ANGEMELDET_ALS_REGEX.find(html) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotBlank() }
    }

    companion object {

        /**
         * Aktuelle Learnweb-Instanz-URL, basierend auf dem heutigen Datum.
         * SS + folgendes WS teilen sich `learnwebYYYY` (Beispiel: SS 2026 +
         * WS 2026/27 → `learnweb2026`).
         */
        fun baseUrl(): String {
            val sem = Semester.fromDate(LocalDate.now())
            return "https://www.uni-hildesheim.de/learnweb${sem.learnwebYear()}"
        }

        /**
         * Moodle-Endpoint, der ST validiert und MoodleSession setzt. CAS-Login
         * mappt das Service auf exakt diese URL.
         */
        fun loginServiceUrl(): String = "${baseUrl()}/login/index.php"

        private val ANGEMELDET_ALS_REGEX =
            Regex("Sie sind angemeldet als\\s+([^<\\n\\r]+)", RegexOption.IGNORE_CASE)
    }
}
