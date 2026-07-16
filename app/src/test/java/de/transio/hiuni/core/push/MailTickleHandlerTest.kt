package de.transio.hiuni.core.push

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests für die reine Tickle-Entscheidungslogik. Kein Firebase, kein Android —
 * genau deshalb liegt die Logik in [MailTickleHandler] und nicht direkt im
 * [HiUniMessagingService].
 */
class MailTickleHandlerTest {

    private val ready = MailTickleHandler.Preconditions(pushEnabled = true, hasMailAccount = true)

    @Test
    fun `mail_tickle mit erfuellten Vorbedingungen loest Sync aus`() {
        val decision = MailTickleHandler.decide(
            data = mapOf("type" to "mail_tickle"),
            preconditions = ready
        )
        assertEquals(MailTickleHandler.Decision.SYNC_MAIL, decision)
    }

    @Test
    fun `sync_tickle mit erfuellten Vorbedingungen loest SYNC_ALL aus`() {
        val decision = MailTickleHandler.decide(
            data = mapOf("type" to "sync_tickle"),
            preconditions = ready
        )
        assertEquals(MailTickleHandler.Decision.SYNC_ALL, decision)
    }

    @Test
    fun `sync_tickle ohne Feature-Flag wird still ignoriert`() {
        val decision = MailTickleHandler.decide(
            data = mapOf("type" to "sync_tickle"),
            preconditions = ready.copy(pushEnabled = false)
        )
        assertEquals(MailTickleHandler.Decision.IGNORE_SILENTLY, decision)
    }

    @Test
    fun `sync_tickle ohne Mail-Konto wird still ignoriert`() {
        val decision = MailTickleHandler.decide(
            data = mapOf("type" to "sync_tickle"),
            preconditions = ready.copy(hasMailAccount = false)
        )
        assertEquals(MailTickleHandler.Decision.IGNORE_SILENTLY, decision)
    }

    @Test
    fun `mail_tickle ohne Feature-Flag wird still ignoriert`() {
        val decision = MailTickleHandler.decide(
            data = mapOf("type" to "mail_tickle"),
            preconditions = ready.copy(pushEnabled = false)
        )
        assertEquals(MailTickleHandler.Decision.IGNORE_SILENTLY, decision)
    }

    @Test
    fun `mail_tickle ohne Mail-Konto wird still ignoriert`() {
        val decision = MailTickleHandler.decide(
            data = mapOf("type" to "mail_tickle"),
            preconditions = ready.copy(hasMailAccount = false)
        )
        assertEquals(MailTickleHandler.Decision.IGNORE_SILENTLY, decision)
    }

    @Test
    fun `weder Feature noch Konto - still ignoriert`() {
        val decision = MailTickleHandler.decide(
            data = mapOf("type" to "mail_tickle"),
            preconditions = MailTickleHandler.Preconditions(
                pushEnabled = false,
                hasMailAccount = false
            )
        )
        assertEquals(MailTickleHandler.Decision.IGNORE_SILENTLY, decision)
    }

    @Test
    fun `unbekannter Message-Type ergibt UNKNOWN_TYPE`() {
        val decision = MailTickleHandler.decide(
            data = mapOf("type" to "something_else"),
            preconditions = ready
        )
        assertEquals(MailTickleHandler.Decision.UNKNOWN_TYPE, decision)
    }

    @Test
    fun `fehlender Type-Key ergibt UNKNOWN_TYPE`() {
        val decision = MailTickleHandler.decide(
            data = emptyMap(),
            preconditions = ready
        )
        assertEquals(MailTickleHandler.Decision.UNKNOWN_TYPE, decision)
    }

    @Test
    fun `leerer Type-Wert ergibt UNKNOWN_TYPE`() {
        val decision = MailTickleHandler.decide(
            data = mapOf("type" to ""),
            preconditions = ready
        )
        assertEquals(MailTickleHandler.Decision.UNKNOWN_TYPE, decision)
    }
}
