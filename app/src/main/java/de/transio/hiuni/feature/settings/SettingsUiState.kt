package de.transio.hiuni.feature.settings

import de.transio.hiuni.feature.settings.data.HildesheimLocations
import de.transio.hiuni.feature.settings.data.MensaLocation

data class SettingsUiState(
    val locations: List<MensaLocation> = HildesheimLocations,
    val selectedLocationId: Int = 150,
    val notificationMinutesBefore: Int = 15,
    val emailSyncIntervalMinutes: Int = 30,
    val emailUsername: String = "",
    val hasStoredCredentials: Boolean = false,
    val credentialsDraft: CredentialsDraft = CredentialsDraft(),
    val message: String? = null
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
