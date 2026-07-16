package de.transio.hiuni.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liefert einen 32-Byte-Schlüssel für SQLCipher. Wird beim ersten Start zufällig erzeugt
 * und in EncryptedSharedPreferences (Android Keystore-gestützt) verschlüsselt abgelegt.
 *
 * SICHERHEIT — kein Klartext-Fallback mehr für NEUE Keys:
 * Früher wurde bei einem Fehler von EncryptedSharedPreferences der DB-Schlüssel
 * unverschlüsselt in normale SharedPreferences ([PREF_FILE_FALLBACK]) geschrieben.
 * Das legte den kompletten SQLCipher-Schlüssel im Klartext offen. Das ist jetzt
 * entfernt: Schlägt EncryptedSharedPreferences fehl, wird laut geloggt (Timber.e)
 * und der Fehler propagiert — es wird KEIN neuer Klartext-Key mehr angelegt.
 *
 * MIGRATION — Bestandsdaten bleiben lesbar:
 * Geräte, die zu einem früheren App-Build bereits einen Klartext-Key im Fallback
 * abgelegt haben, würden sonst ihre DB verlieren. Deshalb wird der Fallback-Store
 * beim Lesen weiterhin konsultiert (nur read-only), falls dort schon ein Key liegt.
 * Neue Keys werden ausschließlich verschlüsselt geschrieben.
 */
@Singleton
class DatabaseKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getOrCreateKey(): ByteArray {
        val prefs = openEncryptedPrefs()

        // 1) Bevorzugt: verschlüsselter Store.
        prefs?.getString(KEY, null)?.let { return Base64.decode(it, Base64.NO_WRAP) }

        // 2) Migration: bestehender Klartext-Key aus alten Builds bleibt lesbar,
        //    damit Bestandsnutzer ihre DB nicht verlieren. Read-only, wir schreiben
        //    hier NICHTS zurück in den Klartext-Store.
        val legacyPlain = context.applicationContext
            .getSharedPreferences(PREF_FILE_FALLBACK, Context.MODE_PRIVATE)
            .getString(KEY, null)
        if (legacyPlain != null) {
            val key = Base64.decode(legacyPlain, Base64.NO_WRAP)
            // Best effort: Legacy-Key in den verschlüsselten Store hochziehen, damit
            // der Klartext-Rest bereinigt werden kann. Nur wenn Encryption verfügbar.
            if (prefs != null) {
                runCatching {
                    prefs.edit().putString(KEY, legacyPlain).apply()
                    context.applicationContext
                        .getSharedPreferences(PREF_FILE_FALLBACK, Context.MODE_PRIVATE)
                        .edit().remove(KEY).apply()
                    Timber.i("DB-Key aus Klartext-Fallback in EncryptedSharedPreferences migriert")
                }.onFailure { Timber.w(it, "Migration des Legacy-DB-Keys fehlgeschlagen") }
            } else {
                Timber.e("DB-Key liegt im Klartext-Fallback UND EncryptedSharedPreferences ist nicht verfügbar — Key kann nicht verschlüsselt werden")
            }
            return key
        }

        // 3) Neuer Key: nur anlegen, wenn wir ihn VERSCHLÜSSELT ablegen können.
        if (prefs == null) {
            Timber.e("EncryptedSharedPreferences nicht verfügbar — es wird KEIN Klartext-DB-Key angelegt (Datenintegrität > Verfügbarkeit)")
            throw IllegalStateException(
                "DB-Schlüssel kann nicht sicher persistiert werden: EncryptedSharedPreferences nicht verfügbar"
            )
        }
        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
        return key
    }

    /** `null`, wenn EncryptedSharedPreferences nicht aufgebaut werden kann. */
    private fun openEncryptedPrefs(): SharedPreferences? = try {
        // MasterKey.Builder mit AES256_GCM + gleichem Standard-Alias
        // (MasterKey.DEFAULT_MASTER_KEY_ALIAS == MasterKeys.MASTER_KEY_ALIAS)
        // ist schlüsselkompatibel zum alten MasterKeys.AES256_GCM_SPEC — bestehende
        // verschlüsselte Prefs bleiben lesbar.
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) {
        Timber.e(t, "EncryptedSharedPreferences für DB-Key konnte nicht erstellt werden")
        null
    }

    private companion object {
        const val KEY_BYTES = 32
        const val KEY = "db_key"
        const val PREF_FILE = "de.transio.hiuni.db_key"
        const val PREF_FILE_FALLBACK = "de.transio.hiuni.db_key_plain"
    }
}
