package de.transio.hiuni.feature.movies.data

import de.transio.hiuni.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reichert unifilm-Scrape-Ergebnisse mit besseren Postern und Beschreibungen aus TMDB an.
 * Aktivierung: TMDB API-Key in `local.properties` setzen:
 *   tmdb.api.key=DEIN_KEY
 * Ohne Key fällt der Repository auf unifilm-Daten zurück.
 */
@Singleton
class TmdbApiService @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {

    val isConfigured: Boolean get() = BuildConfig.TMDB_API_KEY.isNotBlank()

    suspend fun searchMovie(title: String, year: Int? = null): TmdbMovieResult? {
        if (!isConfigured) return null
        if (title.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val urlBuilder = "$BASE_URL/search/movie".toHttpUrlOrNull()!!.newBuilder()
                    .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
                    .addQueryParameter("language", "de-DE")
                    .addQueryParameter("include_adult", "false")
                    .addQueryParameter("query", title.cleanForSearch())
                year?.let { urlBuilder.addQueryParameter("year", it.toString()) }

                val request = Request.Builder().url(urlBuilder.build()).get().build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string().orEmpty()
                    val parsed = json.decodeFromString<TmdbSearchResponse>(body)
                    parsed.results.firstOrNull { it.posterPath?.isNotBlank() == true } ?: parsed.results.firstOrNull()
                }
            }.onFailure { Timber.w(it, "TMDB search failed for '$title'") }.getOrNull()
        }
    }

    fun posterUrl(posterPath: String?, size: String = "w500"): String? =
        posterPath?.takeIf { it.isNotBlank() }?.let { "$IMAGE_BASE/$size$it" }

    private fun String.cleanForSearch(): String =
        substringBefore(" – ").substringBefore(" - ").trim()

    companion object {
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private const val IMAGE_BASE = "https://image.tmdb.org/t/p"
    }
}

@Serializable
data class TmdbSearchResponse(
    @SerialName("results") val results: List<TmdbMovieResult> = emptyList()
)

@Serializable
data class TmdbMovieResult(
    @SerialName("id") val id: Long? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null
)
