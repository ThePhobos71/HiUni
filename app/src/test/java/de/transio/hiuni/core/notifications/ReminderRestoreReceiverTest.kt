package de.transio.hiuni.core.notifications

import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Prüft, dass der [ReminderRestoreReceiver] im (gemergten) Manifest für genau die
 * System-Broadcasts registriert ist, nach denen Reminder neu geplant werden müssen,
 * und nicht von fremden Apps angesprochen werden kann (exported=false).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReminderRestoreReceiverTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun receiversFor(action: String) = context.packageManager
        .queryBroadcastReceivers(Intent(action).setPackage(context.packageName), 0)
        .filter { it.activityInfo.name == ReminderRestoreReceiver::class.java.name }

    @Test
    fun `Receiver ist fuer Reboot, App-Update, Zeit- und Zonenwechsel registriert`() {
        for (action in ReminderRestoreReceiver.HANDLED_ACTIONS) {
            val matches = receiversFor(action)
            assertEquals("Receiver fehlt für $action", 1, matches.size)
            assertFalse("Receiver darf nicht exportiert sein", matches.single().activityInfo.exported)
        }
    }

    @Test
    fun `Aktionsliste deckt die relevanten System-Broadcasts ab`() {
        val expected = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
        assertEquals(expected, ReminderRestoreReceiver.HANDLED_ACTIONS)
    }

    @Test
    fun `LOCKED_BOOT_COMPLETED und fremde Aktionen werden nicht behandelt`() {
        assertFalse(ReminderRestoreReceiver.handles(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertFalse(ReminderRestoreReceiver.handles("de.evil.FAKE"))
        assertFalse(ReminderRestoreReceiver.handles(null))
        assertTrue(receiversFor(Intent.ACTION_LOCKED_BOOT_COMPLETED).isEmpty())
        assertTrue(ReminderRestoreReceiver.handles(Intent.ACTION_BOOT_COMPLETED))
    }
}
