package de.transio.hiuni.feature.movies.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.runCatchingApp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface MoviesRepository {
    fun observeUpcoming(): Flow<List<MovieEntity>>
    suspend fun refresh(city: String = "Hildesheim"): AppResult<Unit>
}

@Singleton
class MoviesRepositoryImpl @Inject constructor(
    private val dao: MovieDao,
    private val scraper: MovieScraper,
    private val tmdb: TmdbApiService
) : MoviesRepository {

    override fun observeUpcoming(): Flow<List<MovieEntity>> = dao.observeUpcoming()

    override suspend fun refresh(city: String): AppResult<Unit> = runCatchingApp {
        val scraped = scraper.fetch(city)
        val enriched = if (tmdb.isConfigured) enrichWithTmdb(scraped) else scraped
        dao.replaceAll(enriched)
    }

    private suspend fun enrichWithTmdb(movies: List<MovieEntity>): List<MovieEntity> = coroutineScope {
        movies.map { movie ->
            async {
                val match = tmdb.searchMovie(title = movie.title, year = movie.date?.year)
                    ?: return@async movie
                movie.copy(
                    posterUrl = tmdb.posterUrl(match.posterPath) ?: movie.posterUrl,
                    description = match.overview?.takeIf { it.isNotBlank() } ?: movie.description
                )
            }
        }.awaitAll()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MoviesRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMoviesRepository(impl: MoviesRepositoryImpl): MoviesRepository
}
