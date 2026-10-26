package de.transio.hiuni.feature.todos.data

import de.transio.hiuni.core.common.DateTimeFormats
import de.transio.hiuni.core.sync.ExamReminderScheduler
import de.transio.hiuni.core.sync.LearnwebAssignmentReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Reine Entscheidungslogik der Aufgaben-Erinnerung: Fälligkeit → Trigger,
 * Vergangenheit/erledigt → kein Reminder, ID-Raum getrennt von anderen Quellen.
 */
class TodoReminderPlannerTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun todo(
        id: Long = 7L,
        due: LocalDate? = LocalDate.of(2026, 11, 3),
        done: Boolean = false
    ) = TodoEntity(id = id, title = "Übungsblatt 3", dueDate = due, isDone = done)

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant()

    @Test
    fun `Fälligkeit in der Zukunft erinnert um 9 Uhr am Fälligkeitstag`() {
        val trigger = TodoReminderPlanner.triggerAt(todo(), now = at(2026, 10, 26, 12), zone = zone)
        assertEquals(at(2026, 11, 3, 9), trigger)
    }

    @Test
    fun `heute fällig vor 9 Uhr plant noch fuer heute`() {
        val today = LocalDate.of(2026, 10, 26)
        val trigger = TodoReminderPlanner.triggerAt(todo(due = today), now = at(2026, 10, 26, 7, 30), zone = zone)
        assertEquals(at(2026, 10, 26, 9), trigger)
    }

    @Test
    fun `heute fällig ab 9 Uhr plant keinen Reminder`() {
        val today = LocalDate.of(2026, 10, 26)
        assertNull(TodoReminderPlanner.triggerAt(todo(due = today), now = at(2026, 10, 26, 9), zone = zone))
        assertNull(TodoReminderPlanner.triggerAt(todo(due = today), now = at(2026, 10, 26, 14), zone = zone))
    }

    @Test
    fun `vergangene Fälligkeit plant keinen Reminder`() {
        val trigger = TodoReminderPlanner.triggerAt(
            todo(due = LocalDate.of(2026, 10, 20)),
            now = at(2026, 10, 26, 8),
            zone = zone
        )
        assertNull(trigger)
    }

    @Test
    fun `erledigte Aufgabe plant keinen Reminder`() {
        assertNull(TodoReminderPlanner.triggerAt(todo(done = true), now = at(2026, 10, 26, 8), zone = zone))
    }

    @Test
    fun `Aufgabe ohne Fälligkeit plant keinen Reminder`() {
        assertNull(TodoReminderPlanner.triggerAt(todo(due = null), now = at(2026, 10, 26, 8), zone = zone))
    }

    @Test
    fun `9 Uhr gilt in Ortszeit auch ueber die Zeitumstellung hinweg`() {
        // 25.10.2026 ist Umstellung auf Winterzeit, 9 Uhr bleibt 9 Uhr lokal.
        val trigger = TodoReminderPlanner.triggerAt(
            todo(due = LocalDate.of(2026, 10, 26)),
            now = at(2026, 10, 24, 12),
            zone = zone
        )
        assertEquals(9, trigger!!.atZone(zone).hour)
    }

    @Test
    fun `Reminder-ID ist negativ und eindeutig pro Aufgabe`() {
        val a = TodoReminderPlanner.reminderId(1L)!!
        val b = TodoReminderPlanner.reminderId(2L)!!
        assertEquals(-1_000_000_001L, a)
        assertNotEquals(a, b)
        // Nicht der Sentinel des NotificationReceivers bzw. die Test-Mitteilung (-1).
        assertNotEquals(-1L, a)
    }

    @Test
    fun `Reminder-ID kollidiert nicht mit Kalender, Klausur, Learnweb, Noten oder Kurs-Pushes`() {
        val id = TodoReminderPlanner.reminderId(1L)!!
        // Kalender-Events sowie Klausur-/Learnweb-Reminder sind positiv.
        assertTrue(id < 0)
        assertTrue(ExamReminderScheduler.EXAM_ID_OFFSET > 0)
        assertTrue(LearnwebAssignmentReminderScheduler.LEARNWEB_ID_OFFSET > 0)
        // Noten-/Kurs-Pushes liegen in [-200_000_000 - 0x0FFFFFFF, -100_000_000].
        val lowestHashedPush = -200_000_000L - 0x0FFFFFFF
        assertTrue(id < lowestHashedPush)
    }

    @Test
    fun `Reminder-ID passt in den Int-Bereich oder wird uebersprungen`() {
        val maxFitting = TodoReminderPlanner.TODO_ID_BASE - Int.MIN_VALUE.toLong()
        assertEquals(Int.MIN_VALUE.toLong(), TodoReminderPlanner.reminderId(maxFitting))
        assertNull(TodoReminderPlanner.reminderId(maxFitting + 1))
    }

    @Test
    fun `nicht persistierte Aufgabe bekommt keine ID`() {
        assertNull(TodoReminderPlanner.reminderId(0L))
    }

    @Test
    fun `Titel und Body nennen Aufgabe und Datum`() {
        val due = LocalDate.of(2026, 10, 27)
        val t = todo(due = due)
        assertEquals("Heute fällig: Übungsblatt 3", TodoReminderPlanner.title(t))
        assertEquals(
            "Offene Aufgabe · ${due.format(DateTimeFormats.dayShortNoComma)}",
            TodoReminderPlanner.body(t)
        )
    }

    @Test
    fun `todoId laesst sich aus der Reminder-ID zurueckrechnen`() {
        for (todoId in listOf(1L, 42L, 1_000_000L)) {
            assertEquals(todoId, TodoReminderPlanner.todoIdFromReminderId(TodoReminderPlanner.reminderId(todoId)!!))
        }
    }

    @Test
    fun `fremde IDs ergeben keine todoId`() {
        assertNull(TodoReminderPlanner.todoIdFromReminderId(TodoReminderPlanner.TODO_ID_BASE))
        assertNull(TodoReminderPlanner.todoIdFromReminderId(-2L))
        assertNull(TodoReminderPlanner.todoIdFromReminderId(12L))
        assertNull(TodoReminderPlanner.todoIdFromReminderId(1_000_000_001L))
    }
}
