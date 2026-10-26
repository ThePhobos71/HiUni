package de.transio.hiuni.core.push

import de.transio.hiuni.BuildConfig
import de.transio.hiuni.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Löst die effektive Push-Server-Konfiguration auf: Standard-URL und API-Key
 * kommen aus `BuildConfig` (`local.properties` → `tickle.server.url` /
 * `tickle.api.key`), in den Sync-Settings kann der Nutzer beides unter
 * „Erweitert" überschreiben (z.B. für einen eigenen Server). Ein leerer
 * Override heißt: Standard verwenden.
 */
@Singleton
class PushServerConfig @Inject constructor(
    private val settings: SettingsDataStore
) {

    data class Resolved(
        val serverUrl: String,
        val apiKey: String
    ) {
        /** Ohne URL oder Key hat die Registrierung kein Ziel (z.B. CI-Build ohne Key). */
        val isComplete: Boolean get() = serverUrl.isNotBlank() && apiKey.isNotBlank()
    }

    val resolved: Flow<Resolved> = combine(
        settings.pushServerUrlOverride,
        settings.pushApiKeyOverride
    ) { url, key -> resolve(url, key) }

    suspend fun current(): Resolved = resolved.first()

    companion object {
        val defaultServerUrl: String get() = BuildConfig.TICKLE_SERVER_URL

        fun resolve(
            overrideUrl: String,
            overrideKey: String,
            defaultUrl: String = BuildConfig.TICKLE_SERVER_URL,
            defaultKey: String = BuildConfig.TICKLE_API_KEY
        ): Resolved = Resolved(
            serverUrl = overrideUrl.trim().ifBlank { defaultUrl.trim() }.trimEnd('/'),
            apiKey = overrideKey.trim().ifBlank { defaultKey.trim() }
        )
    }
}
