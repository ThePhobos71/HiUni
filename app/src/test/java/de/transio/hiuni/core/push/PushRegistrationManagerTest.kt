package de.transio.hiuni.core.push

import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.datastore.SettingsDataStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für die Firebase-freien Pfade des [PushRegistrationManager]:
 * Idempotenz (No-Op wenn Token unverändert), Feature-aus-Skip und
 * Unregister-ohne-Token. Alle drei Pfade lösen KEINEN HTTP-Call aus, laufen
 * also ohne MockWebServer als reine JVM-Tests.
 */
class PushRegistrationManagerTest {

    private val settings = mockk<SettingsDataStore>(relaxed = true)

    private fun manager() = PushRegistrationManager(settings, Dispatchers.Unconfined)

    @Test
    fun `ensureRegistered ist No-Op wenn Token bereits registriert`() = runBlocking {
        every { settings.mailPushEnabled } returns flowOf(true)
        every { settings.mailPushRegisteredToken } returns flowOf("token-123")

        val result = manager().ensureRegistered("token-123")

        assertTrue(result is AppResult.Success)
        // Kein erneutes Persistieren des (unveränderten) Tokens.
        coVerify(exactly = 0) { settings.setMailPushRegisteredToken(any()) }
    }

    @Test
    fun `ensureRegistered skippt wenn Feature aus`() = runBlocking {
        every { settings.mailPushEnabled } returns flowOf(false)

        val result = manager().ensureRegistered("token-abc")

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 0) { settings.setMailPushRegisteredToken(any()) }
    }

    @Test
    fun `ensureRegistered mit leerem Token ist No-Op`() = runBlocking {
        every { settings.mailPushEnabled } returns flowOf(true)

        val result = manager().ensureRegistered("")

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 0) { settings.setMailPushRegisteredToken(any()) }
    }

    @Test
    fun `unregister ohne registrierten Token loescht nur lokal ohne Netz-Call`() = runBlocking {
        every { settings.mailPushRegisteredToken } returns flowOf("")

        val result = manager().unregister(token = null)

        assertTrue(result is AppResult.Success)
        // Lokale Markierung wird (idempotent) auf leer gesetzt.
        coVerify { settings.setMailPushRegisteredToken("") }
    }
}
