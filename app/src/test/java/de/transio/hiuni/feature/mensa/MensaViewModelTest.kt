package de.transio.hiuni.feature.mensa

import app.cash.turbine.test
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

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { mensaRepo.observeForDate(any()) } returns flowOf(emptyList())
        every { mensaRepo.observeAvailableDates(any()) } returns flowOf(emptyList())
        every { mensaRepo.observeAnnouncements(any()) } returns flowOf(emptyList())
        every { mensaRepo.observeSearchWindow(any()) } returns flowOf(emptyList())
        coEvery { mensaRepo.refresh(any(), any()) } returns
            de.transio.hiuni.core.common.AppResult.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectDate updates state`() = runTest {
        val vm = MensaViewModel(mensaRepo)
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
        val vm = MensaViewModel(mensaRepo)
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
    fun `toggleDietFilter toggles between value and null`() = runTest {
        val vm = MensaViewModel(mensaRepo)

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
