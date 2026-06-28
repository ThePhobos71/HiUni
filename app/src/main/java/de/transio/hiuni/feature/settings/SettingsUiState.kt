package de.transio.hiuni.feature.settings

import de.transio.hiuni.core.design.ThemeMode
import de.transio.hiuni.feature.email.MailSwipeAction
import de.transio.hiuni.feature.settings.data.HildesheimLocations
import de.transio.hiuni.feature.settings.data.MensaLocation

/**
 * Sync-Jobs, die per Hand aus den Settings angestoßen werden können.
 * Wird benutzt, um Spam-Klicks auf die jeweiligen Buttons zu verhindern.
 */
enum class SyncJob { LSF, MENSA, MOVIES, SPORT, EMAIL, TEST_NOTIFY }

data class SettingsUiState(
    val locations: List<MensaLocation> = HildesheimLocations,
    val selectedLocationId: Int = 150,
    val notificationMinutesBefore: Int = 15,
    val emailSyncIntervalMinutes: Int = 30,
    val emailUsername: String = "",
    val hasStoredCredentials: Boolean = false,
    val credentialsDraft: CredentialsDraft = CredentialsDraft(),
    val lsfSyncIntervalHours: Int = 12,
    val lastLsfSyncEpoch: Long = 0L,
    /** Letzte LSF-Klausurtermin-Refresh-Zeit. */
    val lastLsfExamsRefreshEpoch: Long = 0L,
    /** Letzte Mensa-Refresh-Zeit (System-Millis). 0 = noch nie. */
    val lastMensaRefreshEpoch: Long = 0L,
    /** Letzte Movies-Refresh-Zeit. */
    val lastMoviesRefreshEpoch: Long = 0L,
    /** Letzte Sport-Refresh-Zeit. */
    val lastSportRefreshEpoch: Long = 0L,
    /** Letzte E-Mail-Sync-Zeit. */
    val lastEmailSyncEpoch: Long = 0L,
    /**
     * Welche Sync-Jobs gerade aus den Settings heraus laufen. Solange ein Job
     * drin ist, ist sein Button disabled — verhindert Spam-Klicks, die mehrere
     * Refreshes in Folge anstoßen würden.
     */
    val runningSyncs: Set<SyncJob> = emptySet(),
    val message: String? = null,
    val mailSwipeRightAction: MailSwipeAction = MailSwipeAction.DEFAULT_RIGHT,
    val mailSwipeLeftAction: MailSwipeAction = MailSwipeAction.DEFAULT_LEFT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val mailRequiresBiometric: Boolean = false,
    val mailDeleteLocalOnly: Boolean = false,
    /**
     * Gewählte Launcher-Icon-Variante — siehe `SettingsDataStore.APP_ICON_VARIANT_*`.
     * Wird nur fürs Highlight im Settings-Picker genutzt; der eigentliche Switch
     * läuft über den AppIconManager.
     */
    val appIconVariant: String = "default"
) {
    val selectedLocation: MensaLocation?
        get() = locations.firstOrNull { it.id == selectedLocationId }
}

data class CredentialsDraft(
    val username: String = "",
    val password: String = ""
) {
    val canSave: Boolean
        get() = username.isNotBlank() && password.isNotBlank()
}

val ReminderOptions = listOf(0, 5, 10, 15, 30, 60, 120)
val SyncIntervalOptions = listOf(15, 30, 60, 120)
val LsfSyncIntervalOptions = listOf(6, 12, 24, 0)
