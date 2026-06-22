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
    fun onLoginSuccess(tgc: String, username: String?, baseUrl: String) {
        cookieStore.save(tgc, username, baseUrl)
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
        val tgc = cookieStore.tgc()
            ?: throw IllegalStateException("Kein TGC vorhanden — Login erforderlich")
        val base = baseUrl()
        val loginUrl = "$base${CasConfig.LOGIN_PATH}?service=${java.net.URLEncoder.encode(serviceUrl, "UTF-8")}"

        // Custom Client der keinen Redirects folgt — wir wollen den Location-Header lesen.
        val noRedirectClient = httpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        val request = Request.Builder()
            .url(loginUrl)
            .header("Cookie", "${CasConfig.TGC_COOKIE_NAME}=$tgc")
            .build()

        val response = noRedirectClient.newCall(request).execute()
        response.use {
            val code = it.code
            val location = it.header("Location")
            Timber.d("CAS getServiceTicket service=$serviceUrl → code=$code location=$location")
            if (code in 300..399 && location != null) {
                val ticket = location.toHttpUrl().queryParameter("ticket")
                if (!ticket.isNullOrBlank()) return@withContext ticket
            }
            if (code == 401 || code == 403) {
                _state.update { CasState.NeedsReauth }
                throw IllegalStateException("CAS hat TGC abgelehnt (HTTP $code)")
            }
            _state.update { CasState.NeedsReauth }
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
