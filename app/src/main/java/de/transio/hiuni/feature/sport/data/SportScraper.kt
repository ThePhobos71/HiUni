package de.transio.hiuni.feature.sport.data

import de.transio.hiuni.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber
import java.io.IOException
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

class ScrapeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Scraper für den öffentlichen Hochschulsport-Plan auf supersaas.de.
 *
 * Die Seite rendert die Slots als server-side JS-Variable ins HTML:
 * `var app=[[start, end, slotId, capacity, ?, serviceType, currentBookings,
 *           "TITLE", "DESCRIPTION", waitlist, "", paidFlag], …];`
 *
 * Felder (empirisch verifiziert):
 *  - [0] Long  startUnixSec
 *  - [1] Long  endUnixSec
 *  - [2] Long  supersaasSlotId
 *  - [3] Int   capacity (negativ z. B. -3 = abgesagt)
 *  - [4] Int   ungenutzt (-1)
 *  - [5] Int   serviceType (immer 3 in den Samples)
 *  - [6] Int   currentBookings
 *  - [7] Str   Titel ("YOGA", "FITNESS", "FÄLLT AUS! - alle level", …)
 *  - [8] Str   Beschreibung, multi-line, oft mit "Ort: …" als erste Zeile
 *  - [9] Int   waitlistCount
 *  - [10] Str  leer
 *  - [11] Int  paidFlag (0/1)
 *
 * Pagination ist nicht möglich — Server liefert immer dieselben ~5 Wochen.
 */
@Singleton
class SportScraper @Inject constructor(
    private val client: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    suspend fun fetch(): List<SportEventEntity> = withContext(io) {
        val request = Request.Builder()
            .url(URL)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
            )
            .header("Accept-Language", "de-DE,de;q=0.9")
            .get()
            .build()
        val html = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw ScrapeException("supersaas HTTP ${resp.code}")
            }
            resp.body?.string().orEmpty()
        }
        parse(html)
    }

    /**
     * Public für Tests + Wiederverwendung im Worker, falls man rohes HTML
     * cachen will. Wirft `ScrapeException`, wenn der `var app=…`-Block fehlt
     * oder das JSON kaputt ist.
     */
    fun parse(html: String): List<SportEventEntity> {
        val match = APP_ARRAY_REGEX.find(html)
            ?: throw ScrapeException("Kein 'var app=[…]'-Block im HTML gefunden")
        val arrayJson = match.groupValues[1]
        val events = try {
            JSONArray(arrayJson)
        } catch (t: JSONException) {
            throw ScrapeException("app[]-JSON nicht parsebar: ${t.message}", t)
        }
        val now = Instant.now()
        val out = ArrayList<SportEventEntity>(events.length())
        for (i in 0 until events.length()) {
            val row = events.optJSONArray(i) ?: continue
            val entity = mapRow(row, fetchedAt = now) ?: continue
            out += entity
        }
        Timber.i("SportScraper: ${out.size}/${events.length()} Events geparst")
        return out
    }

    private fun mapRow(row: JSONArray, fetchedAt: Instant): SportEventEntity? {
        return try {
            val startSec = row.optLong(0, -1L)
            val endSec = row.optLong(1, -1L)
            val slotId = row.optLong(2, -1L)
            if (startSec <= 0 || endSec <= 0 || slotId <= 0) return null

            val capacity = row.optInt(3, 0)
            val currentBookings = row.optInt(6, 0).coerceAtLeast(0)
            val titleRaw = row.optString(7, "").trim()
            val description = row.optString(8, "").trim().takeIf { it.isNotEmpty() }
            val waitlistCount = row.optInt(9, 0).coerceAtLeast(0)
            val paidFlag = row.optInt(11, 0)

            val titleUpper = titleRaw.uppercase(Locale.GERMAN)
            val isCancelled = capacity < 0 || titleUpper.contains("FÄLLT AUS")
            val title = normalizeTitle(titleRaw, isCancelled)
            val location = description?.let(::extractLocation)

            SportEventEntity(
                supersaasSlotId = slotId,
                startTime = Instant.ofEpochSecond(startSec),
                endTime = Instant.ofEpochSecond(endSec),
                title = title,
                description = description,
                location = location,
                capacity = capacity,
                currentBookings = currentBookings,
                waitlistCount = waitlistCount,
                isCancelled = isCancelled,
                isPaidOnly = paidFlag == 1,
                fetchedAt = fetchedAt
            )
        } catch (t: Throwable) {
            Timber.w(t, "SportScraper: row ignoriert, raw=${row.toString().take(120)}")
            null
        }
    }

    companion object {
        const val URL = "https://www.supersaas.de/schedule/HSP_Uni_Hildesheim/Hochschulsport"

        // `.dotAll()` braucht's, weil die JSON-Strings in den Cells echte
        // \r\n-Sequenzen enthalten. Das Trailing-Match-Token (`,;\n`) verhindert
        // dass wir aus Versehen das nächste Array mit reinpacken.
        private val APP_ARRAY_REGEX = Regex(
            """var\s+app\s*=\s*(\[\[.*?]])\s*[,;\n]""",
            RegexOption.DOT_MATCHES_ALL
        )

        // Erste "Ort: …"-Zeile in der Beschreibung — meistens Zeile 1, manchmal
        // mittendrin. Wir nehmen den ersten Treffer bis zum nächsten Zeilenende.
        private val LOCATION_REGEX = Regex(
            """(?im)^\s*Ort\s*:\s*(.+?)\s*$"""
        )

        @JvmStatic
        fun extractLocation(description: String): String? =
            LOCATION_REGEX.find(description)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

        /**
         * supersaas brüllt alles in CAPS ("YOGA", "FITNESS"). Wir machen daraus
         * Title-Case, sofern es nicht ein "FÄLLT AUS"-Hinweis ist — den lassen
         * wir bewusst auffällig stehen.
         */
        @JvmStatic
        fun normalizeTitle(raw: String, isCancelled: Boolean): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            if (isCancelled) return trimmed
            // Nur reines CAPS normalisieren — gemischte Schreibweisen (selten,
            // z. B. "BodyArt") behalten wir 1:1.
            val isAllUpper = trimmed.uppercase(Locale.GERMAN) == trimmed &&
                trimmed.any { it.isLetter() }
            if (!isAllUpper) return trimmed
            return trimmed.lowercase(Locale.GERMAN).replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.GERMAN) else ch.toString()
            }
        }
    }
}

@Suppress("unused")
private inline fun <T> ignoreIOException(block: () -> T): T? = try { block() } catch (_: IOException) { null }
