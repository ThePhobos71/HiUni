package de.transio.hiuni.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.notifications.data.NotificationCategory
import de.transio.hiuni.core.notifications.data.NotificationSettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel für die In-App-Kategorie-Toggles (Erinnerungen & Push → Kategorien).
 * Bewusst eigenständig vom großen [de.transio.hiuni.feature.settings.SettingsViewModel]
 * gehalten (dessen `combine` ist schon am Arity-Limit) und ohne Bezug zum
 * Push-Center-Log-VM.
 *
 * Die Toggles greifen VOR dem [de.transio.hiuni.core.notifications.NotificationPresenter]:
 * eine ausgeschaltete Kategorie unterdrückt OS-Notification UND Center-Eintrag.
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val store: NotificationSettingsStore
) : ViewModel() {

    /**
     * Enabled-Zustand je Kategorie in kanonischer Enum-Reihenfolge. Der
     * SYSTEM-Sammelkanal wird bewusst mit angeboten (er trägt Mensa/Kino/Sport/Bib).
     */
    val state: StateFlow<List<CategoryToggle>> = store.observeAll()
        .map { map ->
            NotificationCategory.entries.map { category ->
                CategoryToggle(category = category, enabled = map[category] ?: true)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(60_000),
            NotificationCategory.entries.map { CategoryToggle(it, enabled = true) }
        )

    fun setEnabled(category: NotificationCategory, enabled: Boolean) {
        viewModelScope.launch { store.setEnabled(category, enabled) }
    }

    data class CategoryToggle(
        val category: NotificationCategory,
        val enabled: Boolean
    )
}
