package de.transio.hiuni.feature.learnweb.data

import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.common.Semester
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
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
    private val settings: SettingsDataStore,
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

    /**
     * Liefert das HTML der Moodle-„Upcoming Events"-Calendar-Ansicht. Diese
     * Ansicht listet alle anstehenden Calendar-Events (alle Kurse, größeres
     * Zeitfenster) — Dashboard zeigt nur die nächsten ~30 Tage im Mini-Kalender.
     *
     * Setzt voraus, dass die [MoodleSession] bereits via `fetchDashboardHtml()`
     * im OkHttp-CookieJar liegt. Wirft, wenn Moodle 4xx/5xx antwortet.
     */
    suspend fun fetchUpcomingHtml(): String = withContext(io) {
        val url = "${baseUrl()}/calendar/view.php?view=upcoming"
        Timber.d("Learnweb fetchUpcoming hitting $url")
        val resp = httpClient.newCall(Request.Builder().url(url).build()).execute()
        val (code, html) = resp.use { it.code to it.body?.string().orEmpty() }
        check(code in 200..299) { "Learnweb /calendar/view.php?view=upcoming antwortete mit HTTP $code" }
        html
    }

    /**
     * Holt die Assignment-Detail-Seite (`mod/assign/view.php?id=<cmId>`). Nur
     * auf dieser Seite rendert Moodle den Submission-Status-Block (Tabelle
     * `generaltable`) — Kalender + Upcoming-Liste liefern lediglich Title und
     * Deadline.
     *
     * `cmId` ist die Course-Module-ID, NICHT die Calendar-Event-ID. Wir parsen
     * sie aus der im Calendar gespeicherten URL — siehe
     * [LearnwebRepositoryImpl.parseCmIdFromUrl].
     *
     * Setzt voraus, dass die MoodleSession bereits im OkHttp-CookieJar liegt
     * (z.B. nach vorherigem `fetchDashboardHtml()`). Wirft, wenn Moodle 4xx/5xx
     * antwortet.
     */
    suspend fun fetchAssignmentDetailHtml(cmId: Long): String = withContext(io) {
        val url = "${baseUrl()}/mod/assign/view.php?id=$cmId"
        Timber.d("Learnweb fetchAssignmentDetail hitting $url")
        val resp = httpClient.newCall(Request.Builder().url(url).build()).execute()
        val (code, html) = resp.use { it.code to it.body?.string().orEmpty() }
        check(code in 200..299) {
            "Learnweb /mod/assign/view.php?id=$cmId antwortete mit HTTP $code"
        }
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

    // ---- Phase 4: iCal-Subscription-Feed --------------------------------

    /**
     * Liefert den `authtoken`-Wert für den Moodle-iCal-Subscription-Feed.
     *
     * Strategie:
     * 1. Persistierten Token aus dem DataStore lesen — wenn vorhanden und
     *    [forceRenew] = false, direkt zurückgeben.
     * 2. Sonst: CAS-SSO sicherstellen (`fetchDashboardHtml()` etabliert die
     *    `MoodleSession`-Cookie), dann GET `/calendar/export.php` und im HTML
     *    den Token extrahieren. Sucht primär in einem `<input name="authtoken">`-
     *    Field (Wizard-Form); fällt zurück auf `authtoken=…`-URL-Parameter, falls
     *    Moodle direkt einen Subscribe-Link im HTML rendert.
     * 3. Token persistieren und zurückgeben.
     *
     * Bei Fehler (HTTP-Code != 2xx oder Token nicht findbar) wird `null`
     * zurückgegeben; die Aufrufer behandeln das als „iCal-Quelle aktuell nicht
     * verfügbar" und überspringen den Sync-Schritt.
     */
    suspend fun ensureICalToken(forceRenew: Boolean = false): String? = withContext(io) {
        if (!forceRenew) {
            val cached = settings.learnwebICalToken.first()
            if (cached.isNotBlank()) {
                Timber.d("Learnweb ensureICalToken: cached")
                return@withContext cached
            }
        }
        try {
            // CAS-SSO etablieren, damit MoodleSession-Cookie im Jar liegt.
            // fetchDashboardHtml() macht das implizit — wir verwerfen das HTML,
            // brauchen es hier nicht.
            fetchDashboardHtml()
            val url = "${baseUrl()}/calendar/export.php"
            Timber.d("Learnweb ensureICalToken: hitting $url")
            val resp = httpClient.newCall(Request.Builder().url(url).build()).execute()
            val (code, html) = resp.use { it.code to it.body?.string().orEmpty() }
            if (code !in 200..299) {
                Timber.w("Learnweb ensureICalToken: HTTP $code beim Holen der Export-Page")
                return@withContext null
            }
            val token = extractAuthToken(html)
            if (token.isNullOrBlank()) {
                Timber.w("Learnweb ensureICalToken: kein authtoken im HTML gefunden (len=${html.length})")
                return@withContext null
            }
            settings.setLearnwebICalToken(token)
            Timber.i("Learnweb ensureICalToken: Token erworben (len=${token.length})")
            token
        } catch (t: Throwable) {
            Timber.w(t, "Learnweb ensureICalToken: Token-Beschaffung fehlgeschlagen")
            null
        }
    }

    /**
     * Lädt den iCal-Subscription-Feed des Users (nur User-relevante Events) als
     * String. Holt sich [ensureICalToken] selbst und retried genau einmal mit
     * `forceRenew = true`, falls Moodle 401/403 (Token abgelaufen) antwortet.
     *
     * Gibt `null` zurück, wenn kein Token erreichbar war oder der Feed wiederholt
     * mit Fehler antwortet — Aufrufer überspringen den Sync-Schritt dann
     * stillschweigend.
     */
    suspend fun fetchICalFeed(): String? = withContext(io) {
        val initialToken = ensureICalToken(forceRenew = false) ?: return@withContext null
        when (val firstAttempt = fetchICalFeedWithToken(initialToken)) {
            is ICalFetchResult.Ok -> firstAttempt.body
            is ICalFetchResult.Unauthorized -> {
                Timber.i("Learnweb fetchICalFeed: 401/403 — Token abgelaufen, erneuere und retrye")
                settings.setLearnwebICalToken("")
                val renewed = ensureICalToken(forceRenew = true) ?: return@withContext null
                (fetchICalFeedWithToken(renewed) as? ICalFetchResult.Ok)?.body
            }
            is ICalFetchResult.Error -> {
                Timber.w("Learnweb fetchICalFeed: HTTP ${firstAttempt.code}, gebe auf")
                null
            }
        }
    }

    private suspend fun fetchICalFeedWithToken(token: String): ICalFetchResult = withContext(io) {
        // events[user]=1 schränkt den Feed auf direkt-zugewiesene User-Events ein.
        // events[course]/[group]/[category] bewusst nicht gesetzt — Moodle macht
        // dann das Sinnvolle (alle Kurs-Termine des Users) und der Feed bleibt
        // nicht leer wegen explizit-0-Filtern.
        val url = "${baseUrl()}/calendar/export.php" +
            "?action=export_execute" +
            "&authtoken=$token" +
            "&events%5Buser%5D=1"
        Timber.d("Learnweb fetchICalFeed: hitting $url")
        val resp = httpClient.newCall(Request.Builder().url(url).build()).execute()
        resp.use {
            val code = it.code
            when {
                code in 200..299 -> ICalFetchResult.Ok(it.body?.string().orEmpty())
                code == 401 || code == 403 -> ICalFetchResult.Unauthorized
                else -> ICalFetchResult.Error(code)
            }
        }
    }

    /**
     * Findet den `authtoken`-Wert im HTML der Calendar-Export-Page. Suchstrategie
     * in dieser Reihenfolge:
     *
     * 1. `<input ... name="authtoken" ... value="XYZ">` — Standard-Wizard-Form
     * 2. `authtoken=XYZ` in irgendeinem `href`/`src`/`value`-Kontext — Fallback,
     *    falls Moodle einen fertigen Subscribe-Link im HTML rendert
     *
     * Verwendet bewusst Regex statt Jsoup, damit wir auf beide Layouts robust
     * matchen — Moodle-HTML variiert je nach Theme/Version stark.
     */
    private fun extractAuthToken(html: String): String? {
        if (html.isBlank()) return null
        AUTHTOKEN_INPUT_REGEXES.forEach { regex ->
            regex.find(html)?.let { match ->
                val value = match.groupValues.getOrNull(1)?.trim()
                if (!value.isNullOrBlank()) return value
            }
        }
        AUTHTOKEN_URL_PARAM_REGEX.find(html)?.let { match ->
            val value = match.groupValues.getOrNull(1)?.trim()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    /** Internes Ergebnis-Triage für [fetchICalFeedWithToken]. */
    private sealed class ICalFetchResult {
        data class Ok(val body: String) : ICalFetchResult()
        object Unauthorized : ICalFetchResult()
        data class Error(val code: Int) : ICalFetchResult()
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

        /**
         * Matches `<input ... name="authtoken" ... value="XYZ">` in beiden
         * Reihenfolge-Varianten (`name` vor `value`, oder umgekehrt). HTML-
         * Attribute können in Moodle einfach- oder doppel-gequoted sein.
         */
        private val AUTHTOKEN_INPUT_REGEXES = listOf(
            Regex(
                "<input[^>]*\\bname=[\"']authtoken[\"'][^>]*\\bvalue=[\"']([^\"']+)[\"']",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "<input[^>]*\\bvalue=[\"']([^\"']+)[\"'][^>]*\\bname=[\"']authtoken[\"']",
                RegexOption.IGNORE_CASE
            )
        )

        /**
         * Fallback: `authtoken=XYZ` in URL-Parameter-Kontext (href/src/value).
         * Konservativ: Token sind hex-ähnlich (a-f, 0-9) und mindestens 16 Zeichen,
         * was uns vor False-Positives in CSS/JS-Inline-Texten schützt.
         */
        private val AUTHTOKEN_URL_PARAM_REGEX =
            Regex("authtoken=([A-Za-z0-9]{16,})", RegexOption.IGNORE_CASE)
    }
}
