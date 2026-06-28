package de.transio.hiuni.feature.onboarding

import de.transio.hiuni.core.auth.UserProfile

/**
 * State des First-Launch-Onboarding-Pagers. `currentSlide` ist 0..4 (5 Slides):
 * Hallo → Features → Login → Bio-Schutz → Notifications. `isAuthenticated`,
 * `hasNotificationsPermission` und `initialLsfSyncDone` steuern die CTA-Logik
 * der Login-/Bio-/Notifications-Slides (grünes Häkchen + "Weiter" statt CTA).
 *
 * `initialLsfSyncDone` ist `true`, sobald [de.transio.hiuni.core.datastore.SettingsDataStore.lastLsfSyncEpoch]
 * nicht mehr 0L ist ODER der 15s-Timeout für den initialen Sync-Hint abgelaufen
 * ist. Auf der Login-Slide entscheidet das Flag, ob wir noch den "Wir holen…"
 * Progress oder den "Wir machen im Hintergrund weiter"-Fallback anzeigen.
 */
data class OnboardingUiState(
    val currentSlide: Int = 0,
    val isAuthenticated: Boolean = false,
    val hasNotificationsPermission: Boolean = false,
    val profile: UserProfile = UserProfile.EMPTY,
    val initialLsfSyncDone: Boolean = false,
    val initialLsfSyncTimedOut: Boolean = false
) {
    companion object {
        const val SLIDE_COUNT = 5
        const val SLIDE_WELCOME = 0
        const val SLIDE_FEATURES = 1
        const val SLIDE_LOGIN = 2
        const val SLIDE_BIOMETRIC = 3
        const val SLIDE_NOTIFICATIONS = 4
    }
}
