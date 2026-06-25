package de.transio.hiuni.feature.onboarding

import de.transio.hiuni.core.auth.UserProfile

/**
 * State des First-Launch-Onboarding-Pagers. `currentSlide` ist 0..3 (4 Slides).
 * `isAuthenticated` und `hasNotificationsPermission` steuern die CTA-Logik der
 * Login- bzw. Notifications-Slides (grünes Häkchen + "Weiter" statt "Erlauben").
 */
data class OnboardingUiState(
    val currentSlide: Int = 0,
    val isAuthenticated: Boolean = false,
    val hasNotificationsPermission: Boolean = false,
    val profile: UserProfile = UserProfile.EMPTY
) {
    companion object {
        const val SLIDE_COUNT = 4
        const val SLIDE_WELCOME = 0
        const val SLIDE_FEATURES = 1
        const val SLIDE_LOGIN = 2
        const val SLIDE_NOTIFICATIONS = 3
    }
}
