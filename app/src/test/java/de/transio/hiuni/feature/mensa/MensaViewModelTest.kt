package de.transio.hiuni.feature.mensa

import de.transio.hiuni.core.search.PendingSearchSelection
import de.transio.hiuni.core.search.SearchFixtures
import de.transio.hiuni.core.search.SearchSelectionHolder
import org.junit.Assert.assertNull
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

    private val selectionHolder = SearchSelectionHolder()

    private fun newVm() = MensaViewModel(mensaRepo, connectivity, settings, selectionHolder)

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
        val vm = newVm()
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
        val vm = newVm()
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
        val vm = newVm()

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
        val vm = newVm()

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
        val vm = newVm()

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

    // ── Deep-Link aus der Spotlight-Suche ──────────────────────────────────

    @Test
    fun `vorgemerktes Gericht springt aufs Datum und oeffnet das Detail-Sheet`() = runTest {
        val meal = SearchFixtures.meal(date = LocalDate.of(2026, 10, 30), category = "Abend · Hauptgericht")
        selectionHolder.stage(PendingSearchSelection.MensaMeal(meal))

        val vm = newVm()

        vm.state.test {
            val state = awaitItem()
            assertEquals(meal.date, state.selectedDate)
            assertEquals(Mealtime.ABEND, state.selectedMealtime)
            // Kategorie ohne Mealtime-Präfix, wie in der Tagesliste.
            assertEquals(meal.copy(category = "Hauptgericht"), state.mealDetail)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(selectionHolder.pending.value)
    }

    @Test
    fun `bereits lebendes ViewModel holt die Vormerkung ab`() = runTest {
        val vm = newVm()
        val meal = SearchFixtures.meal(date = LocalDate.of(2026, 11, 2))

        selectionHolder.stage(PendingSearchSelection.MensaMeal(meal))

        vm.state.test {
            val state = awaitItem()
            assertEquals(meal.date, state.selectedDate)
            assertEquals(Mealtime.MITTAG, state.selectedMealtime)
            assertEquals(meal, state.mealDetail)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `geschlossenes Detail-Sheet oeffnet sich nach Neuaufbau nicht erneut`() = runTest {
        selectionHolder.stage(PendingSearchSelection.MensaMeal(SearchFixtures.meal()))
        val vm = newVm()
        vm.closeMealDetail()

        // Neuer Screen-Aufbau (z. B. Tab erneut betreten) darf die Auswahl nicht wiederholen.
        val recreated = newVm()

        vm.state.test {
            assertNull(awaitItem().mealDetail)
            cancelAndIgnoreRemainingEvents()
        }
        recreated.state.test {
            assertNull(awaitItem().mealDetail)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fremde Vormerkung wird von der Mensa nicht angefasst`() = runTest {
        val foreign = PendingSearchSelection.Exam(SearchFixtures.exam())
        selectionHolder.stage(foreign)

        val vm = newVm()

        vm.state.test {
            assertNull(awaitItem().mealDetail)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(foreign, selectionHolder.pending.value)
    }
}
