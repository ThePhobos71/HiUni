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
class HomeSectionsViewModel @Inject constructor(
    private val settings: SettingsDataStore
) : ViewModel() {

    val visible: StateFlow<List<HomeSection>> = settings.homeSectionsOrder
        .map { decode(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), HomeSection.defaultVisible)

    val hidden: StateFlow<List<HomeSection>> = visible
        .map { active ->
            val activeIds = active.map { it.id }.toSet()
            HomeSection.all.filter { it.id !in activeIds }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())

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
        val section = HomeSection.fromId(id) ?: return@launch
        persist(current + section)
    }

    fun reset() = viewModelScope.launch {
        persist(HomeSection.defaultVisible)
    }

    fun setOrder(ids: List<String>) = viewModelScope.launch {
        val ordered = ids.mapNotNull { HomeSection.fromId(it) }.distinctBy { it.id }
        persist(ordered)
    }

    private suspend fun persist(list: List<HomeSection>) {
        settings.setHomeSectionsOrder(list.joinToString(",") { it.id })
    }

    private fun decode(stored: String): List<HomeSection> =
        stored.split(',')
            .mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
            .mapNotNull { HomeSection.fromId(it) }
            .distinctBy { it.id }
}
