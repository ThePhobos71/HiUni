package de.transio.hiuni.core.sync

import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.network.ConnectivityObserver
import de.transio.hiuni.feature.bib.data.BibAlarmMode
import de.transio.hiuni.feature.bib.data.BibFavoritesStore
import de.transio.hiuni.feature.bib.data.BibRoomAlarm
import de.transio.hiuni.feature.bib.data.BibRoomAlarmStore
import de.transio.hiuni.feature.bib.data.BibRepository
import de.transio.hiuni.feature.grades.data.GradesRepository
import de.transio.hiuni.feature.grades.data.GradesSyncResult
import de.transio.hiuni.feature.learnweb.data.LearnwebRepository
import de.transio.hiuni.feature.mensa.data.MensaRepository
import de.transio.hiuni.feature.movies.data.MoviesRepository
import de.transio.hiuni.feature.sport.data.SportRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Unit-Tests für [PrefetchOrchestrator]: TTL-Skip pro Feature, Offline-Skip,
 * Auth-Skip für auth-pflichtige Features, „ein Fehler bricht die Kette nicht ab",
 * und die Staffelung über den TestScheduler (advanceUntilIdle treibt die
 * [PrefetchOrchestrator.STAGGER_DELAY_MS]-`delay`s).
 *
 * Wie im [LoginSyncOrchestratorTest] läuft der [appScope] auf einem
 * [UnconfinedTestDispatcher] am [TestScope.backgroundScope]-Job.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchOrchestratorTest {

    private val connectivity = mockk<ConnectivityObserver>(relaxed = true)
    private val casSession = mockk<CasSession>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val mensa = mockk<MensaRepository>(relaxed = true)
    private val learnweb = mockk<LearnwebRepository>(relaxed = true)
    private val grades = mockk<GradesRepository>(relaxed = true)
    private val sport = mockk<SportRepository>(relaxed = true)
    private val movies = mockk<MoviesRepository>(relaxed = true)
    private val bib = mockk<BibRepository>(relaxed = true)
    private val lsfSyncScheduler = mockk<LsfSyncScheduler>(relaxed = true)
    private val bibFavorites = mockk<BibFavoritesStore>(relaxed = true)
    private val bibAlarm = mockk<BibRoomAlarmStore>(relaxed = true)

    /** Feste Uhr (Montag 10:00), damit die tageszeitabhängige Bib-TTL deterministisch ist. */
    private val zone = ZoneId.of("Europe/Berlin")
    private var clock: Clock = Clock.fixed(
        LocalDateTime.of(2026, 10, 26, 10, 0).atZone(zone).toInstant(), zone
    )

    @Before
    fun setUpFavorites() {
        coEvery { bibFavorites.currentFavorites() } returns emptySet()
        coEvery { bibAlarm.current(any()) } returns BibRoomAlarm(mode = BibAlarmMode.ALWAYS)
    }

    private fun authState(): CasState =
        CasState.Authenticated(username = "tester", obtainedAt = Instant.now())

    /** Alle Feature-Epochs auf „nie gesynct" (0L) → nichts wird per TTL geskippt. */
    private fun allEpochsStale() {
        every { settings.lastMensaRefreshEpoch } returns flowOf(0L)
        every { settings.lastLsfExamsRefreshEpoch } returns flowOf(0L)
        every { settings.lastLearnwebRefreshEpoch } returns flowOf(0L)
        every { settings.lastGradesRefreshEpoch } returns flowOf(0L)
        every { settings.lastSportRefreshEpoch } returns flowOf(0L)
        every { settings.lastMoviesRefreshEpoch } returns flowOf(0L)
    }

    /** refresh()-Rückgaben so einstellen, dass alle Features erfolgreich sind. */
    private fun allRefreshSucceed() {
        coEvery { mensa.refresh(any(), any()) } returns AppResult.Success(Unit)
        coEvery { learnweb.refresh(any()) } returns AppResult.Success(Unit)
        coEvery { grades.refresh(any()) } returns AppResult.Success(GradesSyncResult(0, 0, 0, 0))
        coEvery { sport.refresh(any()) } returns AppResult.Success(Unit)
        coEvery { movies.refresh(any()) } returns AppResult.Success(Unit)
        coEvery { bib.refreshIfStale(any()) } returns AppResult.Success(Unit)
        every { lsfSyncScheduler.triggerNow() } just Runs
    }

    private fun TestScope.newOrchestrator(): PrefetchOrchestrator {
        // WICHTIG: NICHT aus backgroundScope.coroutineContext bauen — Tasks mit dem
        // Background-Marker werden von advanceUntilIdle() bewusst ignoriert, wodurch
        // die delay(STAGGER_DELAY_MS)-Staffelung nie virtuell voranschreitet und die
        // Warmup-Kette im Test am ersten delay "hängen bleibt". Eigener Job + UTD am
        // testScheduler → Delays laufen auf virtueller Zeit und advanceUntilIdle
        // treibt die komplette Kette bis zum Ende.
        val appScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        return PrefetchOrchestrator(
            connectivity = connectivity,
            casSession = casSession,
            settings = settings,
            mensaRepository = mensa,
            learnwebRepository = learnweb,
            gradesRepository = grades,
            sportRepository = sport,
            moviesRepository = movies,
            bibRepository = bib,
            lsfSyncScheduler = lsfSyncScheduler,
            bibFavorites = bibFavorites,
            bibAlarm = bibAlarm,
            clock = clock,
            appScope = appScope
        )
    }

    @Test
    fun `online und authenticated refresht alle Features`() = runTest {
        every { connectivity.isOnline } returns MutableStateFlow(true)
        every { casSession.state } returns MutableStateFlow(authState())
        allEpochsStale()
        allRefreshSucceed()

        newOrchestrator().prefetch()
        advanceUntilIdle()

        coVerify(exactly = 1) { mensa.refresh(force = false) }
        verify(exactly = 1) { lsfSyncScheduler.triggerNow() }
        coVerify(exactly = 1) { learnweb.refresh(force = false) }
        coVerify(exactly = 1) { grades.refresh(force = false) }
        coVerify(exactly = 1) { sport.refresh(force = false) }
        coVerify(exactly = 1) { movies.refresh(force = false) }
        coVerify(exactly = 1) { bib.refreshIfStale(any()) }
    }

    @Test
    fun `offline ueberspringt den kompletten Warmup`() = runTest {
        every { connectivity.isOnline } returns MutableStateFlow(false)
        every { casSession.state } returns MutableStateFlow(authState())
        allEpochsStale()
        allRefreshSucceed()

        newOrchestrator().prefetch()
        advanceUntilIdle()

        coVerify(exactly = 0) { mensa.refresh(any(), any()) }
        verify(exactly = 0) { lsfSyncScheduler.triggerNow() }
        coVerify(exactly = 0) { grades.refresh(any()) }
        coVerify(exactly = 0) { movies.refresh(any()) }
    }

    @Test
    fun `ohne Session werden nur auth-freie Features gewaermt`() = runTest {
        every { connectivity.isOnline } returns MutableStateFlow(true)
        every { casSession.state } returns MutableStateFlow(CasState.NeedsLogin)
        allEpochsStale()
        allRefreshSucceed()

        newOrchestrator().prefetch()
        advanceUntilIdle()

        // Auth-frei → laufen.
        coVerify(exactly = 1) { mensa.refresh(force = false) }
        coVerify(exactly = 1) { sport.refresh(force = false) }
        coVerify(exactly = 1) { movies.refresh(force = false) }
        // Auth-pflichtig → geskippt.
        verify(exactly = 0) { lsfSyncScheduler.triggerNow() }
        coVerify(exactly = 0) { learnweb.refresh(any()) }
        coVerify(exactly = 0) { grades.refresh(any()) }
        coVerify(exactly = 0) { bib.refreshIfStale(any()) }
    }

    @Test
    fun `frisches Feature wird per TTL uebersprungen`() = runTest {
        every { connectivity.isOnline } returns MutableStateFlow(true)
        every { casSession.state } returns MutableStateFlow(authState())
        allRefreshSucceed()
        // Mensa vor 1 Minute gesynct → jünger als TTL_MENSA_MS (6h) → skip.
        val recent = System.currentTimeMillis() - 60_000
        every { settings.lastMensaRefreshEpoch } returns flowOf(recent)
        // Rest stale.
        every { settings.lastLsfExamsRefreshEpoch } returns flowOf(0L)
        every { settings.lastLearnwebRefreshEpoch } returns flowOf(0L)
        every { settings.lastGradesRefreshEpoch } returns flowOf(0L)
        every { settings.lastSportRefreshEpoch } returns flowOf(0L)
        every { settings.lastMoviesRefreshEpoch } returns flowOf(0L)

        newOrchestrator().prefetch()
        advanceUntilIdle()

        coVerify(exactly = 0) { mensa.refresh(any(), any()) }
        // Andere laufen weiter.
        coVerify(exactly = 1) { grades.refresh(force = false) }
        coVerify(exactly = 1) { movies.refresh(force = false) }
    }

    @Test
    fun `ein Feature-Fehler bricht die Kette nicht ab`() = runTest {
        every { connectivity.isOnline } returns MutableStateFlow(true)
        every { casSession.state } returns MutableStateFlow(authState())
        allEpochsStale()
        allRefreshSucceed()
        // Mensa (erstes Feature) wirft — nachfolgende Features müssen trotzdem laufen.
        coEvery { mensa.refresh(any(), any()) } throws RuntimeException("STW-ON down")

        newOrchestrator().prefetch()
        advanceUntilIdle()

        coVerify(exactly = 1) { mensa.refresh(force = false) }
        verify(exactly = 1) { lsfSyncScheduler.triggerNow() }
        coVerify(exactly = 1) { grades.refresh(force = false) }
        coVerify(exactly = 1) { movies.refresh(force = false) }
        coVerify(exactly = 1) { bib.refreshIfStale(any()) }
    }

    @Test
    fun `Warmup staffelt die Refreshes zeitlich`() = runTest {
        every { connectivity.isOnline } returns MutableStateFlow(true)
        every { casSession.state } returns MutableStateFlow(authState())
        allEpochsStale()
        allRefreshSucceed()

        val start = currentTime
        newOrchestrator().prefetch()
        advanceUntilIdle()
        val elapsed = currentTime - start

        // 7 ausgeführte Features → mindestens 6 Staffel-Delays zwischen ihnen
        // (bzw. bis zu 7, je nachdem ob nach dem letzten noch gewartet wird).
        val minExpected = 6 * PrefetchOrchestrator.STAGGER_DELAY_MS
        assertTrue(
            "erwartet >= ${minExpected}ms Staffelung, war ${elapsed}ms",
            elapsed >= minExpected
        )
    }

    @Test
    fun `zweiter prefetch-Aufruf waehrend laufendem Warmup ist idempotent`() = runTest {
        every { connectivity.isOnline } returns MutableStateFlow(true)
        every { casSession.state } returns MutableStateFlow(authState())
        allEpochsStale()
        allRefreshSucceed()

        val orchestrator = newOrchestrator()
        orchestrator.prefetch()
        // Zweiter Aufruf sofort danach — running-Flag ist noch true → no-op.
        orchestrator.prefetch()
        advanceUntilIdle()

        // Jedes Feature genau einmal, kein Doppel-Warmup.
        coVerify(exactly = 1) { mensa.refresh(force = false) }
        coVerify(exactly = 1) { grades.refresh(force = false) }
    }

    @Test
    fun `ohne Lieblings-Raum bleibt die lange Bib-TTL`() = runTest {
        every { connectivity.isOnline } returns MutableStateFlow(true)
        every { casSession.state } returns MutableStateFlow(authState())
        allEpochsStale()
        allRefreshSucceed()

        newOrchestrator().prefetch()
        advanceUntilIdle()

        coVerify(exactly = 1) { bib.refreshIfStale(PrefetchOrchestrator.TTL_BIB_MS) }
    }

    @Test
    fun `mit Lieblings-Raum tagsueber die kurze Bib-TTL`() = runTest {
        every { connectivity.isOnline } returns MutableStateFlow(true)
        every { casSession.state } returns MutableStateFlow(authState())
        coEvery { bibFavorites.currentFavorites() } returns setOf(102)
        allEpochsStale()
        allRefreshSucceed()

        newOrchestrator().prefetch()
        advanceUntilIdle()

        coVerify(exactly = 1) { bib.refreshIfStale(PrefetchOrchestrator.TTL_BIB_FAVORITES_MS) }
    }

    @Test
    fun `mit Lieblings-Raum laeuft der Bib-Check auch ohne Session`() = runTest {
        every { connectivity.isOnline } returns MutableStateFlow(true)
        every { casSession.state } returns MutableStateFlow(CasState.NeedsLogin)
        coEvery { bibFavorites.currentFavorites() } returns setOf(102)
        allEpochsStale()
        allRefreshSucceed()

        newOrchestrator().prefetch()
        advanceUntilIdle()

        // Belegung ist anonym lesbar; Auth-pflichtige Features bleiben geskippt.
        coVerify(exactly = 1) { bib.refreshIfStale(PrefetchOrchestrator.TTL_BIB_FAVORITES_MS) }
        coVerify(exactly = 0) { grades.refresh(any()) }
    }

    @Test
    fun `mit Lieblings-Raum nachts die lange Bib-TTL`() = runTest {
        every { connectivity.isOnline } returns MutableStateFlow(true)
        every { casSession.state } returns MutableStateFlow(authState())
        coEvery { bibFavorites.currentFavorites() } returns setOf(102)
        clock = Clock.fixed(LocalDateTime.of(2026, 10, 26, 23, 0).atZone(zone).toInstant(), zone)
        allEpochsStale()
        allRefreshSucceed()

        newOrchestrator().prefetch()
        advanceUntilIdle()

        coVerify(exactly = 1) { bib.refreshIfStale(PrefetchOrchestrator.TTL_BIB_MS) }
    }

    @Test
    fun `mit Lieblings-Raum aber ausgeschaltetem Alarm die lange TTL und nur mit Session`() = runTest {
        every { connectivity.isOnline } returns MutableStateFlow(true)
        every { casSession.state } returns MutableStateFlow(CasState.NeedsLogin)
        coEvery { bibFavorites.currentFavorites() } returns setOf(102)
        coEvery { bibAlarm.current(any()) } returns BibRoomAlarm(mode = BibAlarmMode.OFF)
        allEpochsStale()
        allRefreshSucceed()

        newOrchestrator().prefetch()
        advanceUntilIdle()

        // Ohne aktiven Alarm ist der Bib-Schritt nur Warmup: ohne Session übersprungen.
        coVerify(exactly = 0) { bib.refreshIfStale(any()) }
    }

    @Test
    fun `mit Lieblings-Raum und ausgeschaltetem Alarm eingeloggt die lange TTL`() = runTest {
        every { connectivity.isOnline } returns MutableStateFlow(true)
        every { casSession.state } returns MutableStateFlow(authState())
        coEvery { bibFavorites.currentFavorites() } returns setOf(102)
        coEvery { bibAlarm.current(any()) } returns BibRoomAlarm(mode = BibAlarmMode.OFF)
        allEpochsStale()
        allRefreshSucceed()

        newOrchestrator().prefetch()
        advanceUntilIdle()

        coVerify(exactly = 1) { bib.refreshIfStale(PrefetchOrchestrator.TTL_BIB_MS) }
    }
}
