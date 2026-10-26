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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
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
    private val settings: SettingsDataStore,
    private val imagePrefetcher: MovieImagePrefetcher
) : MoviesRepository {

    private companion object {
        const val THROTTLE_MS = 48L * 60 * 60 * 1000 // 48 Stunden

        /** Gleichzeitige TMDB-Anfragen pro Refresh (Suche bzw. Credits). */
        const val TMDB_PARALLEL = 4
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
        // Poster und Hero-Backdrops im Hintergrund in den Bild-Cache holen, damit
        // Kino-Tab und Film-Detail sie beim Öffnen sofort zeigen. Blockiert den
        // Refresh nicht, Fehler landen nur im Log.
        runCatching { imagePrefetcher.prefetch(enriched) }
            .onFailure { Timber.w(it, "Vorladen der Kino-Bilder fehlgeschlagen") }
    }

    /**
     * Reichert das Programm mit TMDB an: Poster, Beschreibung, Backdrop, Bewertung und
     * (nur für kommende Vorstellungen) Regie und Hauptbesetzung. So muss der
     * Detail-Screen beim Öffnen nichts mehr nachladen.
     *
     * Sparsam: unifilm.de listet jede Vorstellung einzeln, gesucht wird aber nur einmal
     * pro Film (gleiche Suchkandidaten und Jahr) und Credits gibt es einmal pro
     * TMDB-Treffer. Überraschungsfilme haben keine Kandidaten (siehe
     * [tmdbSearchCandidates]) und werden gar nicht gesucht.
     */
    private suspend fun enrichWithTmdb(movies: List<MovieEntity>): List<MovieEntity> = coroutineScope {
        val today = LocalDate.now()
        val gate = Semaphore(TMDB_PARALLEL)
        val keyOf = movies.associateWith { TmdbSearchKey(tmdbSearchCandidates(it), it.date?.year) }

        val matches: Map<TmdbSearchKey, TmdbMovieResult?> = keyOf.values
            .distinct()
            .filter { it.candidates.isNotEmpty() }
            .map { key ->
                async {
                    key to gate.withPermit {
                        key.candidates.firstNotNullOfOrNull { candidate ->
                            tmdb.searchMovie(candidate, key.year)
                        }
                    }
                }
            }
            .awaitAll()
            .toMap()

        // Credits nur für Filme, die noch laufen: das Archiv öffnet niemand im Detail.
        val credits: Map<Long, TmdbCredits?> = movies
            .filter { it.isUpcoming(today) }
            .mapNotNull { matches[keyOf.getValue(it)]?.id }
            .distinct()
            .map { id -> async { id to gate.withPermit { tmdb.fetchCredits(id) } } }
            .awaitAll()
            .toMap()

        movies.map { movie ->
            val match = matches[keyOf.getValue(movie)] ?: return@map movie
            movie.withTmdbDetails(
                match = match,
                credits = match.id?.let { credits[it] },
                posterUrl = tmdb.posterUrl(match.posterPath),
                backdropUrl = tmdb.backdropUrl(match.backdropPath)
            )
        }
    }

    /** Alle Vorstellungen eines Films teilen sich denselben Schlüssel und damit eine Suche. */
    private data class TmdbSearchKey(val candidates: List<String>, val year: Int?)
}

/** Übernimmt TMDB-Treffer und -Credits in die Entity; fehlende TMDB-Werte lassen Scraper-Daten stehen. */
internal fun MovieEntity.withTmdbDetails(
    match: TmdbMovieResult,
    credits: TmdbCredits?,
    posterUrl: String?,
    backdropUrl: String?
): MovieEntity = copy(
    posterUrl = posterUrl ?: this.posterUrl,
    description = match.overview?.takeIf { it.isNotBlank() } ?: description,
    backdropUrl = backdropUrl,
    tmdbRating = match.voteAverage,
    tmdbVoteCount = match.voteCount,
    director = credits?.crew
        ?.firstOrNull { it.job.equals("Director", ignoreCase = true) }
        ?.name
        ?.takeIf { it.isNotBlank() }
        ?: director,
    castNames = credits?.cast
        ?.sortedBy { it.order ?: Int.MAX_VALUE }
        ?.mapNotNull { member -> member.name?.takeIf { it.isNotBlank() } }
        ?.take(MAX_CAST_NAMES)
        ?.takeIf { it.isNotEmpty() }
)

private const val MAX_CAST_NAMES = 6

@Module
@InstallIn(SingletonComponent::class)
abstract class MoviesRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMoviesRepository(impl: MoviesRepositoryImpl): MoviesRepository
}
