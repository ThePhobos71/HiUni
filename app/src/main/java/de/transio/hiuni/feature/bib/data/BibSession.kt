package de.transio.hiuni.feature.bib.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hält die PHPSESSID-Session der Bib-Buchung. Erst-Login läuft über
 * CAS-SSO — wir holen ein Service-Ticket für [BibConfig.LOGIN_SERVICE]
 * und feuern es gegen `index.php?login&ticket=…`. ubwww antwortet mit
 * `Set-Cookie: PHPSESSID=…` und 302-Redirect — diese Session-ID nutzen
 * wir für alle AJAX-Endpunkte.
 *
 * PHPSESSID + Zeitstempel werden verschlüsselt persistiert (analog zu
 * [de.transio.hiuni.core.auth.CasCookieStore]) — so überlebt die Bib-
 * Session App-Neustarts, solange ubwww sie nicht serverseitig abräumt
 * (~25 min TTL). Bei Auth-Verlust (HTTP 401/302-to-CAS) invalidieren
 * wir explizit und holen via CAS ein frisches Ticket.
 */
@Singleton
class BibSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val casSession: CasSession,
    private val httpClient: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    private val mutex = Mutex()
    @Volatile private var cachedPhpSessId: String? = null
    @Volatile private var cachedAt: Instant? = null
    private val sessionTtl: Duration = Duration.ofMinutes(25)

    /** Liefert eine gültige PHPSESSID, ggf. nach einem frischen CAS-Login. */
    suspend fun phpSessId(forceRefresh: Boolean = false): String = mutex.withLock {
        val now = Instant.now()
        if (!forceRefresh) {
            // In-Memory-Cache zuerst — fallback Disk. Beide werden auf
            // Plausibilität geprüft (siehe [looksAuthenticated]): Wir verwerfen
            // Sessions die noch von einem alten App-Build stammen, bevor wir
            // wussten dass PHPs erste PHPSESSID die anonyme ist.
            val cachedMem = cachedPhpSessId?.takeIf { looksAuthenticated(it) }
            val cached = cachedMem ?: loadFromDisk()?.let { (id, at) ->
                if (!looksAuthenticated(id)) {
                    Timber.w("Bib-Session: persisted PHPSESSID looks anonymous (len=${id.length}) — discarding")
                    openPrefs()?.edit()?.clear()?.apply()
                    null
                } else {
                    cachedPhpSessId = id
                    cachedAt = at
                    Timber.i("Bib-Session restored from disk (len=${id.length}, age=${Duration.between(at, now).toMinutes()}min)")
                    id
                }
            }
            val at = cachedAt
            if (cached != null && at != null && Duration.between(at, now) < sessionTtl) {
                return@withLock cached
            }
        }
        val fresh = login()
        cachedPhpSessId = fresh
        cachedAt = now
        persistToDisk(fresh, now)
        fresh
    }

    /**
     * Heuristik gegen Session-Fixation-Reste: PHP-Default-Session-IDs sind 26
     * Zeichen alphanumerisch (anonym, ohne User-Bindung). Nach `session_regenerate_id`
     * mit `session.sid_length=48`+ rendert ubwww typischerweise 64-Zeichen-Hex —
     * das ist die User-gebundene Session, die wir wollen. Alles ≤ 32 Zeichen
     * werfen wir weg und holen einen frischen CAS-ST.
     */
    private fun looksAuthenticated(phpSessId: String): Boolean =
        phpSessId.length >= 32

    fun invalidate() {
        cachedPhpSessId = null
        cachedAt = null
        openPrefs()?.edit()?.clear()?.apply()
    }

    private fun persistToDisk(phpSessId: String, at: Instant) {
        openPrefs()?.edit()
            ?.putString(KEY_PHPSESSID, phpSessId)
            ?.putLong(KEY_OBTAINED_AT, at.toEpochMilli())
            ?.apply()
    }

    private fun loadFromDisk(): Pair<String, Instant>? {
        val prefs = openPrefs() ?: return null
        val id = prefs.getString(KEY_PHPSESSID, null)?.takeIf { it.isNotBlank() } ?: return null
        val epoch = prefs.getLong(KEY_OBTAINED_AT, 0L).takeIf { it > 0L } ?: return null
        return id to Instant.ofEpochMilli(epoch)
    }

    private fun openPrefs(): SharedPreferences? = try {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREF_FILE,
            masterKey,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) {
        Timber.w(t, "BibSession: failed to open encrypted prefs")
        null
    }

    private companion object {
        const val PREF_FILE = "de.transio.hiuni.bib_session"
        const val KEY_PHPSESSID = "phpsessid"
        const val KEY_OBTAINED_AT = "obtained_at"
    }

    /**
     * Holt ein Service-Ticket bei CAS und feuert `index.php?login&ticket=…`.
     * Setzt dabei PHPSESSID per Set-Cookie.
     */
    private suspend fun login(): String = withContext(io) {
        val ticket = casSession.getServiceTicket(BibConfig.LOGIN_SERVICE)
        val ticketedUrl = "${BibConfig.LOGIN_SERVICE}&ticket=$ticket"
        Timber.i("Bib-Login: redirecting to $ticketedUrl with fresh ST")

        // Ohne automatische Redirect-Verfolgung — wir wollen den Set-Cookie
        // aus der 302-Antwort von ubwww greifen.
        val client = httpClient.newBuilder()
            .followRedirects(false)
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .build()
        val request = Request.Builder()
            .url(ticketedUrl)
            .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            .build()
        client.newCall(request).execute().use { response ->
            val setCookies = response.headers("Set-Cookie")
            Timber.d("Bib-Login response: code=${response.code} setCookies=$setCookies")
            // PHP regeneriert die Session-ID nach erfolgreichem CAS-Login —
            // die erste PHPSESSID ist die anonyme Pre-Auth-Session, die zweite
            // ist die User-Session mit gebundener Identität. Wir brauchen die
            // letzte, sonst landen Buchungen anonym beim Server.
            val phpSessId = setCookies
                .mapNotNull { extractCookieValue(it, "PHPSESSID") }
                .lastOrNull()
            if (phpSessId.isNullOrBlank()) {
                throw IllegalStateException(
                    "Bib-Login: keine PHPSESSID in Response (HTTP ${response.code})"
                )
            }
            Timber.i("Bib-Login OK — PHPSESSID=${phpSessId.take(8)}… (len=${phpSessId.length})")
            phpSessId
        }
    }

    private fun extractCookieValue(setCookieLine: String, name: String): String? {
        val prefix = "$name="
        val idx = setCookieLine.indexOf(prefix)
        if (idx < 0) return null
        val tail = setCookieLine.substring(idx + prefix.length)
        return tail.substringBefore(';').takeIf { it.isNotBlank() }
    }
}
