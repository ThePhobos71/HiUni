package de.transio.hiuni.feature.lsf.data

import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class LsfConnectionInfo(
    val ok: Boolean,
    val statusCode: Int,
    val username: String?,
    val role: String?,
    val errorMessage: String?
)

@Singleton
class LsfClient @Inject constructor(
    private val casSession: CasSession,
    private val httpClient: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    /**
     * End-to-End-Probe: holt einen ST von CAS, ruft die LSF-Bootstrap-URL mit
     * `?ticket=…` auf (LSF setzt dabei JSESSIONID + redirected zur Portal-Page),
     * folgt dem Redirect und versucht den User-Namen aus der `<div class="divloginstatus">`
     * zu extrahieren — wenn das klappt, läuft die gesamte SSO-Pipeline.
     */
    suspend fun testConnection(): LsfConnectionInfo = withContext(io) {
        try {
            val ticket = casSession.getServiceTicket(LSF_LOGIN_SERVICE)
            val ticketedUrl = "$LSF_LOGIN_SERVICE&ticket=$ticket"
            Timber.i("LSF testConnection ST acquired, hitting $ticketedUrl")
            // OkHttp folgt 302 automatisch → wir landen auf der Portal-Page
            val response = httpClient.newCall(Request.Builder().url(ticketedUrl).build()).execute()
            response.use {
                val code = it.code
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    return@withContext LsfConnectionInfo(
                        ok = false,
                        statusCode = code,
                        username = null,
                        role = null,
                        errorMessage = "LSF antwortete mit HTTP $code"
                    )
                }
                val (username, role) = extractUserInfo(body)
                Timber.i("LSF testConnection success — user=$username role=$role finalUrl=${it.request.url}")
                LsfConnectionInfo(
                    ok = true,
                    statusCode = code,
                    username = username,
                    role = role,
                    errorMessage = null
                )
            }
        } catch (t: Throwable) {
            Timber.w(t, "LSF testConnection failed")
            LsfConnectionInfo(
                ok = false,
                statusCode = -1,
                username = null,
                role = null,
                errorMessage = t.message ?: "Unbekannter Fehler"
            )
        }
    }

    /**
     * Aus der LSF-Portal-HTML den User-Namen und Rolle extrahieren.
     *
     * Beobachtetes Markup (Spike 2026-05-24):
     *   <div class="divloginstatus">
     *     …
     *     Herr Kjell Heinrich Karstens
     *     &nbsp;…&nbsp;
     *     in der Rolle:
     *     Student/-in
     *   </div>
     */
    private fun extractUserInfo(html: String): Pair<String?, String?> {
        return runCatching {
            val doc = Jsoup.parse(html)
            val statusDiv = doc.selectFirst("div.divloginstatus") ?: return null to null
            val raw = statusDiv.text()
            val roleMatch = Regex("in der Rolle:\\s*([A-Za-zÄÖÜäöüß/\\- ]+)").find(raw)
            val role = roleMatch?.groupValues?.get(1)?.trim()
            // User-Name kommt vor "in der Rolle:"
            val beforeRole = if (roleMatch != null) raw.substring(0, roleMatch.range.first) else raw
            val username = beforeRole
                .substringAfter("Abmelden", "")
                .substringBefore("|")
                .replace(Regex("[\\s\\u00A0]+"), " ")
                .trim()
                .takeIf { it.isNotBlank() }
            username to role
        }.getOrElse { null to null }
    }

    companion object {
        const val LSF_BASE = "https://lsf.uni-hildesheim.de/qisserver/rds"

        /** Bootstrap-URL: triggert CAS-Login (wenn ticket fehlt) bzw. validiert ST + setzt JSESSIONID. */
        const val LSF_LOGIN_SERVICE = "$LSF_BASE?state=user&type=1"

        const val LSF_PORTAL = "$LSF_BASE?state=user&type=0"
        const val LSF_STUNDENPLAN = "$LSF_BASE?state=wplan&week=-2&act=show&pool=&show=plan&P.vx=lang&fil=plu&P.subc=plan"
    }
}
