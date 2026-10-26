package de.transio.hiuni.feature.email.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import jakarta.mail.Flags
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** IMAP-Systemflags, die die App aus der UI heraus auf dem Server setzt. */
enum class MailFlag(val imapFlag: Flags.Flag) {
    /** `\Seen`: gelesen/ungelesen. */
    SEEN(Flags.Flag.SEEN),

    /** `\Flagged`: in der App der Stern („Markiert"). */
    FLAGGED(Flags.Flag.FLAGGED)
}

/**
 * Eine noch nicht vom Server bestätigte Flag-Änderung. [folder] ist der logische
 * Ordner aus [EmailEntity.folder]; der Server-Name wird erst beim Senden aufgelöst
 * (der Sent-Ordner heißt je nach Server unterschiedlich).
 */
data class PendingMailFlag(
    val flag: MailFlag,
    val folder: String,
    val uid: Long,
    val value: Boolean
) {
    /** Schlüssel ohne Zielzustand: pro Mail und Flag gibt es höchstens einen offenen Eintrag. */
    internal val target: String get() = "${flag.name}$SEP$folder$SEP$uid$SEP"

    fun encode(): String = target + if (value) "1" else "0"

    companion object {
        private const val SEP = '|'

        fun decode(raw: String): PendingMailFlag? {
            val parts = raw.split(SEP)
            if (parts.size != 4) return null
            val flag = MailFlag.entries.firstOrNull { it.name == parts[0] } ?: return null
            val uid = parts[2].toLongOrNull() ?: return null
            val value = when (parts[3]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            return PendingMailFlag(flag, parts[1], uid, value)
        }
    }
}

/**
 * Warteschlange für Gelesen-/Markiert-Änderungen, die noch nicht auf dem IMAP-Server
 * angekommen sind (offline, Timeout, Server-Fehler). Liegt im App-weiten
 * Preferences-DataStore statt in Room: es sind nur wenige kurze Einträge, und so
 * bleibt das Datenbankschema unverändert.
 *
 * Neuer Zustand für dieselbe Mail und dasselbe Flag ersetzt den alten (der letzte
 * Klick gewinnt). [remove] entfernt nur exakt den bestätigten Zustand, damit ein
 * Klick während eines laufenden Server-Aufrufs nicht verloren geht.
 */
@Singleton
class PendingMailFlagStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    suspend fun pending(): List<PendingMailFlag> =
        dataStore.data.first()[KEY_PENDING].orEmpty()
            .mapNotNull { PendingMailFlag.decode(it) }
            .sortedWith(compareBy({ it.folder }, { it.uid }, { it.flag }))

    suspend fun enqueue(change: PendingMailFlag) {
        dataStore.edit { prefs ->
            val others = prefs[KEY_PENDING].orEmpty().filterNot { it.startsWith(change.target) }
            prefs[KEY_PENDING] = others.toSet() + change.encode()
        }
    }

    suspend fun remove(change: PendingMailFlag) {
        dataStore.edit { prefs ->
            prefs[KEY_PENDING] = prefs[KEY_PENDING].orEmpty() - change.encode()
        }
    }

    private companion object {
        val KEY_PENDING = stringSetPreferencesKey("email_pending_flag_changes")
    }
}
