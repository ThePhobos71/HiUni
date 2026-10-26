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
 * Registriert bzw. deregistriert den FCM-Token beim Tickle-Server (`server/`).
 *
 * HTTP-API:
 *   POST {baseUrl}/register    {"token": "..."}   Header: X-Api-Key
 *   POST {baseUrl}/unregister  {"token": "..."}   Header: X-Api-Key
 *
 * URL und Key kommen aus [PushServerConfig] (BuildConfig-Standard oder
 * Nutzer-Override).
 *
 * Idempotenz: [ensureRegistered] persistiert Token UND Server-URL der letzten
 * erfolgreichen Registrierung. Nur wenn sich eins von beiden geändert hat, geht
 * ein Request raus. So können wir bei jedem App-Start / onNewToken gefahrlos
 * „sicherstellen, dass registriert ist" aufrufen, ohne den Server zu spammen,
 * und ein Wechsel des Standard-Servers (neue App-Version) registriert
 * automatisch neu.
 *
 * Bewusst NICHT der geteilte [de.transio.hiuni.core.network.OkHttpClientProvider]-
 * Client: der trägt einen Scraping-User-Agent und einen PolitenessInterceptor
 * (Random-Delay), beides für eine schlanke JSON-API unpassend. Der Client hier
 * ist minimal.
 */
@Singleton
class PushRegistrationManager @Inject constructor(
    private val settings: SettingsDataStore,
    private val serverConfig: PushServerConfig,
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
     * Stellt sicher, dass [token] beim aktuell konfigurierten Server registriert
     * ist. Idempotent: wenn Token und Server bereits als registriert vermerkt
     * sind, passiert nichts. War der Token vorher bei einem anderen Server
     * registriert, wird er dort best-effort abgemeldet.
     *
     * @return [AppResult.Success] auch im No-Op-Fall; Failure nur bei echtem
     *         Netz-/HTTP-Fehler (der Aufrufer kann dann per WorkManager retryen).
     */
    suspend fun ensureRegistered(token: String): AppResult<Unit> = withContext(io) {
        runCatchingApp {
            val enabled = settings.pushSyncEnabled.first()
            if (!enabled) {
                Timber.i("PushRegistration: Feature aus, kein Register")
                return@runCatchingApp
            }
            if (token.isBlank()) {
                Timber.w("PushRegistration: leerer Token, skip")
                return@runCatchingApp
            }
            val config = serverConfig.current()
            require(config.serverUrl.isNotBlank()) { "Push-Server-URL nicht gesetzt" }
            require(config.apiKey.isNotBlank()) { "Push-API-Key nicht gesetzt" }

            val registeredToken = settings.pushRegisteredToken.first()
            val registeredServer = settings.pushRegisteredServer.first()
            if (registeredToken == token && registeredServer == config.serverUrl) {
                Timber.d("PushRegistration: Token bereits registriert, No-Op")
                return@runCatchingApp
            }
            postToken(config.serverUrl, config.apiKey, endpoint = "register", token = token)
            settings.setPushRegistration(token = token, server = config.serverUrl)
            Timber.i("PushRegistration: Token registriert")

            // Alter Eintrag bei einem anderen Server (z.B. Standard-URL hat sich
            // geändert): best-effort abmelden. Scheitert das, räumt der alte
            // Server den Token spätestens über sein Max-Alter selbst weg.
            if (registeredToken.isNotBlank() && registeredServer.isNotBlank() &&
                registeredServer != config.serverUrl
            ) {
                runCatching {
                    postToken(registeredServer, config.apiKey, endpoint = "unregister", token = registeredToken)
                }.onFailure { Timber.i(it, "PushRegistration: Abmelden beim alten Server fehlgeschlagen, ignoriert") }
            }
        }
    }

    /**
     * Meldet den zuletzt registrierten (oder den übergebenen) Token beim Server
     * ab, bei dem er registriert wurde, und löscht die lokale Markierung. Nach
     * dem Aufruf gilt „kein Token beim Server hinterlegt".
     */
    suspend fun unregister(token: String? = null): AppResult<Unit> = withContext(io) {
        runCatchingApp {
            val effective = token ?: settings.pushRegisteredToken.first()
            if (effective.isBlank()) {
                Timber.i("PushRegistration: kein registrierter Token, nichts abzumelden")
                settings.setPushRegistration(token = "", server = "")
                return@runCatchingApp
            }
            val config = serverConfig.current()
            val server = settings.pushRegisteredServer.first().ifBlank { config.serverUrl }
            require(server.isNotBlank()) { "Push-Server-URL nicht gesetzt" }
            require(config.apiKey.isNotBlank()) { "Push-API-Key nicht gesetzt" }
            postToken(server, config.apiKey, endpoint = "unregister", token = effective)
            settings.setPushRegistration(token = "", server = "")
            Timber.i("PushRegistration: Token abgemeldet")
        }
    }

    private fun postToken(baseUrl: String, apiKey: String, endpoint: String, token: String) {
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
