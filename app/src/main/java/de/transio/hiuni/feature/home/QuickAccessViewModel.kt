package de.transio.hiuni.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickAccessViewModel @Inject constructor(
    private val settings: SettingsDataStore
) : ViewModel() {

    val visible: StateFlow<List<QuickAccessTile>> = settings.homeQuickAccessOrder
        .map { decode(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickAccessTile.defaultVisible)

    val hidden: StateFlow<List<QuickAccessTile>> = visible
        .map { active ->
            val activeIds = active.map { it.id }.toSet()
            QuickAccessTile.all.filter { it.id !in activeIds }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun move(id: String, direction: Int) = viewModelScope.launch {
        val current = visible.value.toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        val target = idx + direction
        if (idx < 0 || target < 0 || target >= current.size) return@launch
        val tmp = current[idx]
        current[idx] = current[target]
        current[target] = tmp
        persist(current)
    }

    fun remove(id: String) = viewModelScope.launch {
        persist(visible.value.filterNot { it.id == id })
    }

    fun add(id: String) = viewModelScope.launch {
        val current = visible.value
        if (current.any { it.id == id }) return@launch
        val tile = QuickAccessTile.fromId(id) ?: return@launch
        persist(current + tile)
    }

    fun reset() = viewModelScope.launch {
        persist(QuickAccessTile.defaultVisible)
    }

    fun setOrder(ids: List<String>) = viewModelScope.launch {
        val ordered = ids.mapNotNull { QuickAccessTile.fromId(it) }.distinctBy { it.id }
        persist(ordered)
    }

    private suspend fun persist(list: List<QuickAccessTile>) {
        settings.setHomeQuickAccessOrder(list.joinToString(",") { it.id })
    }

    private fun decode(stored: String): List<QuickAccessTile> =
        stored.split(',')
            .mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
            .mapNotNull { QuickAccessTile.fromId(it) }
            .distinctBy { it.id }
}
