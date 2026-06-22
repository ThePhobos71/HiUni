package de.transio.hiuni.feature.movies.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.runCatchingApp
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
    private val scraper: MovieScraper
) : MoviesRepository {

    override fun observeUpcoming(): Flow<List<MovieEntity>> = dao.observeUpcoming()

    override suspend fun refresh(city: String): AppResult<Unit> = runCatchingApp {
        val movies = scraper.fetch(city)
        dao.replaceAll(movies)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MoviesRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMoviesRepository(impl: MoviesRepositoryImpl): MoviesRepository
}
