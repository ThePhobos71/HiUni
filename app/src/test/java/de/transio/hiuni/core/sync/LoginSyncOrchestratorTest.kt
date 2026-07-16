package de.transio.hiuni.core.sync

import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.feature.email.data.EmailRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/**
 * Tests für [LoginSyncOrchestrator] — Transition-Dedup über den lokalen
 * `lastWasAuthenticated`-Zustand, den [LoginSyncOrchestrator.MIN_RESYNC_INTERVAL]-
 * Skip und die `start()`-Idempotenz (AtomicBoolean.compareAndSet), damit ein
 * schneller Login-State-Wechsel bzw. ein zweiter start()-Aufruf keinen doppelten
 * Sync triggert.
 *
 * Reine JVM-Coroutines-Tests: der [appScope] wird pro Test aus dem
 * [TestScope.testScheduler] mit einem [UnconfinedTestDispatcher] gebaut. Das ist
 * hier bewusst: der Orchestrator sammelt einen conflated [MutableStateFlow] via
 * `state.collect`. Unter dem Default-`StandardTestDispatcher` startet der
 * Collector erst beim nächsten `advanceUntilIdle()` und sieht durch die
 * Conflation nur den JEWEILS letzten Wert — die not-auth→auth-Transition ginge
 * verloren. Der Unconfined-Dispatcher lässt den Collector eager auf jeder
 * Emission laufen, sodass jede Transition beobachtet wird. Der Scope hängt am
 * `backgroundScope`-Job und wird darüber automatisch aufgeräumt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginSyncOrchestratorTest {

    private val casSession = mockk<CasSession>(relaxed = true)
    private val lsfSyncScheduler = mockk<LsfSyncScheduler>(relaxed = true)
    private val emailRepository = mockk<EmailRepository>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val prefetchOrchestrator = mockk<PrefetchOrchestrator>(relaxed = true)

    private fun authState(): CasState =
        CasState.Authenticated(username = "tester", obtainedAt = Instant.now())

    /**
     * Baut den Orchestrator mit einem app-scope, der auf einem
     * [UnconfinedTestDispatcher] läuft (siehe Klassen-KDoc), aber am
     * [TestScope.backgroundScope]-Job hängt und so automatisch gecancelt wird.
     */
    private fun TestScope.newOrchestrator(
        state: MutableStateFlow<CasState>
    ): LoginSyncOrchestrator {
        every { casSession.state } returns state
        coEvery { emailRepository.refresh(any()) } returns AppResult.Success(Unit)
        val appScope = CoroutineScope(
            backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
        )
        return LoginSyncOrchestrator(
            casSession = casSession,
            lsfSyncScheduler = lsfSyncScheduler,
            emailRepository = emailRepository,
            settings = settings,
            prefetchOrchestrator = prefetchOrchestrator,
            appScope = appScope
        )
    }

    @Test
    fun `frische Transition not-auth zu auth triggert LSF-Sync und Email-Refresh`() = runTest {
        every { settings.lastLsfSyncEpoch } returns flowOf(0L) // noch nie gesynct
        val state = MutableStateFlow<CasState>(CasState.NeedsLogin)
        val orchestrator = newOrchestrator(state)

        orchestrator.start()
        advanceUntilIdle()
        state.value = authState()
        advanceUntilIdle()

        verify(exactly = 1) { lsfSyncScheduler.triggerNow() }
        coVerify(exactly = 1) { emailRepository.refresh(force = true) }
        // Frischer Login stößt zusätzlich den gestaffelten Feature-Warmup an.
        verify(exactly = 1) { prefetchOrchestrator.prefetch() }
    }

    @Test
    fun `Cold-Start bereits authenticated triggert keinen spurious Sync`() = runTest {
        every { settings.lastLsfSyncEpoch } returns flowOf(0L)
        // Startwert ist schon Authenticated → lastWasAuthenticated=true, kein Trigger.
        val state = MutableStateFlow<CasState>(authState())
        val orchestrator = newOrchestrator(state)

        orchestrator.start()
        advanceUntilIdle()

        verify(exactly = 0) { lsfSyncScheduler.triggerNow() }
        coVerify(exactly = 0) { emailRepository.refresh(any()) }
    }

    @Test
    fun `Transition mit kuerzlichem Sync unter MIN_RESYNC_INTERVAL wird geskippt`() = runTest {
        // Letzter Sync vor 1h → jünger als 6h-Schwelle → skip.
        val recent = Instant.now().toEpochMilli() - 60L * 60 * 1000
        every { settings.lastLsfSyncEpoch } returns flowOf(recent)
        val state = MutableStateFlow<CasState>(CasState.NeedsLogin)
        val orchestrator = newOrchestrator(state)

        orchestrator.start()
        advanceUntilIdle()
        state.value = authState()
        advanceUntilIdle()

        verify(exactly = 0) { lsfSyncScheduler.triggerNow() }
        coVerify(exactly = 0) { emailRepository.refresh(any()) }
    }

    @Test
    fun `Transition mit altem Sync ueber MIN_RESYNC_INTERVAL triggert Sync`() = runTest {
        // Letzter Sync vor 7h → älter als 6h → sync.
        val old = Instant.now().toEpochMilli() - 7L * 60 * 60 * 1000
        every { settings.lastLsfSyncEpoch } returns flowOf(old)
        val state = MutableStateFlow<CasState>(CasState.NeedsLogin)
        val orchestrator = newOrchestrator(state)

        orchestrator.start()
        advanceUntilIdle()
        state.value = authState()
        advanceUntilIdle()

        verify(exactly = 1) { lsfSyncScheduler.triggerNow() }
        coVerify(exactly = 1) { emailRepository.refresh(force = true) }
    }

    @Test
    fun `zweiter start-Aufruf ist idempotent - kein zweiter Collector`() = runTest {
        every { settings.lastLsfSyncEpoch } returns flowOf(0L)
        val state = MutableStateFlow<CasState>(CasState.NeedsLogin)
        val orchestrator = newOrchestrator(state)

        orchestrator.start()
        orchestrator.start() // compareAndSet(false,true) beim zweiten Mal → ignored
        advanceUntilIdle()
        state.value = authState()
        advanceUntilIdle()

        // Nur ein Collector läuft → genau ein Trigger, kein Doppel-Feuer.
        verify(exactly = 1) { lsfSyncScheduler.triggerNow() }
    }

    @Test
    fun `NeedsReauth zwischen Logins wird als not-auth gewertet und retriggert danach`() = runTest {
        every { settings.lastLsfSyncEpoch } returns flowOf(0L)
        val state = MutableStateFlow<CasState>(CasState.NeedsLogin)
        val orchestrator = newOrchestrator(state)

        orchestrator.start()
        advanceUntilIdle()
        // 1. Login
        state.value = authState()
        advanceUntilIdle()
        // Session verfällt → NeedsReauth (not-authenticated)
        state.value = CasState.NeedsReauth
        advanceUntilIdle()
        // Erneuter Login → zweite echte Transition
        state.value = authState()
        advanceUntilIdle()

        verify(exactly = 2) { lsfSyncScheduler.triggerNow() }
    }

    @Test
    fun `wiederholter Authenticated-Wert ohne Zwischen-Logout triggert nicht erneut`() = runTest {
        every { settings.lastLsfSyncEpoch } returns flowOf(0L)
        val state = MutableStateFlow<CasState>(CasState.NeedsLogin)
        val orchestrator = newOrchestrator(state)

        orchestrator.start()
        advanceUntilIdle()
        state.value = CasState.Authenticated("a", Instant.ofEpochMilli(1))
        advanceUntilIdle()
        // Neuer Authenticated-Wert (anderer obtainedAt), aber KEIN Logout dazwischen.
        state.value = CasState.Authenticated("a", Instant.ofEpochMilli(2))
        advanceUntilIdle()

        // lastWasAuthenticated bleibt true → kein zweiter Trigger.
        verify(exactly = 1) { lsfSyncScheduler.triggerNow() }
    }

    @Test
    fun `fehlgeschlagener Email-Refresh reisst den Trigger nicht ab`() = runTest {
        every { settings.lastLsfSyncEpoch } returns flowOf(0L)
        coEvery { emailRepository.refresh(any()) } returns
            AppResult.Failure(RuntimeException("imap down"))
        val state = MutableStateFlow<CasState>(CasState.NeedsLogin)
        val orchestrator = newOrchestrator(state)

        orchestrator.start()
        advanceUntilIdle()
        state.value = authState()
        advanceUntilIdle()

        // LSF-Sync wird trotz Email-Fehler getriggert (runCatching kapselt).
        verify(exactly = 1) { lsfSyncScheduler.triggerNow() }
    }
}
