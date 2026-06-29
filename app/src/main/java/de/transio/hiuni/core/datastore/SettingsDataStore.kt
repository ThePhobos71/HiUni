package de.transio.hiuni.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

    val homeSectionsOrder: Flow<String> = dataStore.data
        .map { it[KEY_HOME_SECTIONS_ORDER] ?: DEFAULT_HOME_SECTIONS_ORDER }

    val homeQuickAccessOrder: Flow<String> = dataStore.data
        .map { it[KEY_HOME_QUICK_ACCESS_ORDER] ?: DEFAULT_HOME_QUICK_ACCESS_ORDER }

    val lastEmailSyncEpoch: Flow<Long> = dataStore.data
        .map { it[KEY_LAST_EMAIL_SYNC] ?: 0L }

    val lastMensaRefreshEpoch: Flow<Long> = dataStore.data
        .map { it[KEY_LAST_MENSA_REFRESH] ?: 0L }

    val lastMoviesRefreshEpoch: Flow<Long> = dataStore.data
        .map { it[KEY_LAST_MOVIES_REFRESH] ?: 0L }

    val lastSportRefreshEpoch: Flow<Long> = dataStore.data
        .map { it[KEY_LAST_SPORT_REFRESH] ?: 0L }

    val lastLearnwebRefreshEpoch: Flow<Long> = dataStore.data
        .map { it[KEY_LAST_LEARNWEB_REFRESH] ?: 0L }

    /**
     * LSF-Auto-Sync-Intervall in Stunden. `0` = aus (kein Periodic-Worker),
     * sonst 6 / 12 / 24. Default 12h, damit wir LSF schonen.
     */
    val lsfSyncIntervalHours: Flow<Int> = dataStore.data
        .map { it[KEY_LSF_SYNC_INTERVAL_HOURS] ?: DEFAULT_LSF_SYNC_INTERVAL_HOURS }

    val lastLsfSyncEpoch: Flow<Long> = dataStore.data
        .map { it[KEY_LAST_LSF_SYNC] ?: 0L }

    /**
     * Timestamp der letzten erfolgreichen Klausurtermin-Synchronisation. Separat
     * vom MyCourses-/Stundenplan-Timestamp gespeichert, damit der Sync-Status-Screen
     * sieht, dass die Exams-Phase eigenständig laufen kann.
     */
    val lastLsfExamsRefreshEpoch: Flow<Long> = dataStore.data
        .map { it[KEY_LAST_LSF_EXAMS_REFRESH] ?: 0L }

    /**
     * `true`, sobald der User das Onboarding (4-Slide-Pager beim Erststart)
     * mit "Loslegen" abgeschlossen hat. Default `false` → Onboarding wird
     * gezeigt. Nach `setOnboardingCompleted(true)` taucht es nie wieder auf,
     * außer der User löscht App-Daten oder deinstalliert.
     */
    val onboardingCompleted: Flow<Boolean> = dataStore.data
        .map { it[KEY_ONBOARDING_COMPLETED] ?: false }

    /** Mail-Swipe-Action für Wisch nach rechts (StartToEnd). Default: archive. */
    val mailSwipeRightAction: Flow<String> = dataStore.data
        .map { it[KEY_MAIL_SWIPE_RIGHT] ?: DEFAULT_MAIL_SWIPE_RIGHT }

    /** Mail-Swipe-Action für Wisch nach links (EndToStart). Default: delete. */
    val mailSwipeLeftAction: Flow<String> = dataStore.data
        .map { it[KEY_MAIL_SWIPE_LEFT] ?: DEFAULT_MAIL_SWIPE_LEFT }

    /** Theme-Override: "system" / "light" / "dark". Default folgt dem OS. */
    val themeMode: Flow<String> = dataStore.data
        .map { it[KEY_THEME_MODE] ?: DEFAULT_THEME_MODE }

    /**
     * Gewählte Launcher-Icon-Variante. Werte: "default" / "dark" / "classic" /
     * "studi". Der eigentliche Switch passiert über activity-alias +
     * PackageManager.setComponentEnabledSetting() im [AppIconManager]; dieser
     * String hier ist nur der "was hat der User zuletzt gewählt"-Spiegel für
     * die Settings-UI (Highlight + Persistenz nach Re-Install ist aber
     * pragmatisch — der Alias-Zustand übersteht App-Restarts ja sowieso).
     */
    val appIconVariant: Flow<String> = dataStore.data
        .map { it[KEY_APP_ICON_VARIANT] ?: DEFAULT_APP_ICON_VARIANT }

    /**
     * Erstes Semester, in dem der User die App genutzt hat — Format wie in
     * [Semester.storageKey] (z.B. "2026W" für WS 2026/27). Leere Zeichenkette
     * = noch nicht initialisiert; wird beim ersten App-Start auf das aktuelle
     * Semester gesetzt (siehe AppIconManager). Basis für „Ab welchem Semester
     * ist Icon X freigeschaltet?"
     */
    val firstSemesterKey: Flow<String> = dataStore.data
        .map { it[KEY_FIRST_SEMESTER] ?: "" }

    /**
     * Wenn `true`, muss der User die Mail-Liste mit Fingerabdruck/Face-ID/PIN
     * entsperren bevor sie sichtbar wird. Default `false` — Opt-in via Settings.
     */
    val mailRequiresBiometric: Flow<Boolean> = dataStore.data
        .map { it[KEY_MAIL_REQUIRES_BIOMETRIC] ?: false }

    /**
     * Wenn `true`, löscht Swipe-Delete die Mail nur lokal (isHiddenLocally-Flag);
     * der IMAP-Server behält sie. Sinnvoll wenn der User Mails auf mehreren
     * Geräten nutzt und nur die App-Sicht aufräumen will.
     */
    val mailDeleteLocalOnly: Flow<Boolean> = dataStore.data
        .map { it[KEY_MAIL_DELETE_LOCAL_ONLY] ?: false }

    /**
     * Reminder-IDs, die der [ExamReminderScheduler] aktuell im AlarmManager
     * geschedult hat — als CSV-String persistiert. Brauchen wir, weil
     * AlarmManager selbst keine "alle laufenden Alarme abfragen"-API hat:
     * beim nächsten Sync diffen wir gegen die neue Soll-Menge und canceln
     * verwaiste IDs (z.B. wenn LSF eine Klausur zurückgezogen hat).
     */
    val scheduledExamReminderIds: Flow<Set<Long>> = dataStore.data
        .map { prefs ->
            prefs[KEY_SCHEDULED_EXAM_REMINDER_IDS].orEmpty()
                .split(',')
                .mapNotNull { it.trim().toLongOrNull() }
                .toSet()
        }

    /**
     * Reminder-IDs, die der Learnweb-Assignment-Reminder-Scheduler aktuell im
     * AlarmManager geplant hat — analog zu [scheduledExamReminderIds]. Beim
     * nächsten Sync diffen wir gegen die neue Soll-Menge und canceln verwaiste IDs.
     */
    val scheduledLearnwebReminderIds: Flow<Set<Long>> = dataStore.data
        .map { prefs ->
            prefs[KEY_SCHEDULED_LEARNWEB_REMINDER_IDS].orEmpty()
                .split(',')
                .mapNotNull { it.trim().toLongOrNull() }
                .toSet()
        }

    // Letzter MensaCard-Scan. Wert in 1/1000 €, Source = "INTERCARD"/"MAGNACARTA"
    // damit das ViewModel die Quelle anzeigen kann ohne Mapping-Tabelle.
    val mensaCardBalanceMilliEuro: Flow<Int> = dataStore.data
        .map { it[KEY_MENSA_CARD_VALUE] ?: -1 }
    val mensaCardUid: Flow<String> = dataStore.data
        .map { it[KEY_MENSA_CARD_UID] ?: "" }
    val mensaCardSource: Flow<String> = dataStore.data
        .map { it[KEY_MENSA_CARD_SOURCE] ?: "" }
    val mensaCardScannedEpoch: Flow<Long> = dataStore.data
        .map { it[KEY_MENSA_CARD_SCANNED_AT] ?: 0L }

    // UID der vom User festgelegten "eigenen" Karte. Leer = noch keine Karte
    // als Primärkarte markiert. Scans fremder Karten werden NICHT in den
    // Transaktions-Verlauf der eigenen Karte geschrieben.
    val mensaCardPrimaryUid: Flow<String> = dataStore.data
        .map { it[KEY_MENSA_CARD_PRIMARY_UID] ?: "" }

    /**
     * Betrag der letzten Abbuchung wie die Karte sie meldet (DESfire
     * LimitedCreditValue aus File-Settings, in 1/1000 €, immer positiv).
     * Überlebt App-Restarts. `0` heißt: noch nie gelesen oder seit dem
     * letzten Refund auf 0 gesetzt.
     */
    val mensaCardOnCardLastDebitMilliEuro: Flow<Int> = dataStore.data
        .map { it[KEY_MENSA_CARD_ONCARD_LAST_DEBIT] ?: 0 }

    /**
     * Anzeigename-Modus für Greetings: "first" = nur erster Vorname,
     * "all" = alle Vornamen, "custom" = customDisplayName-Wert.
     */
    val displayNameMode: Flow<String> = dataStore.data
        .map { it[KEY_DISPLAY_NAME_MODE] ?: DEFAULT_DISPLAY_NAME_MODE }

    val customDisplayName: Flow<String> = dataStore.data
        .map { it[KEY_CUSTOM_DISPLAY_NAME] ?: "" }

    suspend fun setDisplayNameMode(mode: String) {
        dataStore.edit { it[KEY_DISPLAY_NAME_MODE] = mode }
    }

    suspend fun setCustomDisplayName(name: String) {
        dataStore.edit { it[KEY_CUSTOM_DISPLAY_NAME] = name }
    }

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

    suspend fun setMailSwipeRightAction(key: String) {
        dataStore.edit { it[KEY_MAIL_SWIPE_RIGHT] = key }
    }

    suspend fun setMailSwipeLeftAction(key: String) {
        dataStore.edit { it[KEY_MAIL_SWIPE_LEFT] = key }
    }

    suspend fun setThemeMode(key: String) {
        dataStore.edit { it[KEY_THEME_MODE] = key }
    }

    suspend fun setAppIconVariant(variant: String) {
        dataStore.edit { it[KEY_APP_ICON_VARIANT] = variant }
    }

    /**
     * Erst-Semester nur setzen wenn der Slot noch leer ist — der Wert ist eine
     * Einmal-Initialisierung beim allerersten App-Start und darf später nie
     * überschrieben werden, sonst würden die „Unlock nach N Semestern"-Gates
     * wieder von vorne zählen.
     */
    suspend fun initFirstSemesterIfMissing(key: String) {
        dataStore.edit { prefs ->
            if (prefs[KEY_FIRST_SEMESTER].isNullOrBlank()) {
                prefs[KEY_FIRST_SEMESTER] = key
            }
        }
    }

    suspend fun setMailRequiresBiometric(enabled: Boolean) {
        dataStore.edit { it[KEY_MAIL_REQUIRES_BIOMETRIC] = enabled }
    }

    suspend fun setMailDeleteLocalOnly(enabled: Boolean) {
        dataStore.edit { it[KEY_MAIL_DELETE_LOCAL_ONLY] = enabled }
    }

    suspend fun setHomeSectionsOrder(order: String) {
        dataStore.edit { it[KEY_HOME_SECTIONS_ORDER] = order }
    }

    suspend fun setHomeQuickAccessOrder(order: String) {
        dataStore.edit { it[KEY_HOME_QUICK_ACCESS_ORDER] = order }
    }

    suspend fun setLastEmailSyncEpoch(epoch: Long) {
        dataStore.edit { it[KEY_LAST_EMAIL_SYNC] = epoch }
    }

    suspend fun setLastMensaRefreshEpoch(epoch: Long) {
        dataStore.edit { it[KEY_LAST_MENSA_REFRESH] = epoch }
    }

    suspend fun setLastMoviesRefreshEpoch(epoch: Long) {
        dataStore.edit { it[KEY_LAST_MOVIES_REFRESH] = epoch }
    }

    suspend fun setLastSportRefreshEpoch(epoch: Long) {
        dataStore.edit { it[KEY_LAST_SPORT_REFRESH] = epoch }
    }

    suspend fun setLastLearnwebRefreshEpoch(epoch: Long) {
        dataStore.edit { it[KEY_LAST_LEARNWEB_REFRESH] = epoch }
    }

    suspend fun setMensaCardScan(uid: String, valueMilliEuro: Int, source: String, epoch: Long) {
        dataStore.edit {
            it[KEY_MENSA_CARD_UID] = uid
            it[KEY_MENSA_CARD_VALUE] = valueMilliEuro
            it[KEY_MENSA_CARD_SOURCE] = source
            it[KEY_MENSA_CARD_SCANNED_AT] = epoch
        }
    }

    suspend fun setMensaCardPrimaryUid(uid: String) {
        dataStore.edit { it[KEY_MENSA_CARD_PRIMARY_UID] = uid }
    }

    suspend fun setMensaCardOnCardLastDebitMilliEuro(amount: Int) {
        dataStore.edit { it[KEY_MENSA_CARD_ONCARD_LAST_DEBIT] = amount }
    }

    suspend fun setLsfSyncIntervalHours(hours: Int) {
        dataStore.edit { it[KEY_LSF_SYNC_INTERVAL_HOURS] = hours }
    }

    suspend fun setLastLsfSyncEpoch(epoch: Long) {
        dataStore.edit { it[KEY_LAST_LSF_SYNC] = epoch }
    }

    suspend fun setLastLsfExamsRefreshEpoch(epoch: Long) {
        dataStore.edit { it[KEY_LAST_LSF_EXAMS_REFRESH] = epoch }
    }

    suspend fun setOnboardingCompleted(done: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = done }
    }

    suspend fun setScheduledExamReminderIds(ids: Set<Long>) {
        dataStore.edit {
            it[KEY_SCHEDULED_EXAM_REMINDER_IDS] = ids.joinToString(",")
        }
    }

    suspend fun setScheduledLearnwebReminderIds(ids: Set<Long>) {
        dataStore.edit {
            it[KEY_SCHEDULED_LEARNWEB_REMINDER_IDS] = ids.joinToString(",")
        }
    }

    companion object {
        const val DATASTORE_NAME = "hiuni_settings"
        const val DEFAULT_MENSA_LOCATION_ID = 150
        const val DEFAULT_NOTIFICATION_MINUTES = 15
        const val DEFAULT_EMAIL_SYNC_INTERVAL = 30
        const val DEFAULT_NAVIGATION_ORDER = "home,calendar,mensa,courses,email"
        const val DEFAULT_HOME_SECTIONS_ORDER = "quick_access,today,exams,films"
        const val DEFAULT_HOME_QUICK_ACCESS_ORDER = "mensa,bib,email,tasks"
        const val DEFAULT_DISPLAY_NAME_MODE = "first"
        const val DISPLAY_NAME_MODE_FIRST = "first"
        const val DISPLAY_NAME_MODE_ALL = "all"
        const val DISPLAY_NAME_MODE_CUSTOM = "custom"
        const val DEFAULT_LSF_SYNC_INTERVAL_HOURS = 12
        const val DEFAULT_MAIL_SWIPE_RIGHT = "archive"
        const val DEFAULT_MAIL_SWIPE_LEFT = "delete"
        const val DEFAULT_THEME_MODE = "system"
        const val DEFAULT_APP_ICON_VARIANT = "default"
        const val APP_ICON_VARIANT_DEFAULT = "default"
        const val APP_ICON_VARIANT_DARK = "dark"
        const val APP_ICON_VARIANT_CLASSIC = "classic"
        const val APP_ICON_VARIANT_STUDI = "studi"

        private val KEY_MENSA_LOCATION_ID = intPreferencesKey("mensa_location_id")
        private val KEY_NOTIFICATION_MINUTES_BEFORE = intPreferencesKey("notification_minutes_before")
        private val KEY_EMAIL_SYNC_INTERVAL = intPreferencesKey("email_sync_interval")
        private val KEY_NAVIGATION_ORDER = stringPreferencesKey("navigation_order")
        private val KEY_HOME_SECTIONS_ORDER = stringPreferencesKey("home_sections_order")
        private val KEY_HOME_QUICK_ACCESS_ORDER = stringPreferencesKey("home_quick_access_order")
        private val KEY_LAST_EMAIL_SYNC = longPreferencesKey("last_email_sync_epoch")
        private val KEY_LAST_MENSA_REFRESH = longPreferencesKey("last_mensa_refresh_epoch")
        private val KEY_LAST_MOVIES_REFRESH = longPreferencesKey("last_movies_refresh_epoch")
        private val KEY_LAST_SPORT_REFRESH = longPreferencesKey("last_sport_refresh_epoch")
        private val KEY_LAST_LEARNWEB_REFRESH = longPreferencesKey("last_learnweb_refresh_epoch")
        private val KEY_DISPLAY_NAME_MODE = stringPreferencesKey("display_name_mode")
        private val KEY_CUSTOM_DISPLAY_NAME = stringPreferencesKey("custom_display_name")
        private val KEY_MENSA_CARD_VALUE = intPreferencesKey("mensa_card_value_milli")
        private val KEY_MENSA_CARD_UID = stringPreferencesKey("mensa_card_uid")
        private val KEY_MENSA_CARD_SOURCE = stringPreferencesKey("mensa_card_source")
        private val KEY_MENSA_CARD_SCANNED_AT = longPreferencesKey("mensa_card_scanned_at")
        private val KEY_MENSA_CARD_PRIMARY_UID = stringPreferencesKey("mensa_card_primary_uid")
        private val KEY_MENSA_CARD_ONCARD_LAST_DEBIT = intPreferencesKey("mensa_card_oncard_last_debit")
        private val KEY_LSF_SYNC_INTERVAL_HOURS = intPreferencesKey("lsf_sync_interval_hours")
        private val KEY_LAST_LSF_SYNC = longPreferencesKey("last_lsf_sync_epoch")
        private val KEY_LAST_LSF_EXAMS_REFRESH = longPreferencesKey("last_lsf_exams_refresh_epoch")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_SCHEDULED_EXAM_REMINDER_IDS = stringPreferencesKey("scheduled_exam_reminder_ids")
        private val KEY_SCHEDULED_LEARNWEB_REMINDER_IDS = stringPreferencesKey("scheduled_learnweb_reminder_ids")
        private val KEY_MAIL_SWIPE_RIGHT = stringPreferencesKey("mail_swipe_right_action")
        private val KEY_MAIL_SWIPE_LEFT = stringPreferencesKey("mail_swipe_left_action")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_APP_ICON_VARIANT = stringPreferencesKey("app_icon_variant")
        private val KEY_FIRST_SEMESTER = stringPreferencesKey("first_semester")
        private val KEY_MAIL_REQUIRES_BIOMETRIC = booleanPreferencesKey("mail_requires_biometric")
        private val KEY_MAIL_DELETE_LOCAL_ONLY = booleanPreferencesKey("mail_delete_local_only")
    }
}
