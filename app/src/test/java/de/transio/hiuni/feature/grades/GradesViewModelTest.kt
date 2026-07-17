package de.transio.hiuni.feature.grades

import app.cash.turbine.test
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.AuthRequiredException
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.network.ConnectivityObserver
import de.transio.hiuni.feature.grades.data.GradeEntity
import de.transio.hiuni.feature.grades.data.GradeStatus
import de.transio.hiuni.feature.grades.data.GradesRepository
import de.transio.hiuni.feature.grades.data.GradesSummaryEntity
import de.transio.hiuni.feature.grades.data.GradesSyncResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class GradesViewModelTest {

    // Fake-Repo mit steuerbaren Flows + programmierbarem refresh-Ergebnis.
    private class FakeGradesRepository : GradesRepository {
        val gradesFlow = MutableStateFlow<List<GradeEntity>>(emptyList())
        val summaryFlow = MutableStateFlow<GradesSummaryEntity?>(null)
        var refreshResult: AppResult<GradesSyncResult> =
            AppResult.Success(GradesSyncResult(0, 0, 0, 0))
        var refreshCalls = 0

        override fun observeAll() = gradesFlow
        override fun observeSummary() = summaryFlow
        override suspend fun refresh(force: Boolean): AppResult<GradesSyncResult> {
            refreshCalls += 1
            return refreshResult
        }
    }

    private val repository = FakeGradesRepository()
    private val casSession = mockk<CasSession>(relaxed = true)
    private val casStateFlow = MutableStateFlow<CasState>(
        CasState.Authenticated(username = "abc", obtainedAt = Instant.now())
    )
    private val connectivity = mockk<ConnectivityObserver>(relaxed = true)
    private val onlineFlow = MutableStateFlow(true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val lastRefreshFlow = MutableStateFlow(0L)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { casSession.state } returns casStateFlow
        every { connectivity.isOnline } returns onlineFlow
        every { settings.lastGradesRefreshEpoch } returns lastRefreshFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = GradesViewModel(repository, casSession, connectivity, settings)

    private fun grade(
        rowId: Long,
        titel: String,
        semester: String,
        note: Double?,
        status: GradeStatus,
        lp: Int = 5,
        versuch: Int = 1
    ) = GradeEntity(
        rowId = rowId,
        mergeKey = "l:$rowId",
        labnr = rowId,
        pruefungsNr = rowId.toString(),
        titel = titel,
        veranstaltungsNr = null,
        kontoNr = null,
        kontoName = null,
        semester = semester,
        note = note,
        status = status,
        bonusLp = lp,
        vermerk = "",
        versuch = versuch,
        pruefungsDatum = null,
        fetchedAt = 0L
    )

    @Test
    fun `initialer State ist isLoading true`() {
        val vm = newVm()
        assertTrue(vm.state.value.isLoading)
    }

    @Test
    fun `authentifiziert und leer nach Sync ergibt leeren nicht-ladenden State`() = runTest {
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertFalse(s.isLoading)
            assertTrue(s.semesters.isEmpty())
            assertNull(s.gpa)
            assertFalse(s.isAuthRequired)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun `Summen landen als GPA und totalLp im State`() = runTest {
        repository.summaryFlow.value = GradesSummaryEntity(
            gpa = 2.3, weightedLp = 100, totalLp = 121, fetchedAt = 0L
        )
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals(2.3, s.gpa)
            assertEquals(121, s.totalLp)
            // 121/180 ≈ 0.672
            assertEquals(0.672f, s.ectsProgress, 0.01f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Leistungen werden nach Semester gruppiert neuestes zuerst`() = runTest {
        repository.gradesFlow.value = listOf(
            grade(1, "Alt", "WiSe 24/25", 2.0, GradeStatus.PASSED),
            grade(2, "Neu", "SoSe 26", 1.7, GradeStatus.PASSED),
            grade(3, "AuchNeu", "SoSe 26", 3.0, GradeStatus.PASSED)
        )
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals(2, s.semesters.size)
            // Neuestes Semester (SoSe 26) zuerst.
            assertEquals("SoSe 26", s.semesters[0].semester)
            assertEquals("WiSe 24/25", s.semesters[1].semester)
            assertEquals(2, s.semesters[0].grades.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SemesterSection passedLp summiert nur bestandene LP`() {
        val section = GradesUiState.groupBySemester(
            listOf(
                grade(1, "A", "SoSe 26", 2.0, GradeStatus.PASSED, lp = 6),
                grade(2, "B", "SoSe 26", null, GradeStatus.REGISTERED, lp = 5),
                grade(3, "C", "SoSe 26", 5.0, GradeStatus.FAILED, lp = 5)
            )
        ).first()
        assertEquals(6, section.passedLp)
    }

    @Test
    fun `ohne CAS-Session wird kein Refresh gefeuert und Auth-Hinweis gesetzt`() = runTest {
        casStateFlow.value = CasState.NeedsLogin
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertTrue(s.isAuthRequired)
            assertFalse(s.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun `AuthRequiredException aus Refresh setzt Auth-Hinweis statt Snackbar-Fehler`() = runTest {
        repository.refreshResult = AppResult.Failure(AuthRequiredException("session weg"))
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertTrue(s.isAuthRequired)
            assertNull(s.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `generischer Refresh-Fehler landet in errorMessage`() = runTest {
        repository.refreshResult = AppResult.Failure(RuntimeException("Netz weg"))
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals("Netz weg", s.errorMessage)
            assertFalse(s.isAuthRequired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bei vorhandenem Cache bleibt Auth-Hinweis aus`() = runTest {
        // Fehlende Session, aber Noten im Cache → lieber Stale-Daten zeigen.
        casStateFlow.value = CasState.NeedsReauth
        repository.gradesFlow.value = listOf(
            grade(1, "Cache", "SoSe 26", 2.0, GradeStatus.PASSED)
        )
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertFalse(s.isAuthRequired)
            assertTrue(s.hasContent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Session weg mit Cache zeigt Reauth-Banner statt Auth-Vollbild`() = runTest {
        // Abgelaufene Session UND Cache vorhanden → dezenter Reauth-Banner über
        // den Stale-Daten (showReauthBanner), aber KEIN Auth-Vollbild.
        casStateFlow.value = CasState.NeedsReauth
        repository.gradesFlow.value = listOf(
            grade(1, "Cache", "SoSe 26", 2.0, GradeStatus.PASSED)
        )
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertTrue("Reauth-Banner erwartet", s.showReauthBanner)
            assertFalse("Kein Auth-Vollbild bei Cache", s.isAuthRequired)
            assertTrue(s.hasContent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Session weg ohne Cache zeigt keinen Reauth-Banner sondern Auth-Vollbild`() = runTest {
        casStateFlow.value = CasState.NeedsReauth
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertFalse("Kein Banner ohne Cache", s.showReauthBanner)
            assertTrue("Auth-Vollbild ohne Cache", s.isAuthRequired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `authentifiziert setzt weder Banner noch Auth-Hinweis`() = runTest {
        repository.gradesFlow.value = listOf(
            grade(1, "X", "SoSe 26", 2.0, GradeStatus.PASSED)
        )
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertFalse(s.showReauthBanner)
            assertFalse(s.isAuthRequired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `lastRefreshEpoch aus Settings landet im State`() = runTest {
        lastRefreshFlow.value = 1_700_000_000_000L
        onlineFlow.value = false
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals(1_700_000_000_000L, s.lastRefreshEpoch)
            assertFalse(s.isOnline)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `consumeError loescht die Fehlermeldung`() = runTest {
        repository.refreshResult = AppResult.Failure(RuntimeException("Netz weg"))
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            assertEquals("Netz weg", expectMostRecentItem().errorMessage)
            vm.consumeError()
            advanceUntilIdle()
            assertNull(expectMostRecentItem().errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh ist reentrant-sicher waehrend eines laufenden Refreshs`() = runTest {
        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            expectMostRecentItem()
            vm.refresh()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        // Cold-Start + 1 manueller Refresh.
        assertEquals(2, repository.refreshCalls)
    }

    @Test
    fun `semesterSortKey ordnet SoSe vor WiSe desselben Jahres`() {
        val soSe = GradesUiState.semesterSortKey("SoSe 26")
        val wiSe = GradesUiState.semesterSortKey("WiSe 26/27")
        assertTrue("WiSe 26/27 muss neuer sein als SoSe 26", wiSe > soSe)
    }

    @Test
    fun `semesterSortKey erkennt vierstellige Jahreszahl`() {
        val ss2025 = GradesUiState.semesterSortKey("SS 2025")
        val ss2026 = GradesUiState.semesterSortKey("SS 2026")
        assertTrue(ss2026 > ss2025)
    }
}
