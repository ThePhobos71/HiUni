package de.transio.hiuni.feature.movies.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scraper für unifilm.de Studentenkinos. Pattern aus v1 (HIUNI_KONZEPTE.md §6.1):
 * - `li.film[data-id][data-sid]` für Listen-Einträge
 * - `div.film-showcase[data-id][data-sid]` für Detail-Block — Cross-Reference via data-id+sid
 * - Filmdaten (Regie/FSK/Land/Dauer/Genre) liegen unstrukturiert in `ul.film-info-filmdaten li`
 *   und werden per String-Heuristik klassifiziert (fragil — Phase 4 könnte robuster werden).
 */
@Singleton
class MovieScraper @Inject constructor(
    private val client: OkHttpClient
) {

    suspend fun fetch(city: String = "Hildesheim"): List<MovieEntity> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/$city"
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
            )
            .get()
            .build()
        val html = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("unifilm.de HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
        parse(Jsoup.parse(html, url))
    }

    internal fun parse(doc: Document): List<MovieEntity> {
        val movies = mutableListOf<MovieEntity>()
        for (filmElem in doc.select("li.film")) {
            val filmId = filmElem.attr("data-id").trim()
            val sessionId = filmElem.attr("data-sid").trim()
            if (filmId.isBlank() || sessionId.isBlank()) continue

            val showcase = doc.selectFirst("div.film-showcase[data-id=$filmId][data-sid=$sessionId]") ?: continue
            val movie = parseSingleFilm(filmElem, showcase, filmId, sessionId) ?: continue
            movies += movie
        }
        return movies
    }

    private fun parseSingleFilm(
        filmElem: Element,
        showcase: Element,
        filmId: String,
        sessionId: String
    ): MovieEntity? {
        val title = showcase.selectFirst("h1.headline-h3 > span")?.text()?.trim()
            ?: filmElem.selectFirst(".filmtitel")?.text()?.trim()
            ?: return null
        val subtitle = showcase.selectFirst("h1.headline-h3 span.headline-normalcase")?.text()?.trim()
        val poster = filmElem.selectFirst("img")?.absUrl("src")?.takeIf { it.isNotBlank() }
        val trailer = showcase.selectFirst("video.film-trailer source")?.absUrl("src")?.takeIf { it.isNotBlank() }
        val description = showcase.select("div.film-info-text > p")
            .joinToString(separator = "\n\n") { it.text().trim() }
            .takeIf { it.isNotBlank() }

        val dateText = showcase.selectFirst("ul.film-info-termin li.datum")?.text()?.trim()
            ?: filmElem.selectFirst(".filmtermin")?.text()?.trim()
        val timeText = showcase.selectFirst("ul.film-info-termin li.uhrzeit")?.text()?.trim()
            ?: filmElem.selectFirst(".filmuhrzeit")?.text()?.trim()
        val room = showcase.selectFirst("ul.film-info-termin li.raum")?.text()?.trim()
            ?: filmElem.selectFirst(".filmraum")?.text()?.trim()

        val date = parseDate(dateText)
        val time = parseTime(timeText)

        var director: String? = null
        var country: String? = null
        var genre: String? = null
        var fsk: String? = null
        var duration: Int? = null
        for (li in showcase.select("ul.film-info-filmdaten li")) {
            val text = li.text().trim()
            when {
                text.startsWith("R:", ignoreCase = true) -> director = text.substringAfter(":").trim()
                text.startsWith("FSK", ignoreCase = true) -> fsk = text
                text.endsWith("Min.") -> duration = text.removeSuffix("Min.").trim().toIntOrNull()
                text.matches(Regex("[A-Z, ]+")) && text.length < 20 -> country = text
                else -> if (genre == null) genre = text
            }
        }

        return MovieEntity(
            filmId = filmId,
            sessionId = sessionId,
            title = title,
            subtitle = subtitle,
            description = description,
            date = date,
            time = time,
            location = room,
            posterUrl = poster,
            trailerUrl = trailer,
            director = director,
            country = country,
            genre = genre,
            durationMinutes = duration,
            fsk = fsk,
            isPast = filmElem.hasClass("film-past")
        )
    }

    private fun parseDate(text: String?): LocalDate? {
        if (text.isNullOrBlank()) return null
        // Patterns: "Di, 19.05.2026" / "19.05.2026" / "19.05."
        val match = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""").find(text)
        if (match != null) {
            return runCatching {
                LocalDate.of(match.groupValues[3].toInt(), match.groupValues[2].toInt(), match.groupValues[1].toInt())
            }.getOrNull()
        }
        val partial = Regex("""(\d{1,2})\.(\d{1,2})\.""").find(text)
        if (partial != null) {
            val year = LocalDate.now().year
            return runCatching {
                LocalDate.of(year, partial.groupValues[2].toInt(), partial.groupValues[1].toInt())
            }.getOrNull()
        }
        return null
    }

    private fun parseTime(text: String?): LocalTime? {
        if (text.isNullOrBlank()) return null
        val match = Regex("""(\d{1,2}):(\d{2})""").find(text) ?: return null
        return runCatching {
            LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }.getOrNull()
    }

    companion object {
        private const val BASE_URL = "https://www.unifilm.de/studentenkinos"
        @Suppress("unused")
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)
    }
}
