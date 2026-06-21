package de.transio.hiuni.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    val mensaLocationId: Flow<Int> = dataStore.data
        .map { it[KEY_MENSA_LOCATION_ID] ?: DEFAULT_MENSA_LOCATION_ID }

    val notificationMinutesBefore: Flow<Int> = dataStore.data
        .map { it[KEY_NOTIFICATION_MINUTES_BEFORE] ?: DEFAULT_NOTIFICATION_MINUTES }

    val emailSyncIntervalMinutes: Flow<Int> = dataStore.data
        .map { it[KEY_EMAIL_SYNC_INTERVAL] ?: DEFAULT_EMAIL_SYNC_INTERVAL }

    val navigationOrder: Flow<String> = dataStore.data
        .map { it[KEY_NAVIGATION_ORDER] ?: DEFAULT_NAVIGATION_ORDER }

    val lastEmailSyncEpoch: Flow<Long> = dataStore.data
        .map { it[KEY_LAST_EMAIL_SYNC] ?: 0L }

    suspend fun setMensaLocationId(id: Int) {
        dataStore.edit { it[KEY_MENSA_LOCATION_ID] = id }
    }

    suspend fun setNotificationMinutesBefore(minutes: Int) {
        dataStore.edit { it[KEY_NOTIFICATION_MINUTES_BEFORE] = minutes }
    }

    suspend fun setEmailSyncIntervalMinutes(minutes: Int) {
        dataStore.edit { it[KEY_EMAIL_SYNC_INTERVAL] = minutes }
    }

    suspend fun setNavigationOrder(order: String) {
        dataStore.edit { it[KEY_NAVIGATION_ORDER] = order }
    }

    suspend fun setLastEmailSyncEpoch(epoch: Long) {
        dataStore.edit { it[KEY_LAST_EMAIL_SYNC] = epoch }
    }

    companion object {
        const val DATASTORE_NAME = "hiuni_settings"
        const val DEFAULT_MENSA_LOCATION_ID = 150
        const val DEFAULT_NOTIFICATION_MINUTES = 15
        const val DEFAULT_EMAIL_SYNC_INTERVAL = 30
        const val DEFAULT_NAVIGATION_ORDER = "home,calendar,mensa,movies,bib,email"

        private val KEY_MENSA_LOCATION_ID = intPreferencesKey("mensa_location_id")
        private val KEY_NOTIFICATION_MINUTES_BEFORE = intPreferencesKey("notification_minutes_before")
        private val KEY_EMAIL_SYNC_INTERVAL = intPreferencesKey("email_sync_interval")
        private val KEY_NAVIGATION_ORDER = stringPreferencesKey("navigation_order")
        private val KEY_LAST_EMAIL_SYNC = longPreferencesKey("last_email_sync_epoch")
    }
}
