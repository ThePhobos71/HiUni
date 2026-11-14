package de.transio.hiuni.feature.rooms

import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.feature.rooms.data.BusySpan
import de.transio.hiuni.feature.rooms.data.Campus
import de.transio.hiuni.feature.rooms.data.CampusSnapshot
import de.transio.hiuni.feature.rooms.data.FreeRoomsRepository
import de.transio.hiuni.feature.rooms.data.FreeRoomsStore
import de.transio.hiuni.feature.rooms.data.LoadProgress
import de.transio.hiuni.feature.rooms.data.LsfRoom
import de.transio.hiuni.feature.rooms.data.RoomDay
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class FreeRoomsViewModelTest {

    private val repository = mockk<FreeRoomsRepository>(relaxed = true)
    private val store = mockk<FreeRoomsStore>(relaxed = true)
    private val campusFlow = MutableStateFlow<Campus?>(null)

    /** Donnerstag, 12.11.2026, 13:42 in Berlin. */
    private val clock = Clock.fixed(Instant.parse("2026-11-12T12:42:00Z"), ZoneId.of("Europe/Berlin"))

    private fun room(code: String, rgid: Int) = LsfRoom(rgid, code, "Seminarraum", Campus.SAMELSON)

    private val snapshot = CampusSnapshot(
        campus = Campus.SAMELSON,
        date = "2026-11-12",
        fetchedAt = 0,
        rooms = listOf(
            RoomDay(room("SC.A.0.09", 2), listOf(BusySpan(960, 1080))),
            RoomDay(room("SC.B.0.37", 245), listOf(BusySpan(855, 900))),
            RoomDay(room("SC.A.1.02", 21), emptyList()),
            RoomDay(room("SC.B.1.03", 239), listOf(BusySpan(720, 840)))
        ),
        missing = listOf(room("SC.C.2.13", 29))
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { store.selectedCampus } returns campusFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = FreeRoomsViewModel(repository, store, clock)

    @Test
    fun `ohne gemerkten Campus wird nichts geladen`() {
        val vm = newVm()
        assertNull(vm.state.value.campus)
        assertEquals(LocalDate.of(2026, 11, 12), vm.state.value.today)
        coVerify(exactly = 0) { repository.load(any(), any()) }
    }

    @Test
    fun `gemerkter Campus zeigt den heutigen Stand ohne Abruf, sortiert wie die Skizze`() {
        campusFlow.value = Campus.SAMELSON
        coEvery { repository.cached(Campus.SAMELSON) } returns snapshot

        val s = newVm().state.value

        assertEquals(Campus.SAMELSON, s.campus)
        assertEquals(13 * 60 + 42, s.atMinute)
        assertEquals(listOf("SC.A.1.02", "SC.A.0.09", "SC.B.0.37"), s.result.free.map { it.room.code })
        assertEquals(listOf("SC.B.1.03"), s.result.busy.map { it.room.code })
        assertEquals(listOf("SC.C.2.13"), s.missing.map { it.code })
        coVerify(exactly = 0) { repository.load(any(), any()) }
    }

    @Test
    fun `Campus waehlen ohne Stand laedt und merkt sich die Wahl`() {
        coEvery { repository.cached(Campus.SAMELSON) } returns null
        coEvery { repository.load(Campus.SAMELSON, any()) } coAnswers {
            secondArg<(LoadProgress) -> Unit>().invoke(LoadProgress(3, 9))
            AppResult.Success(snapshot)
        }
        val vm = newVm()

        vm.selectCampus(Campus.SAMELSON)

        coVerify { store.setCampus(Campus.SAMELSON) }
        coVerify(exactly = 1) { repository.load(Campus.SAMELSON, any()) }
        assertFalse(vm.state.value.isLoading)
        assertEquals(snapshot, vm.state.value.snapshot)
    }

    @Test
    fun `andere Uhrzeit rechnet neu, vergangene Stunden sind nicht waehlbar`() {
        campusFlow.value = Campus.SAMELSON
        coEvery { repository.cached(Campus.SAMELSON) } returns snapshot
        val vm = newVm()

        assertEquals((14..20).map { it * 60 }, vm.state.value.timeOptions)
        vm.selectTime(16 * 60)
        val s = vm.state.value
        assertEquals(16 * 60, s.atMinute)
        assertEquals(listOf("SC.A.1.02", "SC.B.0.37", "SC.B.1.03"), s.result.free.map { it.room.code })
        assertEquals(listOf("SC.A.0.09"), s.result.busy.map { it.room.code })

        vm.selectTime(null)
        assertEquals(13 * 60 + 42, vm.state.value.atMinute)

        vm.selectTime(9 * 60)
        assertNull(vm.state.value.chosenMinute)
    }

    @Test
    fun `Fehler beim Laden zeigt eine Meldung und beendet das Laden`() {
        coEvery { repository.cached(any()) } returns null
        coEvery { repository.load(any(), any()) } returns AppResult.Failure(IOException("offline"))
        val vm = newVm()

        vm.selectCampus(Campus.HAUPTCAMPUS)

        val s = vm.state.value
        assertFalse(s.isLoading)
        assertEquals("LSF nicht erreichbar. Prüfe deine Verbindung und versuch es erneut.", s.error)
        assertNull(s.snapshot)
    }

    @Test
    fun `Neuladen und fehlende nachholen gehen an das Repository`() {
        campusFlow.value = Campus.SAMELSON
        coEvery { repository.cached(Campus.SAMELSON) } returns snapshot
        coEvery { repository.load(any(), any()) } returns AppResult.Success(snapshot)
        coEvery { repository.retryMissing(any(), any()) } returns AppResult.Success(snapshot.copy(missing = emptyList()))
        val vm = newVm()

        vm.retryMissing()
        coVerify(exactly = 1) { repository.retryMissing(Campus.SAMELSON, any()) }
        assertEquals(emptyList<LsfRoom>(), vm.state.value.missing)

        vm.reload()
        coVerify(exactly = 1) { repository.load(Campus.SAMELSON, any()) }
    }

    @Test
    fun `Uhrzeit-Optionen`() {
        assertEquals((14..20).map { it * 60 }, FreeRoomsViewModel.timeOptionsAfter(13 * 60 + 42))
        assertEquals(listOf(20 * 60), FreeRoomsViewModel.timeOptionsAfter(19 * 60))
        assertEquals(emptyList<Int>(), FreeRoomsViewModel.timeOptionsAfter(20 * 60 + 5))
    }
}
