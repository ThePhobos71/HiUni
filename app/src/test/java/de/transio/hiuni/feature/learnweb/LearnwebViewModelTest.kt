package de.transio.hiuni.feature.learnweb

import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.feature.learnweb.data.LearnwebRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Pop-in: vor der ersten Room-Emission darf weder „Mit Uni-Login anmelden" noch
 * „0 KURSE" aufblitzen, wenn der User längst angemeldet ist.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LearnwebViewModelTest {

    private val repo = mockk<LearnwebRepository>(relaxed = true)
    private val cas = mockk<CasSession>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { cas.state } returns MutableStateFlow(CasState.Authenticated("kjell", Instant.EPOCH))
        every { repo.observeCourses() } returns flowOf(emptyList())
        every { repo.observeUpcomingAssignments() } returns flowOf(emptyList())
        coEvery { repo.refresh(any()) } returns AppResult.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `vor der ersten Room-Emission gilt angemeldet, aber nicht geladen`() = runTest {
        every { repo.observeCourses() } returns MutableSharedFlow()
        val vm = LearnwebViewModel(repo, cas)

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertFalse(vm.state.value.isContentLoaded)
        assertTrue("Login-Hinweis würde aufblitzen", vm.state.value.isAuthenticated)
        job.cancel()
    }

    @Test
    fun `nach der ersten Room-Emission ist die Kursliste geladen`() = runTest {
        val vm = LearnwebViewModel(repo, cas)

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertTrue(vm.state.value.isContentLoaded)
        job.cancel()
    }
}
