package de.transio.hiuni.core.push

import com.google.firebase.messaging.RemoteMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für die reine Tickle-Entscheidungslogik. Kein Firebase, kein Android —
 * genau deshalb liegt die Logik in [TickleHandler] und nicht direkt im
 * [HiUniMessagingService].
 */
class TickleHandlerTest {

    private val ready = TickleHandler.Preconditions(pushEnabled = true, hasMailAccount = true)

    @Test
    fun `mail_tickle mit erfuellten Vorbedingungen loest Sync aus`() {
        val decision = TickleHandler.decide(
            data = mapOf("type" to "mail_tickle"),
            preconditions = ready
        )
        assertEquals(TickleHandler.Decision.SYNC_MAIL, decision)
    }

    @Test
    fun `sync_tickle mit erfuellten Vorbedingungen loest SYNC_ALL aus`() {
        val decision = TickleHandler.decide(
            data = mapOf("type" to "sync_tickle"),
            preconditions = ready
        )
        assertEquals(TickleHandler.Decision.SYNC_ALL, decision)
    }

    @Test
    fun `sync_tickle ohne Feature-Flag wird still ignoriert`() {
        val decision = TickleHandler.decide(
            data = mapOf("type" to "sync_tickle"),
            preconditions = ready.copy(pushEnabled = false)
        )
        assertEquals(TickleHandler.Decision.IGNORE_SILENTLY, decision)
    }

    @Test
    fun `sync_tickle ohne Mail-Konto loest trotzdem den Prefetch aus`() {
        val decision = TickleHandler.decide(
            data = mapOf("type" to "sync_tickle"),
            preconditions = ready.copy(hasMailAccount = false)
        )
        assertEquals(TickleHandler.Decision.PREFETCH_ONLY, decision)
    }

    @Test
    fun `mail_tickle ohne Feature-Flag wird still ignoriert`() {
        val decision = TickleHandler.decide(
            data = mapOf("type" to "mail_tickle"),
            preconditions = ready.copy(pushEnabled = false)
        )
        assertEquals(TickleHandler.Decision.IGNORE_SILENTLY, decision)
    }

    @Test
    fun `mail_tickle ohne Mail-Konto wird still ignoriert`() {
        val decision = TickleHandler.decide(
            data = mapOf("type" to "mail_tickle"),
            preconditions = ready.copy(hasMailAccount = false)
        )
        assertEquals(TickleHandler.Decision.IGNORE_SILENTLY, decision)
    }

    @Test
    fun `weder Feature noch Konto - still ignoriert`() {
        val decision = TickleHandler.decide(
            data = mapOf("type" to "mail_tickle"),
            preconditions = TickleHandler.Preconditions(
                pushEnabled = false,
                hasMailAccount = false
            )
        )
        assertEquals(TickleHandler.Decision.IGNORE_SILENTLY, decision)
    }

    @Test
    fun `unbekannter Message-Type ergibt UNKNOWN_TYPE`() {
        val decision = TickleHandler.decide(
            data = mapOf("type" to "something_else"),
            preconditions = ready
        )
        assertEquals(TickleHandler.Decision.UNKNOWN_TYPE, decision)
    }

    @Test
    fun `fehlender Type-Key ergibt UNKNOWN_TYPE`() {
        val decision = TickleHandler.decide(
            data = emptyMap(),
            preconditions = ready
        )
        assertEquals(TickleHandler.Decision.UNKNOWN_TYPE, decision)
    }

    @Test
    fun `leerer Type-Wert ergibt UNKNOWN_TYPE`() {
        val decision = TickleHandler.decide(
            data = mapOf("type" to ""),
            preconditions = ready
        )
        assertEquals(TickleHandler.Decision.UNKNOWN_TYPE, decision)
    }

    @Test
    fun `isTickle erkennt nur die beiden bekannten Typen`() {
        assertTrue(TickleHandler.isTickle(mapOf("type" to "sync_tickle")))
        assertTrue(TickleHandler.isTickle(mapOf("type" to "mail_tickle")))
        assertFalse(TickleHandler.isTickle(mapOf("type" to "other")))
        assertFalse(TickleHandler.isTickle(emptyMap()))
    }

    @Test
    fun `High-Priority zugestellt als Normal gilt als herabgestuft`() {
        assertTrue(
            TickleHandler.isDeprioritized(
                originalPriority = RemoteMessage.PRIORITY_HIGH,
                deliveredPriority = RemoteMessage.PRIORITY_NORMAL
            )
        )
    }

    @Test
    fun `High-Priority zugestellt als High ist nicht herabgestuft`() {
        assertFalse(
            TickleHandler.isDeprioritized(
                originalPriority = RemoteMessage.PRIORITY_HIGH,
                deliveredPriority = RemoteMessage.PRIORITY_HIGH
            )
        )
    }

    @Test
    fun `Normal gesendet ist nie herabgestuft`() {
        assertFalse(
            TickleHandler.isDeprioritized(
                originalPriority = RemoteMessage.PRIORITY_NORMAL,
                deliveredPriority = RemoteMessage.PRIORITY_NORMAL
            )
        )
    }

    @Test
    fun `gespiegelte Prioritaets-Konstante passt zu Firebase`() {
        assertEquals(RemoteMessage.PRIORITY_HIGH, TickleHandler.PRIORITY_HIGH)
    }
}
