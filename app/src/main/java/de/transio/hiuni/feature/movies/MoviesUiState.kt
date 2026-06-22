package de.transio.hiuni.feature.movies

import de.transio.hiuni.feature.movies.data.MovieEntity

data class MoviesUiState(
    val movies: List<MovieEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)
