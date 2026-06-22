package de.transio.hiuni.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun saveCredentials(username: String, password: String): Boolean {
        val prefs = obtainOrRecoverPrefs() ?: return false
        return try {
            prefs.edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .commit()
        } catch (t: Throwable) {
            Timber.e(t, "saveCredentials failed; resetting and retrying")
            resetEncryptedStore()
            val retry = obtainOrRecoverPrefs() ?: return false
            try {
                retry.edit()
                    .putString(KEY_USERNAME, username)
                    .putString(KEY_PASSWORD, password)
                    .commit()
            } catch (inner: Throwable) {
                Timber.e(inner, "saveCredentials retry failed")
                false
            }
        }
    }

    fun getUsername(): String? = obtainOrRecoverPrefs()?.getString(KEY_USERNAME, null)
    fun getPassword(): String? = obtainOrRecoverPrefs()?.getString(KEY_PASSWORD, null)

    fun hasCredentials(): Boolean = !getUsername().isNullOrBlank() && !getPassword().isNullOrBlank()

    fun clear(): Boolean {
        return try {
            obtainOrRecoverPrefs()?.edit()?.clear()?.commit() ?: false
        } catch (t: Throwable) {
            Timber.w(t, "clear failed")
            false
        }
    }

    fun diagnose(): String = buildString {
        val prefs = createEncryptedPrefs()
        appendLine("CredentialsManager diagnostic:")
        appendLine("  encryptedPrefsAvailable=${prefs != null}")
        appendLine("  hasUsername=${prefs?.getString(KEY_USERNAME, null) != null}")
        appendLine("  hasPassword=${prefs?.getString(KEY_PASSWORD, null) != null}")
    }

    private fun obtainOrRecoverPrefs(): SharedPreferences? {
        val first = createEncryptedPrefs()
        if (first != null) return first
        Timber.w("EncryptedSharedPreferences unavailable, attempting self-healing reset")
        resetEncryptedStore()
        return createEncryptedPrefs()
    }

    private fun createEncryptedPrefs(): SharedPreferences? = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREF_FILE,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) {
        Timber.w(t, "Failed to create EncryptedSharedPreferences")
        null
    }

    private fun resetEncryptedStore() {
        try {
            context.applicationContext
                .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .edit().clear().commit()
            context.applicationContext
                .getSharedPreferences(MASTER_KEY_FILE, Context.MODE_PRIVATE)
                .edit().clear().commit()
        } catch (t: Throwable) {
            Timber.e(t, "Encrypted store reset failed")
        }
    }

    companion object {
        private const val PREF_FILE = "de.transio.hiuni.secure_credentials"
        private const val MASTER_KEY_FILE = "_androidx_security_master_key_"
        private const val KEY_USERNAME = "imap_username"
        private const val KEY_PASSWORD = "imap_password"
    }
}
