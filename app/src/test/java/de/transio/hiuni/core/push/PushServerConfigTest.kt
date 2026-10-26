package de.transio.hiuni.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushServerConfigTest {

    private fun resolve(overrideUrl: String = "", overrideKey: String = "") =
        PushServerConfig.resolve(
            overrideUrl = overrideUrl,
            overrideKey = overrideKey,
            defaultUrl = "https://tickle.transio.de",
            defaultKey = "default-key"
        )

    @Test
    fun `ohne Override gilt der Standard`() {
        val r = resolve()
        assertEquals("https://tickle.transio.de", r.serverUrl)
        assertEquals("default-key", r.apiKey)
        assertTrue(r.isComplete)
    }

    @Test
    fun `Override gewinnt und wird getrimmt`() {
        val r = resolve(overrideUrl = "  https://push.example.org/ ", overrideKey = " own-key ")
        assertEquals("https://push.example.org", r.serverUrl)
        assertEquals("own-key", r.apiKey)
    }

    @Test
    fun `nur URL-Override - Standard-Key bleibt`() {
        val r = resolve(overrideUrl = "https://push.example.org")
        assertEquals("default-key", r.apiKey)
    }

    @Test
    fun `Build ohne Standard-Key ist unvollstaendig`() {
        val r = PushServerConfig.resolve("", "", defaultUrl = "https://tickle.transio.de", defaultKey = "")
        assertFalse(r.isComplete)
    }
}
