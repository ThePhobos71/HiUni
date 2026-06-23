package de.transio.hiuni.feature.profile

import de.transio.hiuni.core.auth.UserProfile

/**
 * Snapshot der Profil-Seite. `profile` ist immer non-null (siehe [UserProfile.EMPTY]),
 * `isAuthenticated` entscheidet ob CTAs (Anmelden) oder Identity-Card gezeigt werden.
 */
data class ProfileUiState(
    val profile: UserProfile,
    val isAuthenticated: Boolean
) {
    companion object {
        val EMPTY = ProfileUiState(profile = UserProfile.EMPTY, isAuthenticated = false)
    }
}
