package de.transio.hiuni.core.push

import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.runCatchingApp
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registriert bzw. deregistriert den FCM-Token beim Python-Push-Server.
 *
 * HTTP-API (siehe Aufgabenstellung):
 *   POST {baseUrl}/register    {"token": "..."}   Header: X-Api-Key
 *   POST {baseUrl}/unregister  {"token": "..."}   Header: X-Api-Key
 *
 * Idempotenz: [ensureRegistered] persistiert den zuletzt erfolgreich
 * registrierten Token in [SettingsDataStore.mailPushRegisteredToken]. Ein
 * erneuter Aufruf mit demselben Token ist ein No-Op — so können wir bei jedem
 * App-Start / onNewToken gefahrlos „sicherstellen, dass registriert ist"
 * aufrufen, ohne den Server zu spammen.
 *
 * Bewusst NICHT der geteilte [de.transio.hiuni.core.network.OkHttpClientProvider]-
 * Client: der trägt einen Scraping-User-Agent und einen PolitenessInterceptor
 * (Random-Delay), beides für eine schlanke JSON-API unpassend. Der Client hier
 * ist minimal.
 */
@Singleton
class PushRegistrationManager @Inject constructor(
    private val settings: SettingsDataStore,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Stellt sicher, dass [token] beim Server registriert ist. Idempotent:
     * wenn der Token bereits als registriert vermerkt ist, passiert nichts.
     * Bei Erfolg wird der Token als registriert persistiert.
     *
     * @return [AppResult.Success] auch im No-Op-Fall; Failure nur bei echtem
     *         Netz-/HTTP-Fehler (der Aufrufer kann dann per WorkManager retryen).
     */
    suspend fun ensureRegistered(token: String): AppResult<Unit> = withContext(io) {
        runCatchingApp {
            val enabled = settings.mailPushEnabled.first()
            if (!enabled) {
                Timber.i("PushRegistration: Feature aus — kein Register")
                return@runCatchingApp
            }
            if (token.isBlank()) {
                Timber.w("PushRegistration: leerer Token — skip")
                return@runCatchingApp
            }
            val already = settings.mailPushRegisteredToken.first()
            if (already == token) {
                Timber.d("PushRegistration: Token bereits registriert — No-Op")
                return@runCatchingApp
            }
            postToken(endpoint = "register", token = token)
            settings.setMailPushRegisteredToken(token)
            Timber.i("PushRegistration: Token registriert")
        }
    }

    /**
     * Meldet den zuletzt registrierten (oder den übergebenen) Token beim Server
     * ab und löscht die lokale Registrierungs-Markierung. Nach dem Aufruf gilt
     * „kein Token beim Server hinterlegt".
     */
    suspend fun unregister(token: String? = null): AppResult<Unit> = withContext(io) {
        runCatchingApp {
            val effective = token ?: settings.mailPushRegisteredToken.first()
            if (effective.isBlank()) {
                Timber.i("PushRegistration: kein registrierter Token — nichts abzumelden")
                settings.setMailPushRegisteredToken("")
                return@runCatchingApp
            }
            postToken(endpoint = "unregister", token = effective)
            settings.setMailPushRegisteredToken("")
            Timber.i("PushRegistration: Token abgemeldet")
        }
    }

    private suspend fun postToken(endpoint: String, token: String) {
        val baseUrl = settings.mailPushServerUrl.first().trim()
        val apiKey = settings.mailPushApiKey.first().trim()
        require(baseUrl.isNotBlank()) { "Push-Server-URL nicht gesetzt" }
        require(apiKey.isNotBlank()) { "Push-API-Key nicht gesetzt" }

        val url = baseUrl.trimEnd('/') + "/" + endpoint
        val payload = JSONObject().put("token", token).toString()
        val body = payload.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .header("X-Api-Key", apiKey)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val msg = "Push-Server antwortete mit HTTP ${response.code} auf /$endpoint"
                // 5xx / 429 sind transient → als IOException werfen, damit der
                // PushRegistrationWorker sie über den WorkManager-Backoff retryt.
                // 4xx (außer 429) sind Config-/Auth-Fehler → nicht retrybar.
                if (response.code >= 500 || response.code == 429) {
                    throw java.io.IOException(msg)
                }
                error(msg)
            }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
