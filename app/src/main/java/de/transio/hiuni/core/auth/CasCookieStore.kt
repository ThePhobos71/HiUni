package de.transio.hiuni.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verschlüsselter Store für das CAS-Ticket-Granting-Cookie. Wird einmalig beim
 * WebView-Login extrahiert und bei jedem ServiceTicket-Request wieder gelesen.
 *
 * Storage-Pattern identisch zu CredentialsManager — EncryptedSharedPreferences
 * auf Android-Keystore-basierten MasterKey.
 */
@Singleton
class CasCookieStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun save(tgc: String, username: String?, baseUrl: String) {
        val prefs = openPrefs() ?: return
        prefs.edit()
            .putString(KEY_TGC, tgc)
            .putString(KEY_USERNAME, username)
            .putString(KEY_BASE_URL, baseUrl)
            .putLong(KEY_OBTAINED_AT, System.currentTimeMillis())
            .apply()
        Timber.i("CasCookieStore: TGC persisted (user=$username, base=$baseUrl)")
    }

    fun tgc(): String? = openPrefs()?.getString(KEY_TGC, null)

    fun username(): String? = openPrefs()?.getString(KEY_USERNAME, null)

    fun baseUrl(): String? = openPrefs()?.getString(KEY_BASE_URL, null)

    fun obtainedAt(): Instant? = openPrefs()?.getLong(KEY_OBTAINED_AT, 0L)
        ?.takeIf { it > 0L }
        ?.let(Instant::ofEpochMilli)

    fun hasSession(): Boolean = !tgc().isNullOrBlank()

    fun clear() {
        openPrefs()?.edit()?.clear()?.apply()
        Timber.i("CasCookieStore: cleared")
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
        Timber.w(t, "Failed to open CasCookieStore prefs")
        null
    }

    private companion object {
        const val PREF_FILE = "de.transio.hiuni.cas_session"
        const val KEY_TGC = "tgc"
        const val KEY_USERNAME = "username"
        const val KEY_BASE_URL = "base_url"
        const val KEY_OBTAINED_AT = "obtained_at"
    }
}
