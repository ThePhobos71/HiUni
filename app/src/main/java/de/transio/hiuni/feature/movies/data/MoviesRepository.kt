package de.transio.hiuni.feature.movies.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.runCatchingApp
import de.transio.hiuni.core.datastore.SettingsDataStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface MoviesRepository {
    fun observeUpcoming(): Flow<List<MovieEntity>>
    fun observeArchive(): Flow<List<MovieEntity>>
    suspend fun findById(filmId: String, sessionId: String): MovieEntity?
    /**
     * Refresht das Programm. `force = false` überspringt, wenn der letzte Refresh < 48 h zurückliegt
     * — unifilm.de aktualisiert ihr Programm selten, daher reicht ein Wochenrhythmus locker.
     * Pull-to-Refresh setzt `force = true`.
     */
    suspend fun refresh(city: String = "Hildesheim", force: Boolean = false): AppResult<Unit>
}

@Singleton
class MoviesRepositoryImpl @Inject constructor(
    private val dao: MovieDao,
    private val scraper: MovieScraper,
    private val tmdb: TmdbApiService,
    private val settings: SettingsDataStore
) : MoviesRepository {

    private companion object {
        const val THROTTLE_MS = 48L * 60 * 60 * 1000 // 48 Stunden
    }

    override fun observeUpcoming(): Flow<List<MovieEntity>> =
        dao.observeUpcoming(LocalDate.now())

    override fun observeArchive(): Flow<List<MovieEntity>> =
        dao.observeArchive(LocalDate.now())

    override suspend fun findById(filmId: String, sessionId: String): MovieEntity? =
        dao.findById(filmId, sessionId)

    override suspend fun refresh(city: String, force: Boolean): AppResult<Unit> = runCatchingApp {
        if (!force) {
            val lastRefresh = settings.lastMoviesRefreshEpoch.first()
            val age = System.currentTimeMillis() - lastRefresh
            if (lastRefresh > 0 && age < THROTTLE_MS) {
                return@runCatchingApp
            }
        }
        val scraped = scraper.fetch(city)
        val enriched = if (tmdb.isConfigured) enrichWithTmdb(scraped) else scraped
        dao.replaceAll(enriched)
        settings.setLastMoviesRefreshEpoch(System.currentTimeMillis())
    }

    private suspend fun enrichWithTmdb(movies: List<MovieEntity>): List<MovieEntity> = coroutineScope {
        movies.map { movie ->
            async {
                val candidates = tmdbSearchCandidates(movie)
                val match = candidates.firstNotNullOfOrNull { candidate ->
                    tmdb.searchMovie(candidate, movie.date?.year)
                } ?: return@async movie
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
