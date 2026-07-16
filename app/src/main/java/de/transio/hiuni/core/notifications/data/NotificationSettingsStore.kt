package de.transio.hiuni.core.notifications.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-App-Feintuning für Benachrichtigungs-Kategorien. Liegt VOR dem
 * [de.transio.hiuni.core.notifications.NotificationPresenter]: eine ausgeschaltete
 * Kategorie unterdrückt sowohl die OS-Notification ALS AUCH den Push-Center-Eintrag,
 * bevor überhaupt etwas geschrieben wird.
 *
 * Damit gibt es zwei komplementäre Stellschrauben:
 *  - Android-Notification-Channel (Systemeinstellungen) → stummschalten der
 *    OS-Notification, Push-Center-Log läuft aber weiter.
 *  - Dieser In-App-Toggle → Kategorie komplett aus, auch im Center.
 *
 * Default für jede Kategorie: AN. Nur explizit ausgeschaltete Kategorien werden
 * persistiert (fehlender Key ⇒ an), damit neu hinzukommende Kategorien automatisch
 * aktiv sind, ohne Migration.
 */
@Singleton
class NotificationSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    /** Reaktiver Enabled-Zustand einer Kategorie (Default: an). */
    fun observeEnabled(category: NotificationCategory): Flow<Boolean> =
        dataStore.data.map { it[keyFor(category)] ?: true }

    /** Alle Kategorien mit ihrem aktuellen Enabled-Zustand — für die Settings-UI. */
    fun observeAll(): Flow<Map<NotificationCategory, Boolean>> =
        dataStore.data.map { prefs ->
            NotificationCategory.entries.associateWith { prefs[keyFor(it)] ?: true }
        }

    /**
     * Gate für den Presenter: darf für dieses [NotificationKind] überhaupt etwas
     * gefeuert/geloggt werden? Fehlender Key ⇒ `true` (an).
     */
    suspend fun isEnabled(kind: NotificationKind): Boolean {
        val category = NotificationCategory.of(kind)
        return dataStore.data.first()[keyFor(category)] ?: true
    }

    suspend fun setEnabled(category: NotificationCategory, enabled: Boolean) {
        dataStore.edit { it[keyFor(category)] = enabled }
    }

    private fun keyFor(category: NotificationCategory) =
        booleanPreferencesKey("notif_category_${category.name.lowercase()}_enabled")
}
