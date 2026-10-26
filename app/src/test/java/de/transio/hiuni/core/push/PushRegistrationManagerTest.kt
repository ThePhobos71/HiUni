package de.transio.hiuni.core.push

import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.datastore.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests für die netzfreien Pfade des [PushRegistrationManager]: Idempotenz
 * (No-Op wenn Token UND Server unverändert), Feature-aus-Skip, fehlende Config
 * und Unregister-ohne-Token. Keiner dieser Pfade löst einen HTTP-Call aus, sie
 * laufen also ohne MockWebServer als reine JVM-Tests.
 */
class PushRegistrationManagerTest {

    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val serverConfig = mockk<PushServerConfig>()

    private fun manager() = PushRegistrationManager(settings, serverConfig, Dispatchers.Unconfined)

    @Before
    fun setUp() {
        coEvery { serverConfig.current() } returns
            PushServerConfig.Resolved(serverUrl = SERVER, apiKey = "key")
        every { settings.pushRegisteredServer } returns flowOf(SERVER)
    }

    @Test
    fun `ensureRegistered ist No-Op wenn Token und Server bereits registriert`() = runBlocking {
        every { settings.pushSyncEnabled } returns flowOf(true)
        every { settings.pushRegisteredToken } returns flowOf("token-123")

        val result = manager().ensureRegistered("token-123")

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 0) { settings.setPushRegistration(any(), any()) }
    }

    @Test
    fun `ensureRegistered skippt wenn Feature aus`() = runBlocking {
        every { settings.pushSyncEnabled } returns flowOf(false)

        val result = manager().ensureRegistered("token-abc")

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 0) { settings.setPushRegistration(any(), any()) }
    }

    @Test
    fun `ensureRegistered mit leerem Token ist No-Op`() = runBlocking {
        every { settings.pushSyncEnabled } returns flowOf(true)

        val result = manager().ensureRegistered("")

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 0) { settings.setPushRegistration(any(), any()) }
    }

    @Test
    fun `ensureRegistered ohne API-Key schlaegt fehl ohne Netz-Call`() = runBlocking {
        every { settings.pushSyncEnabled } returns flowOf(true)
        coEvery { serverConfig.current() } returns
            PushServerConfig.Resolved(serverUrl = SERVER, apiKey = "")

        val result = manager().ensureRegistered("token-xyz")

        // IllegalArgumentException → Worker wertet das als nicht retrybar.
        assertTrue(result is AppResult.Failure && result.error is IllegalArgumentException)
        coVerify(exactly = 0) { settings.setPushRegistration(any(), any()) }
    }

    @Test
    fun `unregister ohne registrierten Token loescht nur lokal ohne Netz-Call`() = runBlocking {
        every { settings.pushRegisteredToken } returns flowOf("")

        val result = manager().unregister(token = null)

        assertTrue(result is AppResult.Success)
        coVerify { settings.setPushRegistration(token = "", server = "") }
    }

    private companion object {
        const val SERVER = "https://tickle.transio.de"
    }
}
