package de.transio.hiuni.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.feature.mensa.data.MensaPriceGroupResolver
import de.transio.hiuni.feature.mensa.data.PriceGroup
import de.transio.hiuni.feature.mensa.data.PriceGroupDetection
import de.transio.hiuni.feature.mensa.data.PriceGroupPreference
import de.transio.hiuni.feature.mensa.data.PriceGroupRules
import de.transio.hiuni.feature.mensa.data.PriceGroupSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MensaPriceGroupUiState(
    val preference: PriceGroupPreference = PriceGroupPreference.AUTO,
    val detection: PriceGroupDetection = PriceGroupDetection(PriceGroup.STUDENT, PriceGroupSource.DEFAULT)
) {
    /** Gruppe, deren Preise die App gerade anzeigt. */
    val effective: PriceGroup get() = PriceGroupRules.effective(preference, detection.group)

    /** Beschriftung der Automatik-Option inkl. erkannter Gruppe. */
    val autoLabel: String get() = PriceGroupRules.autoLabel(detection)
}

/**
 * Preisgruppen-Auswahl im Mensa-Einstellungsscreen. Eigenes ViewModel neben dem
 * [SettingsViewModel], damit dessen ohnehin volles `combine` nicht weiter wächst.
 */
@HiltViewModel
class MensaPriceGroupViewModel @Inject constructor(
    private val resolver: MensaPriceGroupResolver
) : ViewModel() {

    val state: StateFlow<MensaPriceGroupUiState> = combine(
        resolver.preference,
        resolver.detection
    ) { preference, detection -> MensaPriceGroupUiState(preference, detection) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MensaPriceGroupUiState())

    fun select(preference: PriceGroupPreference) {
        viewModelScope.launch { resolver.setPreference(preference) }
    }
}
