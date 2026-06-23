package de.transio.hiuni.feature.bib.data

import de.transio.hiuni.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dünner HTTP-Wrapper für die Bib-Buchungs-API. Kein Caching, kein Parsing —
 * das macht das Repository. Wir sammeln Antworten als Strings und liefern sie
 * weiter; Diskriminierung zwischen "Login abgelaufen" und "Server-Fehler"
 * passiert anhand des Status-Codes + Location-Headers (Redirect zu CAS).
 */
@Singleton
class BibClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val session: BibSession,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    /**
     * Bare-Bones HTTP-Client für ubwww — ohne globalen CookieJar.
     * Wir senden Cookies ausschließlich über den expliziten `Cookie`-Header,
     * sonst kann der App-CookieJar (von früheren Anonym-Calls oder anderen
     * Features) unsere PHPSESSID still überschreiben → Server sieht uns als
     * Anonym → eigene Buchungen werden als BOOKED statt OWN_BOOKING geliefert.
     */
    private fun bareClient(): OkHttpClient = httpClient.newBuilder()
        .followRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .build()

    /** Index-HTML, anonym (anonymous = ohne unsere PHPSESSID). */
    suspend fun fetchIndexHtmlAnonymous(): String = withContext(io) {
        val request = Request.Builder()
            .url(BibConfig.INDEX_URL)
            .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            .build()
        bareClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Bib index.php HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
    }

    /**
     * Index-HTML mit eingeloggter PHPSESSID — enthält OWN_BOOKING-Zellen.
     * Bei Session-Verlust einmal Auto-Reauth.
     */
    suspend fun fetchIndexHtmlAuthenticated(): String = withSession { phpSessId ->
        val request = Request.Builder()
            .url(BibConfig.INDEX_URL_AUTHENTICATED)
            .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            .header("Cookie", "PHPSESSID=$phpSessId; gap_language=de")
            .header("Referer", BibConfig.INDEX_URL_AUTHENTICATED)
            .build()
        val resp = bareClient().newCall(request).execute()
        resp.use {
            val body = it.body?.string().orEmpty()
            AuthResult(it.code, it.header("Location"), body)
        }
    }

    /** POST bookings.php → Liste eigener Buchungen als HTML-Fragment. */
    suspend fun fetchMyBookingsHtml(): String = withSession { phpSessId ->
        val request = Request.Builder()
            .url(BibConfig.BOOKINGS_URL)
            .post("".toRequestBody())
            .header("Accept", "text/javascript, text/html, application/xml, text/xml, */*")
            .header("Cookie", "PHPSESSID=$phpSessId; gap_language=de")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("X-Prototype-Version", "1.7")
            .build()
        val resp = bareClient().newCall(request).execute()
        resp.use {
            AuthResult(it.code, it.header("Location"), it.body?.string().orEmpty())
        }
    }

    /**
     * `set_data.php?action=book_room&value=YYYYMMDD,HHMM,HHMM,ROOM,`
     * Trailing comma ist im Original-Frontend — wir reproduzieren das.
     */
    suspend fun bookRoom(
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        roomId: Int
    ): String = withSession { phpSessId ->
        val value = listOf(
            BibScraper.formatDate(date),
            BibScraper.toMilitary(start).toString(),
            BibScraper.toMilitary(end).toString(),
            roomId.toString(),
            ""
        ).joinToString(",")
        val url = "${BibConfig.SET_DATA_URL}?action=book_room&value=$value"
        Timber.i("Bib bookRoom → $url")
        ajaxGet(url, phpSessId)
    }

    /** `set_data.php?action=delete&value=YYYYMMDD,HHMM,ROOM` */
    suspend fun cancelBooking(
        date: LocalDate,
        start: LocalTime,
        roomId: Int
    ): String = withSession { phpSessId ->
        val value = "${BibScraper.formatDate(date)},${BibScraper.toMilitary(start)},$roomId"
        val url = "${BibConfig.SET_DATA_URL}?action=delete&value=$value"
        Timber.i("Bib cancelBooking → $url")
        ajaxGet(url, phpSessId)
    }

    /**
     * `get_data.php?action=get_end_times&value=YYYYMMDD,HHMM,ROOM` →
     * `<option>`-Snippet mit erlaubten Endzeiten.
     */
    suspend fun fetchEndTimes(
        date: LocalDate,
        start: LocalTime,
        roomId: Int
    ): String = withSession { phpSessId ->
        val value = "${BibScraper.formatDate(date)},${BibScraper.toMilitary(start)},$roomId"
        val url = "${BibConfig.GET_DATA_URL}?action=get_end_times&value=$value"
        ajaxGet(url, phpSessId)
    }

    private fun ajaxGet(url: String, phpSessId: String): AuthResult {
        val request = Request.Builder()
            .url(url.toHttpUrl())
            .header("Accept", "text/javascript, text/html, application/xml, text/xml, */*")
            .header("Cookie", "PHPSESSID=$phpSessId; gap_language=de")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("X-Prototype-Version", "1.7")
            .build()
        val resp = bareClient().newCall(request).execute()
        return resp.use {
            AuthResult(it.code, it.header("Location"), it.body?.string().orEmpty())
        }
    }

    /**
     * Session-Guard: holt PHPSESSID, führt block aus, prüft auf Re-Auth-Trigger.
     * Bei 302-to-CAS oder 401/403 wird Session invalidiert und EIN Retry gefahren.
     */
    private suspend fun withSession(block: (String) -> AuthResult): String = withContext(io) {
        var phpSessId = session.phpSessId()
        var result = block(phpSessId)
        if (result.indicatesAuthLoss()) {
            Timber.w("Bib API meldet Auth-Verlust (code=${result.code}, loc=${result.location}) — Re-Login")
            session.invalidate()
            phpSessId = session.phpSessId(forceRefresh = true)
            result = block(phpSessId)
            if (result.indicatesAuthLoss()) {
                throw IllegalStateException("Bib-Login schlägt fehl auch nach Re-Auth")
            }
        }
        if (result.code !in 200..299 && result.code !in 300..399) {
            throw IllegalStateException("Bib API HTTP ${result.code}: ${result.body.take(200)}")
        }
        result.body
    }

    private data class AuthResult(val code: Int, val location: String?, val body: String) {
        fun indicatesAuthLoss(): Boolean = when {
            code in 300..399 && location != null && location.contains("/sso/login") -> true
            code == 401 || code == 403 -> true
            else -> false
        }
    }
}
