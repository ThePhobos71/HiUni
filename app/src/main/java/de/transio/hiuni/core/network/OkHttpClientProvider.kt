package de.transio.hiuni.core.network

import android.content.Context
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

object OkHttpClientProvider {

    private const val CACHE_SIZE_BYTES = 5L * 1024 * 1024
    private const val CACHE_DIR_NAME = "http_cache"
    // Normaler mobiler Chrome-UA — manche Uni-Seiten blocken auffällige Strings ("HiUni" etc.).
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.6367.0 Mobile Safari/537.36"

    fun create(context: Context, debug: Boolean): OkHttpClient {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        val cookieJar = InMemoryCookieJar()

        val builder = OkHttpClient.Builder()
            .cache(Cache(cacheDir, CACHE_SIZE_BYTES))
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor { chain ->
                val original = chain.request()
                // Nur Default-UA setzen wenn der Aufrufer keinen eigenen gesetzt hat
                // (z.B. CasSession ersetzt den UA für TGC-Validierung).
                val request = if (original.header("User-Agent") == null) {
                    original.newBuilder().header("User-Agent", USER_AGENT).build()
                } else original
                chain.proceed(request)
            }
            // Random-Delay vor jedem LSF-/CAS-Hit (Uni-Hildesheim-Infrastruktur).
            // Spreizt Sync-Phasen und vermeidet Burst-Traffic.
            .addInterceptor(PolitenessInterceptor())

        if (debug) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
        }

        return builder.build()
    }

    private class InMemoryCookieJar : CookieJar {
        private val store = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            store.getOrPut(url.host) { mutableListOf() }.apply {
                removeAll { existing -> cookies.any { it.name == existing.name } }
                addAll(cookies)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            store[url.host].orEmpty().toList()
    }
}
