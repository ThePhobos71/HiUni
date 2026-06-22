package de.transio.hiuni.core.auth

import de.transio.hiuni.core.security.CredentialsManager
import de.transio.hiuni.di.ApplicationScope
import de.transio.hiuni.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CasState {
    object NeedsLogin : CasState
    data class Authenticated(val username: String?, val obtainedAt: Instant) : CasState
    object NeedsReauth : CasState
}

/**
 * Holt Service-Tickets (ST) für einzelne Backend-Services. Verwendet das in
 * [CasCookieStore] persistierte TGC, das via [WebLoginActivity] beim Erst-Login
 * extrahiert wurde.
 *
 * Service-Ticket-Acquisition via HTML-Flow (funktioniert in jeder CAS-Konfiguration):
 *   GET <casBase>/login?service=<serviceUrl>   mit TGC-Cookie
 *   → 302 Location: <serviceUrl>?ticket=ST-...
 *
 * Wird per `followRedirects(false)` abgefangen damit wir das Ticket aus dem
 * Location-Header extrahieren können, statt blind weiterzuleiten.
 */
@Singleton
class CasSession @Inject constructor(
    private val cookieStore: CasCookieStore,
    private val credentialsManager: CredentialsManager,
    private val httpClient: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope
) {

    private val _state = MutableStateFlow<CasState>(computeInitialState())
    val state: StateFlow<CasState> = _state.asStateFlow()

    private val _profile = MutableStateFlow(cookieStore.profile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    fun refreshState() {
        _state.value = computeInitialState()
        _profile.value = cookieStore.profile()
    }

    /** Wird von WebLoginActivity nach erfolgreichem Login gerufen. */
    fun onLoginSuccess(tgc: String, cookieHeader: String, userAgent: String, username: String?, baseUrl: String) {
        cookieStore.save(tgc, cookieHeader, userAgent, username, baseUrl)
        _state.value = CasState.Authenticated(
            username = username,
            obtainedAt = cookieStore.obtainedAt() ?: Instant.now()
        )
        // Profile gleich nachladen — Vorname/Nachname etc. werden auf der CAS-Success-
        // Page als Attribute-Table angezeigt.
        appScope.launch {
            val profile = runCatching { fetchUserProfile() }.getOrDefault(UserProfile.EMPTY)
            if (profile.uid != null || profile.vorname != null) {
                cookieStore.saveProfile(profile)
                _profile.value = profile
            }
        }
    }

    fun logout() {
        cookieStore.clear()
        _state.value = CasState.NeedsLogin
        _profile.value = UserProfile.EMPTY
    }

    fun baseUrl(): String = cookieStore.baseUrl() ?: CasConfig.DEFAULT_CAS_BASE_URL

    /**
     * Holt ein Service-Ticket für [serviceUrl]. Wirft, wenn TGC fehlt oder Server ablehnt.
     * Cached intentionally NICHT — STs sind one-time-use und kurzlebig.
     *
     * Bei abgelaufenem TGT versucht die Methode genau EIN Mal silent zu renewen,
     * sofern Username + Passwort aus dem WebView-Login persistiert wurden — sonst
     * landen wir in [CasState.NeedsReauth] und der User muss den WebView neu öffnen.
     */
    suspend fun getServiceTicket(serviceUrl: String): String = withContext(io) {
        try {
            return@withContext fetchServiceTicket(serviceUrl)
        } catch (firstAttemptFailure: TgtRejectedException) {
            Timber.i("CAS TGT abgelaufen — versuche Silent-Renewal mit gespeicherter RZ-Kennung")
            val renewed = silentRenew(serviceUrl)
            if (renewed != null) {
                Timber.i("Silent-Renewal erfolgreich, returning ticket from POST-Response")
                return@withContext renewed
            }
            // Renewal lieferte selbst kein Ticket (oder Service hat nicht direkt
            // redirected) — Cookies sind aber neu, also noch ein ST-Versuch.
            try {
                return@withContext fetchServiceTicket(serviceUrl)
            } catch (secondAttemptFailure: TgtRejectedException) {
                _state.update { CasState.NeedsReauth }
                throw IllegalStateException(
                    "CAS-Login abgelaufen und Silent-Renewal fehlgeschlagen — bitte erneut anmelden",
                    secondAttemptFailure
                )
            }
        }
    }

    private suspend fun fetchServiceTicket(serviceUrl: String): String = withContext(io) {
        val cookieHeader = cookieStore.cookieHeader()
            ?: cookieStore.tgc()?.let { "${CasConfig.TGC_COOKIE_NAME}=$it" }
            ?: throw IllegalStateException("Keine CAS-Session — Login erforderlich")
        val base = baseUrl()

        // Manuelle Encoding-Strategie matched den Spike-Format: nur `?` und `&` im
        // Service-URL escapen, `://` und `/` so lassen. OkHttp's addQueryParameter
        // escapt Slashes mit, was Apache CAS verwirrt.
        // Spike-Encoding: `&`, `?`, `=` werden escaped. `://` und `/` bleiben unverändert.
        val encodedService = serviceUrl
            .replace("&", "%26")
            .replace("?", "%3F")
            .replace("=", "%3D")
        // gateway=true: CAS validiert nur TGC, zeigt KEINEN Login-Form.
        // Bei gültigem TGC → 302 mit ticket; bei invalidem TGC → 302 ohne ticket.
        val loginUrl = "$base${CasConfig.LOGIN_PATH}?service=$encodedService&gateway=true"

        Timber.d("CAS getServiceTicket loginUrl=$loginUrl cookieLen=${cookieHeader.length}")

        val noRedirectClient = httpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .build()

        // KRITISCH: CAS bindet das TGC an den User-Agent des Browsers der es bekommen hat.
        // Wir MÜSSEN den WebView-UA replayen — sonst löscht CAS das TGC (Set-Cookie Max-Age=0).
        val storedUa = cookieStore.userAgent()
        val builder = Request.Builder()
            .url(loginUrl)
            .header("Cookie", cookieHeader)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        if (!storedUa.isNullOrBlank()) {
            builder.header("User-Agent", storedUa)
            Timber.d("CAS using stored WebView-UA: ${storedUa.take(60)}…")
        } else {
            Timber.w("CAS: no stored User-Agent — TGC validation will likely fail. Re-login required.")
        }
        val request = builder.build()

        // DIAGNOSTIK: zeige was wir actually senden
        val tgcInCookies = cookieHeader.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("TGC=") }
            ?.let { "TGC[${it.length - 4}ch:${it.substring(4, 14)}…${it.takeLast(8)}]" }
            ?: "TGC[MISSING]"
        val otherCookieNames = cookieHeader.split(';')
            .map { it.trim().substringBefore('=') }
            .filter { it.isNotBlank() && it != "TGC" }
        Timber.d("CAS request cookies: $tgcInCookies plus $otherCookieNames")

        val response = noRedirectClient.newCall(request).execute()
        response.use {
            val code = it.code
            val location = it.header("Location")
            Timber.d("CAS getServiceTicket → code=$code location=$location respCookies=${it.headers("Set-Cookie")}")
            if (code in 300..399 && location != null) {
                val ticket = location.toHttpUrl().queryParameter("ticket")
                if (!ticket.isNullOrBlank()) return@withContext ticket
                // 302 ohne Ticket = CAS hat das TGC verworfen (typisch wenn der Server
                // gleichzeitig per Set-Cookie TGC=...; Max-Age=0 löscht).
                Timber.w("CAS redirected without ?ticket= param: $location — TGT abgelaufen?")
                throw TgtRejectedException("CAS-Redirect ohne Ticket — TGT vermutlich abgelaufen")
            }
            val bodyExcerpt = runCatching { it.peekBody(1024).string() }.getOrNull().orEmpty()
            Timber.w("CAS getServiceTicket failed code=$code bodyExcerpt=${bodyExcerpt.take(500)}")
            if (code == 401 || code == 403) {
                throw TgtRejectedException("CAS hat TGC abgelehnt (HTTP $code)")
            }
            throw IllegalStateException("Service-Ticket konnte nicht geholt werden (HTTP $code)")
        }
    }

    private class TgtRejectedException(message: String) : RuntimeException(message)

    /**
     * Holt eine neue CAS-Session via POST an `/sso/login` mit den beim WebView-Login
     * abgefangenen RZ-Credentials. Bei Erfolg wird das neue TGC im CookieStore
     * abgelegt und — falls der POST direkt mit einem Service-Redirect endet — das
     * Service-Ticket aus dem Location-Header zurückgegeben.
     *
     * `null` heißt: Renewal nicht möglich (keine Creds, falsche Creds, 2FA-Page).
     * Der Caller fällt dann in den User-Re-Auth-Pfad.
     */
    private suspend fun silentRenew(serviceUrl: String): String? = withContext(io) {
        val username = credentialsManager.getUsername()
        val password = credentialsManager.getPassword()
        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            Timber.w("Silent-Renewal nicht möglich — keine gespeicherten Credentials")
            return@withContext null
        }
        val base = baseUrl()
        val encodedService = serviceUrl
            .replace("&", "%26")
            .replace("?", "%3F")
            .replace("=", "%3D")
        val loginUrl = "$base${CasConfig.LOGIN_PATH}?service=$encodedService"
        val storedUa = cookieStore.userAgent().orEmpty().ifBlank { "Mozilla/5.0 (Linux; Android)" }

        val noRedirectClient = httpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .build()

        // 1) GET Login-Page für `execution`-Token + initial JSESSIONID-Cookie.
        val getReq = Request.Builder()
            .url(loginUrl)
            .header("User-Agent", storedUa)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()
        val getResp = noRedirectClient.newCall(getReq).execute()
        val getCookies = getResp.headers("Set-Cookie").mapNotNull { it.substringBefore(';').takeIf(String::isNotBlank) }
        val getBody = getResp.use { it.body?.string().orEmpty() }
        val execution = Jsoup.parse(getBody)
            .selectFirst("input[name=execution]")
            ?.attr("value")
            ?.takeIf { it.isNotBlank() }
        if (execution.isNullOrBlank()) {
            Timber.w("Silent-Renewal: kein execution-Token in Login-Page gefunden — evtl. 2FA / Captcha")
            return@withContext null
        }

        // 2) POST credentials.
        val form = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .add("execution", execution)
            .add("_eventId", "submit")
            .add("geolocation", "")
            .build()
        val postCookieHeader = getCookies.joinToString("; ")
        val postReq = Request.Builder()
            .url(loginUrl)
            .post(form)
            .header("User-Agent", storedUa)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .apply { if (postCookieHeader.isNotBlank()) header("Cookie", postCookieHeader) }
            .build()
        val postResp = noRedirectClient.newCall(postReq).execute()
        val postSetCookies = postResp.headers("Set-Cookie")
        val location = postResp.header("Location")
        val postBodyExcerpt = runCatching { postResp.peekBody(512).string() }.getOrNull().orEmpty()
        postResp.close()
        Timber.d("Silent-Renewal POST → code=${postResp.code} location=$location setCookies=$postSetCookies")

        // TGC aus Set-Cookies extrahieren.
        val tgcCookie = postSetCookies
            .firstOrNull { it.startsWith("${CasConfig.TGC_COOKIE_NAME}=") }
        val tgc = tgcCookie
            ?.substringAfter('=')
            ?.substringBefore(';')
            ?.takeIf { it.isNotBlank() }
        if (tgc.isNullOrBlank()) {
            // Kein TGC = Login wurde verworfen (falsches Passwort, 2FA, abgelaufener execution-Token).
            Timber.w("Silent-Renewal: kein TGC in POST-Response — Login wurde verworfen. bodyExcerpt=${postBodyExcerpt.take(300)}")
            return@withContext null
        }

        // Komplettes Cookie-Snapshot bauen für spätere ST-Requests.
        val mergedCookies = (getCookies + postSetCookies.mapNotNull { it.substringBefore(';').takeIf(String::isNotBlank) })
            // Dedupe-by-name: behalte den jeweils letzten Eintrag pro Cookie-Name.
            .reversed()
            .distinctBy { it.substringBefore('=') }
            .reversed()
        val mergedCookieHeader = mergedCookies.joinToString("; ")

        cookieStore.save(
            tgc = tgc,
            cookieHeader = mergedCookieHeader,
            userAgent = storedUa,
            username = username,
            baseUrl = base
        )
        _state.update {
            CasState.Authenticated(
                username = username,
                obtainedAt = cookieStore.obtainedAt() ?: Instant.now()
            )
        }

        // Ticket aus Location ziehen (falls CAS direkt zum Service redirected hat).
        if (location != null) {
            runCatching { location.toHttpUrl().queryParameter("ticket") }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { return@withContext it }
        }
        null
    }

    /**
     * Holt die Attribute-Page von CAS (`GET /sso/login` ohne service-Param zeigt
     * die "Anmeldung erfolgreich"-Seite mit User-Attributen wenn TGC gültig ist).
     * Parsed `#divPrincipalAttributes table` und extrahiert Vorname/Nachname/etc.
     */
    suspend fun fetchUserProfile(): UserProfile = withContext(io) {
        val cookieHeader = cookieStore.cookieHeader()
            ?: cookieStore.tgc()?.let { "${CasConfig.TGC_COOKIE_NAME}=$it" }
            ?: throw IllegalStateException("Keine CAS-Session — Login erforderlich")
        val url = "${baseUrl()}${CasConfig.LOGIN_PATH}"

        val noRedirectClient = httpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .build()

        val builder = Request.Builder()
            .url(url)
            .header("Cookie", cookieHeader)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        cookieStore.userAgent()?.takeIf { it.isNotBlank() }?.let { builder.header("User-Agent", it) }
        val response = noRedirectClient.newCall(builder.build()).execute()
        response.use {
            if (!it.isSuccessful) {
                Timber.w("CAS fetchUserProfile failed code=${it.code}")
                return@withContext UserProfile.EMPTY
            }
            val html = it.body?.string().orEmpty()
            val profile = parseUserAttributes(html)
            Timber.i("CAS fetched profile: uid=${profile.uid} vorname=${profile.vorname} nachname=${profile.nachname}")
            profile
        }
    }

    private fun parseUserAttributes(html: String): UserProfile {
        return runCatching {
            val doc = Jsoup.parse(html)
            // Principal-Tab enthält die Identity-Attribute
            val rows = doc.select("#divPrincipalAttributes table tbody tr")
            val map = mutableMapOf<String, String>()
            for (row in rows) {
                val cells = row.select("td")
                if (cells.size < 2) continue
                val key = cells[0].text().trim()
                val rawValue = cells[1].text().trim()
                val value = rawValue.removePrefix("[").removeSuffix("]").trim()
                if (key.isNotBlank() && value.isNotBlank()) map[key] = value
            }
            UserProfile(
                uid = map["uid"] ?: map["username"],
                vorname = map["Vorname"],
                nachname = map["Nachname"],
                fullName = map["Name"],
                mail = map["Mail"],
                matrikel = map["mtknr"]
            )
        }.getOrElse {
            Timber.w(it, "parseUserAttributes failed")
            UserProfile.EMPTY
        }
    }

    private fun computeInitialState(): CasState {
        if (!cookieStore.hasSession()) return CasState.NeedsLogin
        val obtained = cookieStore.obtainedAt() ?: return CasState.NeedsReauth
        return CasState.Authenticated(
            username = cookieStore.username(),
            obtainedAt = obtained
        )
    }
}
