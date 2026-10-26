package de.transio.hiuni.core.notifications

import de.transio.hiuni.core.notifications.NotificationActionSpec.ArchiveMail
import de.transio.hiuni.core.notifications.NotificationActionSpec.CompleteTodo
import de.transio.hiuni.core.notifications.NotificationActionSpec.MarkMailsRead
import de.transio.hiuni.core.notifications.NotificationActionSpec.SnoozeEvent
import de.transio.hiuni.core.notifications.NotificationActionSpec.SnoozeTodo
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.feature.todos.data.TodoReminderPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Welche Aktions-Buttons eine OS-Notification je Quelle bekommt. */
class NotificationActionPlannerTest {

    @Test
    fun `eine neue Mail bekommt Als gelesen und Archivieren fuer genau diese Mail`() {
        val actions = NotificationActionPlanner.mailActions(newUnreadUids = listOf(42L), mailLockEnabled = false)

        assertEquals(listOf(MarkMailsRead(listOf(42L)), ArchiveMail(42L)), actions)
        assertEquals(listOf("Als gelesen", "Archivieren"), actions.map { it.label })
    }

    @Test
    fun `mehrere neue Mails bekommen nur Alle als gelesen`() {
        val actions = NotificationActionPlanner.mailActions(listOf(1L, 2L, 3L), mailLockEnabled = false)

        assertEquals(listOf(MarkMailsRead(listOf(1L, 2L, 3L))), actions)
        assertEquals("Alle als gelesen", actions.single().label)
    }

    @Test
    fun `Mail-Aktionen verlangen ein entsperrtes Geraet`() {
        val actions = NotificationActionPlanner.mailActions(listOf(1L), mailLockEnabled = false) +
            NotificationActionPlanner.mailActions(listOf(1L, 2L), mailLockEnabled = false)

        assertTrue(actions.all { it.requiresUnlock })
    }

    @Test
    fun `aktive Mail-Sperre zeigt gar keine Mail-Aktionen`() {
        assertTrue(NotificationActionPlanner.mailActions(listOf(1L), mailLockEnabled = true).isEmpty())
        assertTrue(NotificationActionPlanner.mailActions(listOf(1L, 2L), mailLockEnabled = true).isEmpty())
    }

    @Test
    fun `ohne neue Mails keine Aktionen`() {
        assertTrue(NotificationActionPlanner.mailActions(emptyList(), mailLockEnabled = false).isEmpty())
    }

    @Test
    fun `Aufgaben-Erinnerung bekommt Erledigt und Spaeter fuer die Aufgabe aus der Reminder-ID`() {
        val reminderId = TodoReminderPlanner.reminderId(7L)!!

        val actions = NotificationActionPlanner.reminderActions(
            kind = NotificationKind.TODO,
            eventId = reminderId,
            title = "Heute fällig: Blatt 3",
            body = "Offene Aufgabe"
        )

        assertEquals(listOf(CompleteTodo(7L), SnoozeTodo(7L)), actions)
        assertEquals(listOf("Erledigt", "Später"), actions.map { it.label })
        assertTrue(actions.none { it.requiresUnlock })
    }

    @Test
    fun `TODO mit fremder ID bekommt keine Aktionen`() {
        val actions = NotificationActionPlanner.reminderActions(NotificationKind.TODO, 5L, "x", null)

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `Kalender-Erinnerung bekommt Spaeter mit Titel und Text zum erneuten Anzeigen`() {
        val actions = NotificationActionPlanner.reminderActions(
            kind = NotificationKind.EVENT,
            eventId = 12L,
            title = "Mathe 2",
            body = "Gleich geht's los"
        )

        assertEquals(listOf(SnoozeEvent(12L, "Mathe 2", "Gleich geht's los")), actions)
        assertEquals("Später", actions.single().label)
        assertFalse(actions.single().requiresUnlock)
    }

    @Test
    fun `Klausur- und Learnweb-Reminder sowie synthetische IDs bekommen keine Aktionen`() {
        assertTrue(NotificationActionPlanner.reminderActions(NotificationKind.EXAM, 1_000_000_001L, "x", null).isEmpty())
        assertTrue(NotificationActionPlanner.reminderActions(NotificationKind.LEARNWEB, 2_000_000_001L, "x", null).isEmpty())
        assertTrue(NotificationActionPlanner.reminderActions(NotificationKind.EVENT, -1L, "x", null).isEmpty())
        assertTrue(NotificationActionPlanner.reminderActions(NotificationKind.EVENT, 0L, "x", null).isEmpty())
    }

    @Test
    fun `Spaeter verschiebt Aufgaben um eine Stunde und Termine um zehn Minuten`() {
        val now = Instant.parse("2026-10-26T08:00:00Z")

        assertEquals(now.plus(Duration.ofHours(1)), NotificationActionPlanner.snoozeUntil(SnoozeTodo(1L), now))
        assertEquals(now.plus(Duration.ofMinutes(10)), NotificationActionPlanner.snoozeUntil(SnoozeEvent(1L, "t", null), now))
    }

    @Test
    fun `Request-Codes sind je Aktion einer Notification verschieden`() {
        val specs = listOf(
            MarkMailsRead(listOf(1L)),
            ArchiveMail(1L),
            CompleteTodo(1L),
            SnoozeTodo(1L),
            SnoozeEvent(1L, "t", null)
        )
        val codes = specs.map { NotificationActionPlanner.requestCode(systemId = -2, spec = it) }

        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `Kategorie einer Aktion folgt ihrer Quelle`() {
        assertEquals(NotificationKind.MAIL, NotificationActionPlanner.kindOf(ArchiveMail(1L)))
        assertEquals(NotificationKind.MAIL, NotificationActionPlanner.kindOf(MarkMailsRead(listOf(1L))))
        assertEquals(NotificationKind.TODO, NotificationActionPlanner.kindOf(CompleteTodo(1L)))
        assertEquals(NotificationKind.TODO, NotificationActionPlanner.kindOf(SnoozeTodo(1L)))
        assertEquals(NotificationKind.EVENT, NotificationActionPlanner.kindOf(SnoozeEvent(1L, "t", null)))
    }
}
