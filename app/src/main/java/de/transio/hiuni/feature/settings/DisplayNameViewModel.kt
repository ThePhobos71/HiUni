package de.transio.hiuni.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.UserProfile
import de.transio.hiuni.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DisplayNameUiState(
    val mode: String = SettingsDataStore.DISPLAY_NAME_MODE_FIRST,
    val customName: String = "",
    val profile: UserProfile = UserProfile.EMPTY
) {
    val firstNamePreview: String? get() = profile.firstName
    val fullVornamePreview: String? get() = profile.vorname
    val hasMultipleFirstNames: Boolean
        get() = (profile.vorname?.trim()?.split(Regex("\\s+"))?.count { it.isNotBlank() } ?: 0) >= 2
    val currentGreeting: String
        get() = when (mode) {
            SettingsDataStore.DISPLAY_NAME_MODE_CUSTOM ->
                customName.trim().takeIf { it.isNotBlank() } ?: (profile.firstName ?: "Studi")
            SettingsDataStore.DISPLAY_NAME_MODE_ALL ->
                profile.vorname?.takeIf { it.isNotBlank() } ?: "Studi"
            else -> profile.firstName ?: "Studi"
        }
}

@HiltViewModel
class DisplayNameViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    casSession: CasSession
) : ViewModel() {

    val state: StateFlow<DisplayNameUiState> = combine(
        settings.displayNameMode,
        settings.customDisplayName,
        casSession.profile
    ) { mode, custom, profile ->
        DisplayNameUiState(mode = mode, customName = custom, profile = profile)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), DisplayNameUiState())

    fun setMode(mode: String) = viewModelScope.launch {
        settings.setDisplayNameMode(mode)
    }

    fun setCustom(name: String) = viewModelScope.launch {
        settings.setCustomDisplayName(name)
    }
}
