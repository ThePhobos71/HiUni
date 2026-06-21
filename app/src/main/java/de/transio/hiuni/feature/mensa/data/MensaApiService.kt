package de.transio.hiuni.feature.mensa.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MensaApiService @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {

    data class FetchResult(val meals: List<MealEntity>, val announcements: List<Announcement>)

    suspend fun fetchMenu(locationId: Int, from: LocalDate, to: LocalDate): FetchResult =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/locations/$locationId/menu/${from.format(API_DATE)}/${to.format(API_DATE)}"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("STW-ON HTTP ${resp.code} for $url")
                }
                val body = resp.body?.string().orEmpty()
                parse(body, locationId)
            }
        }

    internal fun parse(body: String, locationId: Int): FetchResult {
        if (body.isBlank()) return FetchResult(emptyList(), emptyList())
        val response = runCatching { json.decodeFromString<MensaMenuResponse>(body) }
            .getOrElse {
                Timber.w(it, "Could not parse STW-ON menu response")
                return FetchResult(emptyList(), emptyList())
            }
        val meals = response.meals.mapIndexedNotNull { idx, meal ->
            meal.toEntity(
                locationId = locationId,
                fallbackKey = "${meal.date ?: "unknown"}-$idx-${meal.name?.hashCode() ?: 0}"
            )
        }
        val announcements = response.meals.mapNotNull { it.toAnnouncement() }.distinct()
        return FetchResult(meals = meals, announcements = announcements)
    }

    companion object {
        private const val BASE_URL = "https://sls.api.stw-on.de/v1"
        private val API_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
