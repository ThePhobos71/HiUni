package de.transio.hiuni.feature.mensa

import app.cash.turbine.test
import de.transio.hiuni.feature.mensa.data.FavoriteMealStore
import de.transio.hiuni.feature.mensa.data.FavoriteMealWatcher
import de.transio.hiuni.feature.mensa.data.MealEntity
import de.transio.hiuni.feature.mensa.data.MensaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** Liste "Lieblingsgerichte" in den Mensa-Einstellungen. */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteMealsViewModelTest {

    private val keywords = MutableStateFlow(listOf("Currywurst", "Lasagne"))
    private val store = mockk<FavoriteMealStore>(relaxed = true).also {
        every { it.observeKeywords() } returns keywords
    }
    private val watcher = mockk<FavoriteMealWatcher>(relaxed = true)
    private val repository = mockk<MensaRepository>(relaxed = true)
    private val thu = LocalDate.of(2026, 11, 19)

    private fun meal(name: String, date: LocalDate) = MealEntity(
        sourceId = "$name-$date",
        locationId = 150,
        date = date,
        category = "Mittag",
        name = name,
        description = null,
        priceStudentCents = null,
        priceEmployeeCents = null,
        priceGuestCents = null,
        tags = ""
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.observeSearchWindow(any()) } returns
            flowOf(listOf(meal("Currywurst mit Pommes", thu)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = FavoriteMealsViewModel(store, watcher, repository)

    @Test
    fun `jedes Stichwort zeigt seinen naechsten Treffer`() = runTest {
        newVm().state.test {
            val rows = awaitItem().rows
            assertEquals(listOf("Currywurst", "Lasagne"), rows.map { it.keyword })
            assertEquals(thu, rows[0].nextHit?.date)
            assertNull(rows[1].nextHit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hinzufuegen prueft still, ohne Mitteilung`() = runTest {
        coEvery { store.addKeyword("Pizza") } returns true
        val vm = newVm()

        vm.add("Pizza")

        coVerify(exactly = 1) { watcher.check(notify = false) }
        vm.state.test {
            assertFalse(awaitItem().rejected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `abgelehntes Stichwort setzt den Hinweis`() = runTest {
        coEvery { store.addKeyword(any()) } returns false
        val vm = newVm()

        vm.add("ei")

        vm.state.test {
            assertTrue(awaitItem().rejected)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { watcher.check(any()) }
    }

    @Test
    fun `entfernen reicht an den Store durch`() = runTest {
        newVm().remove("Lasagne")

        coVerify { store.removeKeyword("Lasagne") }
    }
}
