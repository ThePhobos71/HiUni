package de.transio.hiuni.feature.sport

import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.network.ConnectivityObserver
import de.transio.hiuni.feature.sport.data.SportRepository
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

/** Pop-in: der Sport-Screen darf vor der ersten Room-Emission keinen Empty-State zeigen. */
@OptIn(ExperimentalCoroutinesApi::class)
class SportViewModelTest {

    private val repo = mockk<SportRepository>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val connectivity = mockk<ConnectivityObserver>(relaxed = true).also {
        every { it.isOnline } returns MutableStateFlow(true)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repo.observeUpcoming() } returns flowOf(emptyList())
        every { repo.observeDistinctTitles() } returns flowOf(emptyList())
        every { settings.lastSportRefreshEpoch } returns flowOf(0L)
        coEvery { repo.refresh(any()) } returns AppResult.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `solange Room noch nicht geliefert hat, gilt der Plan als nicht geladen`() = runTest {
        every { repo.observeUpcoming() } returns MutableSharedFlow()
        val vm = SportViewModel(repo, connectivity, settings)

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertFalse(vm.state.value.isContentLoaded)
        job.cancel()
    }

    @Test
    fun `nach der ersten Room-Emission ist der Plan geladen`() = runTest {
        val vm = SportViewModel(repo, connectivity, settings)

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertTrue(vm.state.value.isContentLoaded)
        job.cancel()
    }
}
