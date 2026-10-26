package de.transio.hiuni.feature.movies

import de.transio.hiuni.core.common.LoadStatus
import de.transio.hiuni.feature.movies.data.MovieEntity

data class MoviesUiState(
    val movies: List<MovieEntity> = emptyList(),
    /**
     * Vereinheitlichter Lade-/Fehler-Status (siehe [LoadStatus]). Die
     * delegierenden Accessoren unten halten `state.isLoading`/`isRefreshing`/
     * `errorMessage` in Screens und Tests unverändert lesbar.
     */
    val load: LoadStatus = LoadStatus.Idle,
    /**
     * `false` bis Room zum ersten Mal geliefert hat. Unterscheidet „noch nicht gelesen"
     * von „gelesen und leer", damit der Empty-State nicht vor dem Cache aufblitzt.
     */
    val isContentLoaded: Boolean = false
) {
    val isRefreshing: Boolean get() = load.isRefreshing
    val isLoading: Boolean get() = load.isLoading
    val errorMessage: String? get() = load.error

    /** Sind Filme im Cache? Steuert ErrorState/Skeleton vs. Snackbar. */
    val hasContent: Boolean get() = movies.isNotEmpty()
}
