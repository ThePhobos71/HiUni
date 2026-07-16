package de.transio.hiuni.feature.settings

import app.cash.turbine.test
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.feature.lsf.data.LsfClient
import de.transio.hiuni.feature.lsf.data.LsfConnectionInfo
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CasLoginViewModelTest {

    private val casSession = mockk<CasSession>(relaxed = true)
    private val lsfClient = mockk<LsfClient>(relaxed = true)
    private val casStateFlow = MutableStateFlow<CasState>(CasState.NeedsLogin)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { casSession.state } returns casStateFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = CasLoginViewModel(casSession, lsfClient)

    private fun connectionInfo(ok: Boolean) = LsfConnectionInfo(
        ok = ok,
        statusCode = if (ok) 200 else 500,
        username = if (ok) "karstens" else null,
        role = if (ok) "Student" else null,
        errorMessage = if (ok) null else "LSF antwortete mit HTTP 500"
    )

    @Test
    fun `state spiegelt den CasSession-State wider`() = runTest {
        val vm = newVm()
        vm.state.test {
            assertEquals(CasState.NeedsLogin, awaitItem())
            casStateFlow.value = CasState.Authenticated("karstens", Instant.now())
            assertTrue(awaitItem() is CasState.Authenticated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onLoginResult mit success triggert refreshState`() = runTest {
        val vm = newVm()
        vm.onLoginResult(success = true)
        verify(exactly = 1) { casSession.refreshState() }
    }

    @Test
    fun `onLoginResult mit failure triggert kein refreshState`() = runTest {
        val vm = newVm()
        vm.onLoginResult(success = false)
        verify(exactly = 0) { casSession.refreshState() }
    }

    @Test
    fun `logout ruft CasSession-logout und setzt UI zurueck`() = runTest {
        every { casSession.logout() } just Runs
        val vm = newVm()
        // Vorher einen Testresult setzen, damit logout wirklich zurücksetzt.
        coEvery { lsfClient.testConnection() } returns connectionInfo(ok = true)
        vm.testLsfConnection()
        vm.ui.test {
            // aktueller State enthält lastTestResult != null
            assertTrue(expectMostRecentItem().lastTestResult != null)
            vm.logout()
            val afterLogout = awaitItem()
            assertNull(afterLogout.lastTestResult)
            assertFalse(afterLogout.testing)
            cancelAndIgnoreRemainingEvents()
        }
        verify { casSession.logout() }
    }

    @Test
    fun `testLsfConnection erfolgreicher Pfad - testing true dann Ergebnis`() = runTest {
        coEvery { lsfClient.testConnection() } returns connectionInfo(ok = true)
        val vm = newVm()
        vm.testLsfConnection()
        vm.ui.test {
            val s = expectMostRecentItem()
            assertFalse(s.testing)
            assertEquals(true, s.lastTestResult?.ok)
            assertEquals("karstens", s.lastTestResult?.username)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `testLsfConnection Fehlerpfad - Ergebnis mit ok false und Fehlermeldung`() = runTest {
        coEvery { lsfClient.testConnection() } returns connectionInfo(ok = false)
        val vm = newVm()
        vm.testLsfConnection()
        vm.ui.test {
            val s = expectMostRecentItem()
            assertFalse(s.testing)
            assertEquals(false, s.lastTestResult?.ok)
            assertEquals("LSF antwortete mit HTTP 500", s.lastTestResult?.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `testLsfConnection setzt testing waehrend des Roundtrips auf true`() = runTest {
        // Deferred, das wir manuell auflösen, um den Zwischenzustand zu beobachten.
        val gate = CompletableDeferred<LsfConnectionInfo>()
        coEvery { lsfClient.testConnection() } coAnswers { gate.await() }
        val vm = newVm()
        vm.ui.test {
            assertEquals(CasUiState(), awaitItem()) // initial
            vm.testLsfConnection()
            // Erste Emission nach Aufruf: testing=true, lastTestResult=null
            val running = awaitItem()
            assertTrue(running.testing)
            assertNull(running.lastTestResult)
            // Jetzt Roundtrip abschließen
            gate.complete(connectionInfo(ok = true))
            val done = awaitItem()
            assertFalse(done.testing)
            assertEquals(true, done.lastTestResult?.ok)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
