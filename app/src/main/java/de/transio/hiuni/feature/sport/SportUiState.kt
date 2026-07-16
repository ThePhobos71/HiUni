package de.transio.hiuni.feature.sport

import de.transio.hiuni.core.common.LoadStatus
import de.transio.hiuni.feature.sport.data.SportEventEntity

data class SportUiState(
    val events: List<SportEventEntity> = emptyList(),
    val distinctTitles: List<String> = emptyList(),
    val selectedFilter: String? = null,
    /**
     * Vereinheitlichter Lade-/Fehler-Status (siehe [LoadStatus]). Sport kennt
     * keinen separaten Erst-Load-Skeleton — leerer Cache + `isRefreshing`
     * steuern den Platzhalter. Der `errorMessage`-Accessor löst den früheren
     * uneinheitlichen `lastError`-Namen auf den Standard auf.
     */
    val load: LoadStatus = LoadStatus.Idle,
    /** Prozessweiter Netz-Status. Für die Stale-/Offline-Kennzeichnung im Header. */
    val isOnline: Boolean = true,
    /** Epoch-ms des letzten erfolgreichen Sport-Refresh (0 = nie). Speist das StalenessLabel. */
    val lastRefreshEpoch: Long = 0L
) {
    val isRefreshing: Boolean get() = load.isRefreshing
    val errorMessage: String? get() = load.error

    /** Events nach aktuell aktivem Filter — leerer Filter = alles. */
    val filteredEvents: List<SportEventEntity>
        get() = if (selectedFilter == null) events
        else events.filter { it.title == selectedFilter }
}
