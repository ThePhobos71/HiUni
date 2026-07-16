package de.transio.hiuni.feature.movies

import app.cash.turbine.test
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.feature.movies.data.MovieEntity
import de.transio.hiuni.feature.movies.data.MoviesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MoviesViewModelTest {

    private val repo = mockk<MoviesRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repo.observeUpcoming() } returns flowOf(emptyList())
        coEvery { repo.refresh(any(), any()) } returns AppResult.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load with empty cache and success ends not loading, no error`() = runTest {
        val vm = MoviesViewModel(repo)

        vm.state.test {
            val state = awaitItem()
            assertEquals(false, state.isLoading)
            assertEquals(null, state.errorMessage)
            assertEquals(false, state.hasContent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial load failure with empty cache sets errorMessage and stops loading`() = runTest {
        coEvery { repo.refresh(any(), any()) } returns
            AppResult.Failure(RuntimeException("kein Netz"))
        val vm = MoviesViewModel(repo)

        vm.state.test {
            val state = awaitItem()
            assertEquals("kein Netz", state.errorMessage)
            assertEquals(false, state.isLoading)
            assertEquals(false, state.hasContent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failure with cached content still reports hasContent for stale display`() = runTest {
        val cached = MovieEntity(
            filmId = "f1",
            sessionId = "s1",
            title = "Testfilm",
            date = LocalDate.of(2026, 7, 20)
        )
        every { repo.observeUpcoming() } returns flowOf(listOf(cached))
        coEvery { repo.refresh(any(), any()) } returns
            AppResult.Failure(RuntimeException("stale"))
        val vm = MoviesViewModel(repo)

        vm.state.test {
            val state = awaitItem()
            assertEquals(true, state.hasContent)
            assertEquals("stale", state.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
