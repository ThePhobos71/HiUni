package de.transio.hiuni.feature.movies

import de.transio.hiuni.feature.movies.data.MovieEntity

data class MovieDetailUiState(
    val isLoading: Boolean = true,
    val movie: MovieEntity? = null,
    val rating: Double? = null,
    val voteCount: Int? = null,
    val backdropUrl: String? = null,
    val cast: List<String> = emptyList(),
    val crewDirector: String? = null
)
