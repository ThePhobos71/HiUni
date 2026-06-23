package de.transio.hiuni.feature.bib

import app.cash.turbine.test
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.auth.UserProfile
import de.transio.hiuni.feature.bib.data.BibRepository
import de.transio.hiuni.feature.bib.data.BibUiData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BibViewModelTest {

    private val repository = mockk<BibRepository>(relaxed = true)
    private val casSession = mockk<CasSession>(relaxed = true)
    private val repoStateFlow = MutableStateFlow(BibUiData())
    private val casStateFlow = MutableStateFlow<CasState>(
        CasState.Authenticated(username = "tester", obtainedAt = Instant.now())
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.state } returns repoStateFlow
        every { casSession.state } returns casStateFlow
        every { casSession.profile } returns MutableStateFlow(UserProfile.EMPTY)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = BibViewModel(repository, casSession)

    private fun BibViewModel.open(roomId: Int = 101) {
        openBookingScreen(roomId = roomId, date = LocalDate.of(2026, 5, 27))
    }

    /** Hilfs-Funktion: führt actions aus und liefert das letzte BibUiState. */
    private suspend fun BibViewModel.stateAfter(actions: BibViewModel.() -> Unit): BibUiState {
        lateinit var result: BibUiState
        state.test {
            awaitItem() // initial
            actions()
            result = expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }
        return result
    }

    @Test
    fun `toggleSlot fuegt ersten Slot in leere Auswahl ein`() = runTest {
        val vm = newVm()
        val state = vm.stateAfter {
            open()
            toggleSlot(4) { false }
        }
        assertEquals(listOf(4), state.booking?.selected)
    }

    @Test
    fun `toggleSlot erweitert Auswahl wenn idx an Maximum angrenzt`() = runTest {
        val vm = newVm()
        val state = vm.stateAfter {
            open()
            toggleSlot(4) { false }
            toggleSlot(5) { false }
            toggleSlot(6) { false }
        }
        assertEquals(listOf(4, 5, 6), state.booking?.selected)
    }

    @Test
    fun `toggleSlot erweitert Auswahl auch nach links bei angrenzendem idx unter min`() = runTest {
        val vm = newVm()
        val state = vm.stateAfter {
            open()
            toggleSlot(5) { false }
            toggleSlot(4) { false }
        }
        assertEquals(listOf(4, 5), state.booking?.selected?.sorted())
    }

    @Test
    fun `toggleSlot setzt Auswahl zurueck bei nicht-angrenzendem Tap`() = runTest {
        val vm = newVm()
        val state = vm.stateAfter {
            open()
            toggleSlot(4) { false }
            toggleSlot(5) { false }
            toggleSlot(10) { false } // 10 ist nicht angrenzend zu 4..5
        }
        assertEquals(listOf(10), state.booking?.selected)
    }

    @Test
    fun `toggleSlot kuerzt vom Ende bei Klick auf bereits selektierten Slot`() = runTest {
        val vm = newVm()
        val state = vm.stateAfter {
            open()
            listOf(4, 5, 6, 7).forEach { toggleSlot(it) { false } }
            toggleSlot(6) { false } // mittlerer Slot → Range schrumpft auf [4, 5]
        }
        assertEquals(listOf(4, 5), state.booking?.selected)
    }

    @Test
    fun `toggleSlot leert die Auswahl beim Klick auf den ersten Slot`() = runTest {
        val vm = newVm()
        val state = vm.stateAfter {
            open()
            toggleSlot(4) { false }
            toggleSlot(5) { false }
            toggleSlot(4) { false }
        }
        assertTrue(state.booking?.selected.orEmpty().isEmpty())
    }

    @Test
    fun `toggleSlot ignoriert geblockten Slot komplett`() = runTest {
        val vm = newVm()
        val state = vm.stateAfter {
            open()
            toggleSlot(4) { false }
            toggleSlot(5) { it == 5 } // Slot 5 ist BOOKED/CLOSED → no-op
        }
        assertEquals(listOf(4), state.booking?.selected)
    }

    @Test
    fun `openBookingScreen ohne CasSession-Authenticated zeigt Snackbar statt Dialog`() = runTest {
        casStateFlow.value = CasState.NeedsLogin
        val vm = newVm()
        vm.state.test {
            awaitItem() // initial
            vm.openBookingScreen(roomId = 101, date = LocalDate.of(2026, 5, 27))
            val s = awaitItem()
            assertEquals(
                "Bitte zuerst mit Uni-Login anmelden, um Räume zu buchen.",
                s.snackbar
            )
            assertNull("Booking-Dialog darf bei NeedsLogin nicht öffnen", s.booking)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `closeBookingScreen setzt booking auf null`() = runTest {
        val vm = newVm()
        val state = vm.stateAfter {
            open()
            closeBookingScreen()
        }
        assertNull(state.booking)
    }

    @Test
    fun `selectDate ueberschreibt explicit gewaehlte Datum`() = runTest {
        val vm = newVm()
        val target = LocalDate.of(2026, 6, 1)
        val state = vm.stateAfter { selectDate(target) }
        assertEquals(target, state.selectedDate)
    }
}
