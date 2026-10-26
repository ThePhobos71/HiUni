package de.transio.hiuni.feature.movies

import androidx.lifecycle.SavedStateHandle
import de.transio.hiuni.feature.movies.data.MovieEntity
import de.transio.hiuni.feature.movies.data.MoviesRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModelTest {

    private val repo = mockk<MoviesRepository>()
    private val handle = SavedStateHandle(mapOf("filmId" to "10", "sessionId" to "a"))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `TMDB-Details kommen aus Room, ohne eigene Netzwerk-Abfrage`() {
        coEvery { repo.findById("10", "a") } returns MovieEntity(
            filmId = "10",
            sessionId = "a",
            title = "Dune: Part Two",
            posterUrl = "https://image.tmdb.org/t/p/w780/p.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w1280/b.jpg",
            tmdbRating = 8.2,
            tmdbVoteCount = 5400,
            director = "Denis Villeneuve",
            castNames = listOf("Timothée Chalamet", "Zendaya")
        )

        val state = MovieDetailViewModel(handle, repo).state.value

        assertFalse(state.isLoading)
        assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg", state.backdropUrl)
        assertEquals(8.2, state.rating!!, 0.0001)
        assertEquals(5400, state.voteCount)
        assertEquals("Denis Villeneuve", state.crewDirector)
        assertEquals(listOf("Timothée Chalamet", "Zendaya"), state.cast)
    }

    @Test
    fun `Film ohne TMDB-Treffer zeigt leere Details`() {
        coEvery { repo.findById("10", "a") } returns MovieEntity(
            filmId = "10",
            sessionId = "a",
            title = "Kurzfilmnacht"
        )

        val state = MovieDetailViewModel(handle, repo).state.value

        assertNull(state.backdropUrl)
        assertNull(state.rating)
        assertEquals(emptyList<String>(), state.cast)
    }

    @Test
    fun `unbekannter Film beendet das Laden ohne Film`() {
        coEvery { repo.findById("10", "a") } returns null

        val state = MovieDetailViewModel(handle, repo).state.value

        assertFalse(state.isLoading)
        assertNull(state.movie)
    }
}
