package de.transio.hiuni.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liefert einen 32-Byte-Schlüssel für SQLCipher. Wird beim ersten Start zufällig erzeugt
 * und in EncryptedSharedPreferences (Android Keystore-gestützt) verschlüsselt abgelegt.
 *
 * Falls EncryptedSharedPreferences nicht verfügbar ist (extrem alte Geräte / korrupte
 * Keystore-Records), fällt der Provider auf einen Standard-SharedPreferences-Speicher
 * zurück — schlechter als nichts, da die DB sonst gar nicht zu öffnen wäre. In dem Fall
 * wird ein Timber-Warning geloggt.
 */
@Singleton
class DatabaseKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getOrCreateKey(): ByteArray {
        val prefs = openPrefs()
        prefs.getString(KEY, null)?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
        return key
    }

    private fun openPrefs(): SharedPreferences = try {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREF_FILE,
            masterKey,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) {
        Timber.w(t, "EncryptedSharedPreferences not available, falling back to plain SharedPreferences for DB key")
        context.applicationContext.getSharedPreferences(PREF_FILE_FALLBACK, Context.MODE_PRIVATE)
    }

    private companion object {
        const val KEY_BYTES = 32
        const val KEY = "db_key"
        const val PREF_FILE = "de.transio.hiuni.db_key"
        const val PREF_FILE_FALLBACK = "de.transio.hiuni.db_key_plain"
    }
}
