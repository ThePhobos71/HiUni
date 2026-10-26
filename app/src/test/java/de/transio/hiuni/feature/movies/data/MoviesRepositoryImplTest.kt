package de.transio.hiuni.feature.movies.data

import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.datastore.SettingsDataStore
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class MoviesRepositoryImplTest {

    private val dao = mockk<MovieDao>()
    private val scraper = mockk<MovieScraper>()
    private val tmdb = mockk<TmdbApiService>()
    private val settings = mockk<SettingsDataStore>()
    private val prefetcher = mockk<MovieImagePrefetcher>()
    private val stored = slot<List<MovieEntity>>()

    private val today = LocalDate.now()

    private val repo = MoviesRepositoryImpl(dao, scraper, tmdb, settings, prefetcher)

    private val duneMatch = TmdbMovieResult(
        id = 42,
        title = "Dune: Part Two",
        overview = "Paul vereint sich mit den Fremen.",
        posterPath = "/dune-poster.jpg",
        backdropPath = "/dune-backdrop.jpg",
        voteAverage = 8.2,
        voteCount = 5400
    )

    private val duneCredits = TmdbCredits(
        id = 42,
        cast = listOf(
            TmdbCastMember(name = "Zendaya", order = 1),
            TmdbCastMember(name = "Timothée Chalamet", order = 0),
            TmdbCastMember(name = "Rebecca Ferguson", order = 2),
            TmdbCastMember(name = "Josh Brolin", order = 3),
            TmdbCastMember(name = "Austin Butler", order = 4),
            TmdbCastMember(name = "Florence Pugh", order = 5),
            TmdbCastMember(name = "Dave Bautista", order = 6)
        ),
        crew = listOf(
            TmdbCrewMember(name = "Greig Fraser", job = "Director of Photography"),
            TmdbCrewMember(name = "Denis Villeneuve", job = "Director")
        )
    )

    @Before
    fun setUp() {
        every { settings.lastMoviesRefreshEpoch } returns flowOf(0L)
        coEvery { settings.setLastMoviesRefreshEpoch(any()) } just Runs
        coEvery { dao.replaceAll(capture(stored)) } just Runs
        every { prefetcher.prefetch(any()) } just Runs
        every { tmdb.isConfigured } returns true
        every { tmdb.posterUrl(any(), any()) } answers {
            firstArg<String?>()?.let { "https://image.tmdb.org/t/p/w780$it" }
        }
        every { tmdb.backdropUrl(any(), any()) } answers {
            firstArg<String?>()?.let { "https://image.tmdb.org/t/p/w1280$it" }
        }
        coEvery { tmdb.searchMovie(any(), any()) } returns null
        coEvery { tmdb.fetchCredits(any()) } returns null
    }

    private fun movie(
        filmId: String,
        sessionId: String,
        title: String,
        date: LocalDate? = today.plusDays(2),
        isPast: Boolean = false,
        director: String? = null
    ) = MovieEntity(
        filmId = filmId,
        sessionId = sessionId,
        title = title,
        date = date,
        isPast = isPast,
        posterUrl = "https://unifilm.de/$filmId.jpg",
        director = director
    )

    @Test
    fun `TMDB-Details landen in der gespeicherten Entity`() = runTest {
        coEvery { scraper.fetch(any()) } returns listOf(
            movie("10", "a", "Dune: Part Two", director = "D. Villeneuve")
        )
        coEvery { tmdb.searchMovie("Dune: Part Two", any()) } returns duneMatch
        coEvery { tmdb.fetchCredits(42) } returns duneCredits

        val result = repo.refresh(force = true)

        assertTrue(result is AppResult.Success)
        val dune = stored.captured.single()
        assertEquals("https://image.tmdb.org/t/p/w780/dune-poster.jpg", dune.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w1280/dune-backdrop.jpg", dune.backdropUrl)
        assertEquals("Paul vereint sich mit den Fremen.", dune.description)
        assertEquals(8.2, dune.tmdbRating!!, 0.0001)
        assertEquals(5400, dune.tmdbVoteCount)
        // TMDB-Regie schlägt die Scraper-Heuristik (wie bisher im Detail-Screen).
        assertEquals("Denis Villeneuve", dune.director)
        // Top 6 nach TMDB-`order`, nicht nach Listenreihenfolge.
        assertEquals(
            listOf(
                "Timothée Chalamet", "Zendaya", "Rebecca Ferguson",
                "Josh Brolin", "Austin Butler", "Florence Pugh"
            ),
            dune.castNames
        )
    }

    @Test
    fun `mehrere Vorstellungen desselben Films kosten nur eine Suche und einmal Credits`() = runTest {
        coEvery { scraper.fetch(any()) } returns listOf(
            movie("10", "a", "Dune: Part Two"),
            movie("10", "b", "Dune: Part Two", date = today.plusDays(4)),
            movie("10", "c", "Dune: Part Two", date = today.plusDays(6))
        )
        coEvery { tmdb.searchMovie("Dune: Part Two", any()) } returns duneMatch
        coEvery { tmdb.fetchCredits(42) } returns duneCredits

        repo.refresh(force = true)

        coVerify(exactly = 1) { tmdb.searchMovie(any(), any()) }
        coVerify(exactly = 1) { tmdb.fetchCredits(42) }
        assertEquals(3, stored.captured.size)
        stored.captured.forEach { row ->
            assertEquals("https://image.tmdb.org/t/p/w1280/dune-backdrop.jpg", row.backdropUrl)
            assertEquals("Denis Villeneuve", row.director)
            assertEquals(6, row.castNames!!.size)
        }
    }

    @Test
    fun `ohne Treffer keine Credits, Scraper-Daten bleiben`() = runTest {
        coEvery { scraper.fetch(any()) } returns listOf(
            movie("11", "a", "Unbekannter Kurzfilm", director = "Jane Doe")
        )

        repo.refresh(force = true)

        coVerify(exactly = 0) { tmdb.fetchCredits(any()) }
        val row = stored.captured.single()
        assertEquals("https://unifilm.de/11.jpg", row.posterUrl)
        assertEquals("Jane Doe", row.director)
        assertNull(row.backdropUrl)
        assertNull(row.tmdbRating)
        assertNull(row.tmdbVoteCount)
        assertNull(row.castNames)
    }

    @Test
    fun `Ueberraschungsfilm wird nie bei TMDB gesucht`() = runTest {
        coEvery { scraper.fetch(any()) } returns listOf(movie("12", "a", "Filmabend"))

        repo.refresh(force = true)

        coVerify(exactly = 0) { tmdb.searchMovie(any(), any()) }
        coVerify(exactly = 0) { tmdb.fetchCredits(any()) }
        assertNull(stored.captured.single().backdropUrl)
    }

    @Test
    fun `vergangene Vorstellungen bekommen Suchdaten, aber keine Credits`() = runTest {
        coEvery { scraper.fetch(any()) } returns listOf(
            movie("13", "a", "Alter Film", date = today.minusDays(3)),
            movie("14", "a", "Archivfilm", isPast = true)
        )
        coEvery { tmdb.searchMovie("Alter Film", any()) } returns duneMatch.copy(id = 7)
        coEvery { tmdb.searchMovie("Archivfilm", any()) } returns duneMatch.copy(id = 8)

        repo.refresh(force = true)

        coVerify(exactly = 0) { tmdb.fetchCredits(any()) }
        stored.captured.forEach { row ->
            assertEquals("https://image.tmdb.org/t/p/w1280/dune-backdrop.jpg", row.backdropUrl)
            assertNull(row.castNames)
        }
    }

    @Test
    fun `nach erfolgreichem Refresh werden die gespeicherten Filme zum Vorladen gereicht`() = runTest {
        coEvery { scraper.fetch(any()) } returns listOf(movie("10", "a", "Dune: Part Two"))
        coEvery { tmdb.searchMovie("Dune: Part Two", any()) } returns duneMatch

        repo.refresh(force = true)

        verify(exactly = 1) { prefetcher.prefetch(stored.captured) }
    }

    @Test
    fun `ohne TMDB-Key wird trotzdem vorgeladen, aber nichts bei TMDB angefragt`() = runTest {
        every { tmdb.isConfigured } returns false
        coEvery { scraper.fetch(any()) } returns listOf(movie("10", "a", "Dune: Part Two"))

        repo.refresh(force = true)

        coVerify(exactly = 0) { tmdb.searchMovie(any(), any()) }
        verify(exactly = 1) { prefetcher.prefetch(any()) }
    }

    @Test
    fun `gedrosselter Refresh laedt nichts vor`() = runTest {
        every { settings.lastMoviesRefreshEpoch } returns flowOf(System.currentTimeMillis())

        repo.refresh(force = false)

        coVerify(exactly = 0) { scraper.fetch(any()) }
        verify(exactly = 0) { prefetcher.prefetch(any()) }
    }

    @Test
    fun `fehlgeschlagener Scrape laedt nichts vor`() = runTest {
        coEvery { scraper.fetch(any()) } throws RuntimeException("unifilm.de down")

        val result = repo.refresh(force = true)

        assertTrue(result is AppResult.Failure)
        verify(exactly = 0) { prefetcher.prefetch(any()) }
    }
}
