package de.transio.hiuni.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.navigation.Destination
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

const val MIN_NAV_TABS = 2
const val MAX_NAV_TABS = 5

@HiltViewModel
class NavTabsViewModel @Inject constructor(
    private val settings: SettingsDataStore
) : ViewModel() {

    val tabs: StateFlow<List<Destination>> = settings.navigationOrder
        .map { stored -> decode(stored).ifEmpty { Destination.defaultPrimary } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Destination.defaultPrimary)

    val availableForAdd: StateFlow<List<Destination>> = tabs
        .map { active ->
            val activeRoutes = active.map { it.route }.toSet()
            Destination.all.filter { it.route !in activeRoutes }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun move(route: String, direction: Int) = viewModelScope.launch {
        val current = tabs.value.toMutableList()
        val idx = current.indexOfFirst { it.route == route }
        val target = idx + direction
        if (idx < 0 || target < 0 || target >= current.size) return@launch
        val tmp = current[idx]
        current[idx] = current[target]
        current[target] = tmp
        persist(current)
    }

    fun remove(route: String) = viewModelScope.launch {
        val current = tabs.value
        if (current.size <= MIN_NAV_TABS) return@launch
        if (route == Destination.Home.route) return@launch // Home darf nicht entfernt werden
        persist(current.filterNot { it.route == route })
    }

    fun add(route: String) = viewModelScope.launch {
        val current = tabs.value
        if (current.size >= MAX_NAV_TABS) return@launch
        if (current.any { it.route == route }) return@launch
        val dest = Destination.fromRoute(route) ?: return@launch
        persist(current + dest)
    }

    fun reset() = viewModelScope.launch {
        persist(Destination.defaultPrimary)
    }

    private suspend fun persist(list: List<Destination>) {
        settings.setNavigationOrder(list.joinToString(",") { it.route })
    }

    private fun decode(stored: String): List<Destination> =
        stored.split(',')
            .mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
            .mapNotNull { Destination.fromRoute(it) }
            .distinctBy { it.route }
            .take(MAX_NAV_TABS)
}
