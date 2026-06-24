package de.transio.hiuni.feature.sport

import de.transio.hiuni.feature.sport.data.SportEventEntity

data class SportUiState(
    val events: List<SportEventEntity> = emptyList(),
    val distinctTitles: List<String> = emptyList(),
    val selectedFilter: String? = null,
    val isRefreshing: Boolean = false,
    val lastError: String? = null
) {
    /** Events nach aktuell aktivem Filter — leerer Filter = alles. */
    val filteredEvents: List<SportEventEntity>
        get() = if (selectedFilter == null) events
        else events.filter { it.title == selectedFilter }
}
