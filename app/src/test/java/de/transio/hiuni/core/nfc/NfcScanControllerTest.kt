package de.transio.hiuni.core.nfc

import android.nfc.Tag
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kaltstart per Karten-Auflegen: MainActivity reicht den Tag in onCreate weiter,
 * das MensaCardViewModel sammelt [NfcScanController.tags] aber erst nach der
 * Navigation. Der Tag muss bis dahin warten, darf aber nicht ewig liegen bleiben.
 */
class NfcScanControllerTest {

    private val tag = mockk<Tag>()

    @Test
    fun `Kaltstart-Tag kommt an, auch wenn noch niemand sammelt`() = runTest {
        val controller = NfcScanController()
        controller.onUnsolicitedTag(tag)

        assertTrue(controller.scanning.value)
        assertSame(tag, withTimeoutOrNull(1_000) { controller.tags.first() })
    }

    @Test
    fun `Kaltstart-Tag wird nur einmal ausgeliefert`() = runTest {
        val controller = NfcScanController()
        controller.onUnsolicitedTag(tag)
        controller.tags.first()

        assertNull(withTimeoutOrNull(1_000) { controller.tags.first() })
    }

    @Test
    fun `veralteter Kaltstart-Tag wird verworfen`() = runTest {
        var nowMs = 0L
        val controller = NfcScanController().apply { clockMs = { nowMs } }
        controller.onUnsolicitedTag(tag)
        nowMs += NfcScanController.PENDING_TAG_MAX_AGE_MS + 1

        assertNull(withTimeoutOrNull(1_000) { controller.tags.first() })
    }

    @Test
    fun `normaler Tag ohne aktiven Scan wird ignoriert`() = runTest {
        val controller = NfcScanController()
        controller.onTagReceived(tag)

        assertNull(withTimeoutOrNull(1_000) { controller.tags.first() })
    }
}
