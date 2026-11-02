package de.transio.hiuni.core.notifications

import android.content.Context
import android.content.Intent
import de.transio.hiuni.core.notifications.NotificationActionSpec.ArchiveMail
import de.transio.hiuni.core.notifications.NotificationActionSpec.BookBibRoom
import de.transio.hiuni.core.notifications.NotificationActionSpec.CompleteTodo
import de.transio.hiuni.core.notifications.NotificationActionSpec.MarkMailsRead
import de.transio.hiuni.core.notifications.NotificationActionSpec.SnoozeEvent
import de.transio.hiuni.core.notifications.NotificationActionSpec.SnoozeTodo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/**
 * Hin- und Rückweg einer Aktion über den Intent zum [NotificationActionReceiver]
 * und dessen Registrierung im Manifest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationActionIntentsTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun roundTrip(spec: NotificationActionSpec, systemId: Int) =
        NotificationActionIntents.parse(NotificationActionIntents.build(context, spec, systemId))

    @Test
    fun `jede Aktion uebersteht den Weg ueber den Intent samt systemId`() {
        val specs = listOf(
            MarkMailsRead(listOf(1L, 2L, 3L)),
            ArchiveMail(42L),
            CompleteTodo(7L),
            SnoozeTodo(7L),
            SnoozeEvent(12L, "Mathe 2", "Gleich geht's los"),
            SnoozeEvent(12L, "Mathe 2", null),
            BookBibRoom(102, LocalDate.of(2026, 10, 26), LocalTime.of(14, 0), LocalTime.of(16, 0))
        )
        for (spec in specs) {
            assertEquals(NotificationActionIntents.Parsed(spec, -2), roundTrip(spec, -2))
        }
    }

    @Test
    fun `Intent zielt explizit auf den Aktions-Receiver`() {
        val intent = NotificationActionIntents.build(context, ArchiveMail(1L), -2)

        assertEquals(NotificationActionReceiver::class.java.name, intent.component?.className)
    }

    @Test
    fun `Aktionen derselben Notification sind fuer PendingIntent unterscheidbar`() {
        val read = NotificationActionIntents.build(context, MarkMailsRead(listOf(1L)), -2)
        val archive = NotificationActionIntents.build(context, ArchiveMail(1L), -2)
        val otherNotification = NotificationActionIntents.build(context, SnoozeTodo(1L), -1_000_000_005)
        val sameTypeOtherNotification = NotificationActionIntents.build(context, SnoozeTodo(2L), -1_000_000_006)

        assertFalse(read.filterEquals(archive))
        assertFalse(otherNotification.filterEquals(sameTypeOtherNotification))
        assertNotEquals(read.data, archive.data)
    }

    @Test
    fun `fremde oder unvollstaendige Intents werden verworfen`() {
        assertNull(NotificationActionIntents.parse(Intent("de.evil.FAKE")))
        assertNull(NotificationActionIntents.parse(Intent(NotificationActionIntents.ACTION_ARCHIVE_MAIL)))
        assertNull(NotificationActionIntents.parse(Intent(NotificationActionIntents.ACTION_COMPLETE_TODO)))
        assertNull(NotificationActionIntents.parse(Intent(NotificationActionIntents.ACTION_BOOK_BIB_ROOM)))
    }

    @Test
    fun `Receiver ist im Manifest registriert und nicht exportiert`() {
        val intent = NotificationActionIntents.build(context, CompleteTodo(1L), 1)
        val matches = context.packageManager.queryBroadcastReceivers(intent, 0)
            .filter { it.activityInfo.name == NotificationActionReceiver::class.java.name }

        assertEquals(1, matches.size)
        assertFalse(matches.single().activityInfo.exported)
    }
}
