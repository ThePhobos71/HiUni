package de.transio.hiuni.core.notifications.lecture

import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.calendar.data.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Planung der Vorlesungs-Erinnerungen: nur Kurse mit gesetztem Vorlauf, nur Trigger im
 * rollierenden Fenster `(now, now + HORIZON]`, IDs im eigenen Bereich und je Vorkommen.
 */
class LectureReminderPlannerTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    /** Montag, 26.10.2026, 10:30 Ortszeit. */
    private val now: Instant = at(2026, 10, 26, 10, 30)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant()

    private fun lecture(
        id: Long,
        start: Instant,
        courseLsfId: String? = "4711",
        location: String? = "F101",
        sourceKind: String = CustomEventEntity.SOURCE_LSF_STUNDENPLAN,
        recurrenceRule: String? = null
    ) = CustomEventEntity(
        id = id,
        title = "Vorlesung $id",
        location = location,
        startTime = start,
        endTime = start.plus(Duration.ofMinutes(90)),
        sourceKind = sourceKind,
        recurrenceRule = recurrenceRule,
        courseLsfId = courseLsfId
    )

    private fun plan(events: List<CustomEventEntity>, minutes: Map<String, Int> = mapOf("4711" to 15)) =
        LectureReminderPlanner.plan(events, minutes, now, zone)

    @Test
    fun `Erinnerung liegt den eingestellten Vorlauf vor dem Beginn`() {
        val start = at(2026, 10, 26, 14, 15)
        val reminders = plan(listOf(lecture(1, start)))
        val reminder = reminders.single()
        assertEquals(start.minus(Duration.ofMinutes(15)), reminder.triggerAt)
        assertEquals(start, reminder.occurrenceStart)
        assertEquals("Vorlesung 1", reminder.title)
        assertEquals("Beginnt um 14:15 · F101", reminder.body)
        assertEquals(LectureReminderPlanner.reminderId(1, start), reminder.id)
    }

    @Test
    fun `je Kurs eigener Vorlauf, Kurse ohne Vorlauf und ohne Zuordnung bekommen nichts`() {
        val start = at(2026, 10, 26, 14, 0)
        val reminders = plan(
            listOf(
                lecture(1, start, courseLsfId = "4711"),
                lecture(2, start, courseLsfId = "0815"),
                lecture(3, start, courseLsfId = "9999"),
                lecture(4, start, courseLsfId = null)
            ),
            minutes = mapOf("4711" to 5, "0815" to 30)
        )
        assertEquals(
            mapOf(1L to start.minus(Duration.ofMinutes(5)), 2L to start.minus(Duration.ofMinutes(30))),
            reminders.associate { it.masterId to it.triggerAt }
        )
    }

    @Test
    fun `ohne eingestellten Vorlauf wird nichts geplant`() {
        assertTrue(plan(listOf(lecture(1, at(2026, 10, 26, 14, 0))), minutes = emptyMap()).isEmpty())
    }

    @Test
    fun `vergangene und schon laufende Vorkommen werden uebersprungen`() {
        val reminders = plan(
            listOf(
                // läuft schon
                lecture(1, at(2026, 10, 26, 10, 15)),
                // beginnt in 10 Min, 15-Min-Erinnerung wäre schon vorbei
                lecture(2, at(2026, 10, 26, 10, 40)),
                // Erinnerung genau jetzt: zählt als vorbei
                lecture(3, at(2026, 10, 26, 10, 45)),
                // Erinnerung in 1 Min
                lecture(4, at(2026, 10, 26, 10, 46))
            )
        )
        assertEquals(listOf(4L), reminders.map { it.masterId })
    }

    @Test
    fun `nur Trigger im rollierenden Fenster werden geplant`() {
        val horizonEnd = now.plus(LectureReminderPlanner.HORIZON)
        val inside = lecture(1, horizonEnd.plus(Duration.ofMinutes(15)))
        val outside = lecture(2, horizonEnd.plus(Duration.ofMinutes(16)))
        val farAway = lecture(3, now.plus(Duration.ofDays(10)))
        assertEquals(listOf(1L), plan(listOf(inside, outside, farAway)).map { it.masterId })
    }

    @Test
    fun `wiederkehrende Vorlesung bekommt je Vorkommen im Fenster eine eigene Erinnerung`() {
        val daily = lecture(
            7,
            start = at(2026, 10, 19, 12, 0),
            recurrenceRule = RecurrenceRule(
                freq = RecurrenceRule.Freq.DAILY,
                interval = 1,
                byDays = null,
                until = LocalDate.of(2027, 2, 1)
            ).toJsonString()
        )
        val reminders = plan(listOf(daily))
        // Fenster endet am 29.10. um 10:30, der Trigger 11:45 an dem Tag liegt schon dahinter.
        assertEquals(
            listOf(at(2026, 10, 26, 12, 0), at(2026, 10, 27, 12, 0), at(2026, 10, 28, 12, 0)),
            reminders.map { it.occurrenceStart }
        )
        assertEquals(3, reminders.map { it.id }.toSet().size)
        assertTrue(reminders.all { it.masterId == 7L })
    }

    @Test
    fun `woechentliche Vorlesung wird expandiert`() {
        val weekly = lecture(
            8,
            start = at(2026, 9, 29, 8, 30),
            recurrenceRule = RecurrenceRule(
                freq = RecurrenceRule.Freq.WEEKLY,
                interval = 1,
                byDays = listOf(DayOfWeek.TUESDAY),
                until = LocalDate.of(2027, 2, 1)
            ).toJsonString()
        )
        val reminder = plan(listOf(weekly)).single()
        assertEquals(at(2026, 10, 27, 8, 15), reminder.triggerAt)
    }

    @Test
    fun `nur LSF-Stundenplan-Termine zaehlen`() {
        val start = at(2026, 10, 26, 14, 0)
        val reminders = plan(listOf(lecture(1, start, sourceKind = CustomEventEntity.SOURCE_USER)))
        assertTrue(reminders.isEmpty())
    }

    @Test
    fun `IDs liegen im eigenen Bereich, stabil und je Vorkommen verschieden`() {
        val a = LectureReminderPlanner.reminderId(1, at(2026, 10, 26, 14, 0))
        val b = LectureReminderPlanner.reminderId(1, at(2026, 10, 27, 14, 0))
        val c = LectureReminderPlanner.reminderId(2, at(2026, 10, 26, 14, 0))
        assertEquals(a, LectureReminderPlanner.reminderId(1, at(2026, 10, 26, 14, 0)))
        assertNotEquals(a, b)
        assertNotEquals(a, c)
        for (id in listOf(a, b, c, LectureReminderPlanner.reminderId(Long.MAX_VALUE, Instant.EPOCH))) {
            assertTrue(LectureReminderPlanner.isReminderId(id))
            assertTrue(id in LectureReminderPlanner.ID_MIN..LectureReminderPlanner.ID_MAX)
        }
        // Grenzen: Bib endet bei ca. -768,4 Mio, Live-Vorlesung beginnt bei -800 Mio
        // (Weck-Alarm -799.999.999).
        assertTrue(LectureReminderPlanner.ID_MAX < -768_435_455L)
        assertTrue(LectureReminderPlanner.ID_MIN > LiveLecturePlanner.NOTIFICATION_ID_MAX + 1L)
        assertFalse(LectureReminderPlanner.isReminderId(-1L))
        assertFalse(LectureReminderPlanner.isReminderId(LiveLecturePlanner.NOTIFICATION_ID_MAX.toLong()))
        // Passt verlustfrei in den Int-Request-Code des NotificationSchedulers.
        assertEquals(a, a.toInt().toLong())
    }

    @Test
    fun `Inhaltszeile ohne Raum nur mit Uhrzeit`() {
        val start = at(2026, 10, 26, 14, 15)
        val reminder = plan(listOf(lecture(1, start, location = " "))).single()
        assertEquals("Beginnt um 14:15", reminder.body)
    }
}
