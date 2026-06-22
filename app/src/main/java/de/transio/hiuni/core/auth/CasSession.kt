package de.transio.hiuni.core.auth

import de.transio.hiuni.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
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
    private val httpClient: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    private val _state = MutableStateFlow<CasState>(computeInitialState())
    val state: StateFlow<CasState> = _state.asStateFlow()

    fun refreshState() {
        _state.value = computeInitialState()
    }

    /** Wird von WebLoginActivity nach erfolgreichem Login gerufen. */
    fun onLoginSuccess(tgc: String, cookieHeader: String, userAgent: String, username: String?, baseUrl: String) {
        cookieStore.save(tgc, cookieHeader, userAgent, username, baseUrl)
        _state.value = CasState.Authenticated(
            username = username,
            obtainedAt = cookieStore.obtainedAt() ?: Instant.now()
        )
    }

    fun logout() {
        cookieStore.clear()
        _state.value = CasState.NeedsLogin
    }

    fun baseUrl(): String = cookieStore.baseUrl() ?: CasConfig.DEFAULT_CAS_BASE_URL

    /**
     * Holt ein Service-Ticket für [serviceUrl]. Wirft, wenn TGC fehlt oder Server ablehnt.
     * Cached intentionally NICHT — STs sind one-time-use und kurzlebig.
     */
    suspend fun getServiceTicket(serviceUrl: String): String = withContext(io) {
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
                Timber.w("CAS redirected without ?ticket= param: $location")
            }
            // Detail-Logging der Response damit wir den Fehler verstehen
            val bodyExcerpt = runCatching { it.peekBody(1024).string() }.getOrNull().orEmpty()
            Timber.w("CAS getServiceTicket failed code=$code bodyExcerpt=${bodyExcerpt.take(500)}")
            if (code == 401 || code == 403) {
                _state.update { CasState.NeedsReauth }
                throw IllegalStateException("CAS hat TGC abgelehnt (HTTP $code)")
            }
            throw IllegalStateException("Service-Ticket konnte nicht geholt werden (HTTP $code)")
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
