package de.transio.hiuni.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fragt `releases/latest` des öffentlichen Repos ab, ohne Token. Merkt sich ETag
 * und Antwort: Folge-Checks laufen mit `If-None-Match`, eine 304-Antwort zählt bei
 * GitHub nicht gegen das Limit von 60 Anfragen pro Stunde.
 */
@Singleton
class GitHubReleaseSource @Inject constructor(
    okHttpClient: OkHttpClient,
    private val prefs: UpdatePreferences
) : ReleaseSource {

    // Ohne HTTP-Cache: das ETag-Handling machen wir selbst, und der geteilte
    // 5-MB-Cache soll nicht mit Release-JSON belegt werden.
    private val client = okHttpClient.newBuilder().cache(null).build()

    override suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val cached = prefs.cachedRelease()
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", USER_AGENT)
            .apply { cached?.let { header("If-None-Match", it.etag) } }
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw UpdateException("Keine Verbindung zu GitHub", e)
        }
        response.use { res ->
            when {
                res.code == 304 && cached != null -> ReleaseJsonParser.parse(cached.body)
                res.isSuccessful -> {
                    val body = try {
                        res.body?.string().orEmpty()
                    } catch (e: IOException) {
                        throw UpdateException("Keine Verbindung zu GitHub", e)
                    }
                    val release = ReleaseJsonParser.parse(body)
                    res.header("ETag")?.let { prefs.saveCachedRelease(CachedRelease(it, body)) }
                    release
                }
                // Noch kein veröffentlichtes Release.
                res.code == 404 -> null
                res.code == 403 || res.code == 429 ->
                    throw UpdateException("GitHub-Limit erreicht, bitte später erneut versuchen")
                else -> throw UpdateException("GitHub antwortet nicht (HTTP ${res.code})")
            }
        }
    }

    companion object {
        const val REPO = "ThePhobos71/HiUni"
        const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
        const val USER_AGENT = "HiUni-Android"
    }
}
