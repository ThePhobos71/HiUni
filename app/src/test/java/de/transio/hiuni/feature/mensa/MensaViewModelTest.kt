package de.transio.hiuni.feature.mensa

import de.transio.hiuni.core.search.PendingSearchSelection
import de.transio.hiuni.core.search.SearchFixtures
import de.transio.hiuni.core.search.SearchSelectionHolder
import org.junit.Assert.assertNull
import app.cash.turbine.test
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.feature.mensa.data.MensaRepository
import de.transio.hiuni.feature.mensa.data.MealEntity
import de.transio.hiuni.feature.mensa.data.OpeningHourBlock
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

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
        every { mensaRepo.observeOpeningHours() } returns flowOf(emptyList())
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

    // ── Pop-in: geladen vs. noch nicht geladen ────────────────────────────

    private fun meal(date: LocalDate = LocalDate.now(), name: String = "Linsencurry") = MealEntity(
        sourceId = "m-$name",
        locationId = 150,
        date = date,
        category = "Mittag · Essen 1",
        name = name,
        description = null,
        priceStudentCents = 350,
        priceEmployeeCents = null,
        priceGuestCents = null,
        tags = "vegan"
    )

    @Test
    fun `solange Room noch nicht geliefert hat, gilt der State als nicht geladen`() = runTest {
        // Room-Query hängt noch: der Screen darf dann KEINEN Empty-State zeigen.
        every { mensaRepo.observeForDate(any()) } returns MutableSharedFlow()
        val vm = newVm()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        val state = vm.state.value
        assertFalse(state.isContentLoaded)
        assertFalse(state.showEmptyState)
        job.cancel()
    }

    @Test
    fun `gecachte Gerichte erscheinen sofort ohne Skeleton, auch waehrend der Refresh laeuft`() = runTest {
        every { mensaRepo.observeForDate(any()) } returns flowOf(listOf(meal()))
        every { mensaRepo.observeAvailableDates(any()) } returns flowOf(listOf(LocalDate.now()))
        val gate = CompletableDeferred<de.transio.hiuni.core.common.AppResult<Unit>>()
        coEvery { mensaRepo.refresh(any(), any()) } coAnswers { gate.await() }
        val vm = newVm()
        // Testgericht ist ein Mittagsgericht; autoSelect() hängt sonst an der Uhrzeit.
        vm.selectMealtime(Mealtime.MITTAG)

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        val state = vm.state.value
        assertTrue(state.isContentLoaded)
        assertTrue(state.hasContent)
        assertFalse("Skeleton trotz Cache", state.isLoading)
        assertFalse(state.showSkeleton)
        gate.complete(de.transio.hiuni.core.common.AppResult.Success(Unit))
        job.cancel()
    }

    @Test
    fun `Cache fuer andere Tage, aber nicht fuer heute zeigt keinen Skeleton`() = runTest {
        // z.B. Abend-Slot ohne Gerichte: der Auto-Refresh darf keinen Skeleton-Blitz
        // vor dem Empty-State erzeugen, solange irgendein Speiseplan im Cache liegt.
        every { mensaRepo.observeAvailableDates(any()) } returns
            flowOf(listOf(LocalDate.now().plusDays(1)))
        val gate = CompletableDeferred<de.transio.hiuni.core.common.AppResult<Unit>>()
        coEvery { mensaRepo.refresh(any(), any()) } coAnswers { gate.await() }
        val vm = newVm()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertFalse(vm.state.value.showSkeleton)
        assertTrue(vm.state.value.showEmptyState)
        gate.complete(de.transio.hiuni.core.common.AppResult.Success(Unit))
        job.cancel()
    }

    @Test
    fun `erster Load ohne jeden Cache zeigt Skeleton`() = runTest {
        val gate = CompletableDeferred<de.transio.hiuni.core.common.AppResult<Unit>>()
        coEvery { mensaRepo.refresh(any(), any()) } coAnswers { gate.await() }
        val vm = newVm()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertTrue(vm.state.value.isLoading)
        assertTrue(vm.state.value.showSkeleton)
        assertFalse(vm.state.value.showEmptyState)
        gate.complete(de.transio.hiuni.core.common.AppResult.Success(Unit))
        job.cancel()
    }

    @Test
    fun `leer nach abgeschlossenem Load zeigt Empty-State statt Skeleton`() = runTest {
        val vm = newVm()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        val state = vm.state.value
        assertTrue(state.isContentLoaded)
        assertFalse(state.showSkeleton)
        assertTrue(state.showEmptyState)
        job.cancel()
    }

    @Test
    fun `Such-Korpus wird erst beim Oeffnen der Suche abgefragt`() = runTest {
        val vm = newVm()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        verify(exactly = 0) { mensaRepo.observeSearchWindow(any()) }

        vm.openSearch()
        verify(exactly = 1) { mensaRepo.observeSearchWindow(any()) }
        job.cancel()
    }

    @Test
    fun `gecachte Oeffnungszeiten landen im ersten geladenen State`() = runTest {
        val blocks = listOf(
            OpeningHourBlock("noon", 1, 5, LocalTime.of(11, 0), LocalTime.of(14, 15))
        )
        every { mensaRepo.observeOpeningHours() } returns flowOf(blocks)
        val vm = newVm()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertEquals(blocks, vm.state.value.openingHours)
        job.cancel()
    }
}
