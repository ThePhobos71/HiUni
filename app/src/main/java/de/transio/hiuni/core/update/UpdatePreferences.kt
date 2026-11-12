package de.transio.hiuni.core.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class UpdatePrefsSnapshot(
    /** „Automatisch nach Updates suchen", Default an. */
    val autoCheck: Boolean = true,
    /** Vom Nutzer übersprungene Version (versionCode). */
    val skippedVersionCode: Int? = null,
    /** Letzter erfolgreicher Check, 0 = nie. */
    val lastCheckEpochMs: Long = 0L,
    /** Zuletzt per Mitteilung gemeldete Version. */
    val lastNotifiedVersionCode: Int? = null
)

/** Letzte 200-Antwort samt ETag, damit Folge-Checks mit `If-None-Match` laufen. */
data class CachedRelease(val etag: String, val body: String)

/** Persistenz des Update-Pfads. Als Interface, damit Tests ohne DataStore auskommen. */
interface UpdatePreferences {
    val autoCheck: Flow<Boolean>
    suspend fun snapshot(): UpdatePrefsSnapshot
    suspend fun setAutoCheck(enabled: Boolean)
    suspend fun setSkippedVersionCode(code: Int?)
    suspend fun setLastCheckEpochMs(epochMs: Long)
    suspend fun setLastNotifiedVersionCode(code: Int)
    suspend fun cachedRelease(): CachedRelease?
    suspend fun saveCachedRelease(cache: CachedRelease)
}

@Singleton
class DataStoreUpdatePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : UpdatePreferences {

    override val autoCheck: Flow<Boolean> = dataStore.data
        .map { it[KEY_AUTO_CHECK] ?: true }
        .distinctUntilChanged()

    override suspend fun snapshot(): UpdatePrefsSnapshot {
        val prefs = dataStore.data.first()
        return UpdatePrefsSnapshot(
            autoCheck = prefs[KEY_AUTO_CHECK] ?: true,
            skippedVersionCode = prefs[KEY_SKIPPED],
            lastCheckEpochMs = prefs[KEY_LAST_CHECK] ?: 0L,
            lastNotifiedVersionCode = prefs[KEY_LAST_NOTIFIED]
        )
    }

    override suspend fun setAutoCheck(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_CHECK] = enabled }
    }

    override suspend fun setSkippedVersionCode(code: Int?) {
        dataStore.edit { if (code == null) it.remove(KEY_SKIPPED) else it[KEY_SKIPPED] = code }
    }

    override suspend fun setLastCheckEpochMs(epochMs: Long) {
        dataStore.edit { it[KEY_LAST_CHECK] = epochMs }
    }

    override suspend fun setLastNotifiedVersionCode(code: Int) {
        dataStore.edit { it[KEY_LAST_NOTIFIED] = code }
    }

    override suspend fun cachedRelease(): CachedRelease? {
        val prefs = dataStore.data.first()
        val etag = prefs[KEY_ETAG] ?: return null
        val body = prefs[KEY_BODY] ?: return null
        return CachedRelease(etag, body)
    }

    override suspend fun saveCachedRelease(cache: CachedRelease) {
        dataStore.edit {
            it[KEY_ETAG] = cache.etag
            it[KEY_BODY] = cache.body
        }
    }

    private companion object {
        val KEY_AUTO_CHECK = booleanPreferencesKey("app_update_auto_check")
        val KEY_SKIPPED = intPreferencesKey("app_update_skipped_version_code")
        val KEY_LAST_CHECK = longPreferencesKey("app_update_last_check_epoch_ms")
        val KEY_LAST_NOTIFIED = intPreferencesKey("app_update_last_notified_version_code")
        val KEY_ETAG = stringPreferencesKey("app_update_cached_etag")
        val KEY_BODY = stringPreferencesKey("app_update_cached_body")
    }
}
