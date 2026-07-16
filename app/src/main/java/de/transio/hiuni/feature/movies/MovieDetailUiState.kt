package de.transio.hiuni.feature.movies

import de.transio.hiuni.core.common.LoadStatus
import de.transio.hiuni.feature.movies.data.MovieEntity

data class MovieDetailUiState(
    /**
     * Detail-Pane trackt nur den Erst-Load (kein Refresh, kein Fehler-Overlay).
     * Vereinheitlicht über [LoadStatus]; der `isLoading`-Accessor hält den
     * Screen-Zugriff `state.isLoading` unverändert lesbar.
     */
    val load: LoadStatus = LoadStatus.loading(),
    val movie: MovieEntity? = null,
    val rating: Double? = null,
    val voteCount: Int? = null,
    val backdropUrl: String? = null,
    val cast: List<String> = emptyList(),
    val crewDirector: String? = null
) {
    val isLoading: Boolean get() = load.isLoading
}
