package de.transio.hiuni.feature.movies

import de.transio.hiuni.feature.movies.data.MovieEntity

data class MoviesUiState(
    val movies: List<MovieEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    /**
     * Erst-Load ohne Cache: true vom ersten `refresh(force=false)` bis zur ersten
     * Content-Emission. Steuert den Skeleton-Platzhalter; getrennt von
     * [isRefreshing] (Pull-to-Refresh über vorhandenem Cache).
     */
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    /** Sind Filme im Cache? Steuert ErrorState/Skeleton vs. Snackbar. */
    val hasContent: Boolean get() = movies.isNotEmpty()
}
