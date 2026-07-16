package de.transio.hiuni.feature.mensa

import app.cash.turbine.test
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.feature.mensa.data.MensaRepository
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
class MensaViewModelTest {

    private val mensaRepo = mockk<MensaRepository>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val connectivity = mockk<de.transio.hiuni.core.network.ConnectivityObserver>(relaxed = true).also {
        every { it.isOnline } returns kotlinx.coroutines.flow.MutableStateFlow(true)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { mensaRepo.observeForDate(any()) } returns flowOf(emptyList())
        every { mensaRepo.observeAvailableDates(any()) } returns flowOf(emptyList())
        every { mensaRepo.observeAnnouncements(any()) } returns flowOf(emptyList())
        every { mensaRepo.observeSearchWindow(any()) } returns flowOf(emptyList())
        every { settings.mensaLocationId } returns flowOf(150)
        every { settings.lastMensaRefreshEpoch } returns flowOf(0L)
        coEvery { mensaRepo.refresh(any(), any()) } returns
            de.transio.hiuni.core.common.AppResult.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectDate updates state`() = runTest {
        val vm = MensaViewModel(mensaRepo, connectivity, settings)
        val target = LocalDate.of(2026, 6, 1)

        vm.selectDate(target)

        vm.state.test {
            // initial emission shows new date because Unconfined dispatcher applies update synchronously
            val state = awaitItem()
            assertEquals(target, state.selectedDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectMealtime updates state and resets activeDietFilter`() = runTest {
        val vm = MensaViewModel(mensaRepo, connectivity, settings)
        vm.toggleDietFilter(DietFilter.VEGAN)
        vm.selectMealtime(Mealtime.ABEND)

        vm.state.test {
            val state = awaitItem()
            assertEquals(Mealtime.ABEND, state.selectedMealtime)
            assertEquals(null, state.activeDietFilter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial load with empty cache and success ends not loading, no error`() = runTest {
        val vm = MensaViewModel(mensaRepo, connectivity, settings)

        vm.state.test {
            val state = awaitItem()
            // Unconfined-Dispatcher: refresh() ist beim Sampling schon durch.
            assertEquals(false, state.isLoading)
            assertEquals(null, state.errorMessage)
            assertEquals(false, state.hasContent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial load failure with empty cache sets errorMessage and stops loading`() = runTest {
        coEvery { mensaRepo.refresh(any(), any()) } returns
            de.transio.hiuni.core.common.AppResult.Failure(RuntimeException("offline"))
        val vm = MensaViewModel(mensaRepo, connectivity, settings)

        vm.state.test {
            val state = awaitItem()
            assertEquals("offline", state.errorMessage)
            assertEquals(false, state.isLoading)
            assertEquals(false, state.hasContent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleDietFilter toggles between value and null`() = runTest {
        val vm = MensaViewModel(mensaRepo, connectivity, settings)

        vm.toggleDietFilter(DietFilter.VEGETARISCH)
        vm.state.test {
            assertEquals(DietFilter.VEGETARISCH, awaitItem().activeDietFilter)
            cancelAndIgnoreRemainingEvents()
        }

        vm.toggleDietFilter(DietFilter.VEGETARISCH)
        vm.state.test {
            assertEquals(null, awaitItem().activeDietFilter)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
