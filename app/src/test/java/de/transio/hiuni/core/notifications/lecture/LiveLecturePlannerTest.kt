package de.transio.hiuni.core.notifications.lecture

import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.calendar.data.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Reine Planungslogik der Laufende-Vorlesung-Mitteilung: welche Vorlesungen laufen
 * gerade, wann ist der nächste Weckzeitpunkt, welche IDs und Timeouts entstehen.
 * Alle Zeiten in Europe/Berlin, damit die Tests nicht von der CI-Zone abhängen.
 */
class LiveLecturePlannerTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    /** Montag, 26.10.2026, 10:30 Ortszeit. */
    private val now: Instant = at(2026, 10, 26, 10, 30)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant()

    private fun lecture(
        id: Long,
        start: Instant,
        end: Instant = start.plus(Duration.ofMinutes(90)),
        title: String = "Vorlesung $id",
        location: String? = "F101",
        sourceKind: String = CustomEventEntity.SOURCE_LSF_STUNDENPLAN,
        recurrenceRule: String? = null
    ) = CustomEventEntity(
        id = id,
        title = title,
        location = location,
        startTime = start,
        endTime = end,
        sourceKind = sourceKind,
        recurrenceRule = recurrenceRule
    )

    private fun plan(vararg events: CustomEventEntity, dismissed: Set<String> = emptySet()) =
        LiveLecturePlanner.plan(events.toList(), now, zone, dismissed)

    // --- Laufend-Erkennung --------------------------------------------------------

    @Test
    fun `laufende LSF-Vorlesung wird erkannt`() {
        val running = lecture(1, start = at(2026, 10, 26, 10, 15), end = at(2026, 10, 26, 11, 45))
        val result = plan(running)
        assertEquals(listOf(1L), result.running.map { it.masterId })
        assertEquals(at(2026, 10, 26, 11, 45), result.running.single().end)
        assertEquals("F101", result.running.single().location)
    }

    @Test
    fun `Vorlesung, die genau jetzt endet, laeuft nicht mehr`() {
        val ended = lecture(1, start = at(2026, 10, 26, 9, 0), end = now)
        assertTrue(plan(ended).running.isEmpty())
    }

    @Test
    fun `Vorlesung, die genau jetzt beginnt, laeuft`() {
        val starting = lecture(1, start = now)
        assertEquals(listOf(1L), plan(starting).running.map { it.masterId })
    }

    @Test
    fun `Alarm, der knapp vor dem Start feuert, zeigt die Vorlesung schon an`() {
        // Toleranz gegen früh feuernde Alarme: 30 s vor Start zählt schon als laufend,
        // sonst würde der Planer den Weckzeitpunkt erneut auf denselben Start legen.
        val soon = lecture(1, start = now.plusSeconds(30))
        val result = plan(soon)
        assertEquals(listOf(1L), result.running.map { it.masterId })
        assertNotEquals(soon.startTime, result.nextWakeAt)
    }

    @Test
    fun `eigene Termine, Pins und Learnweb-Deadlines zaehlen nicht`() {
        val start = at(2026, 10, 26, 10, 0)
        val result = plan(
            lecture(1, start, sourceKind = CustomEventEntity.SOURCE_USER),
            lecture(2, start, sourceKind = CustomEventEntity.SOURCE_MENSA_PIN),
            lecture(3, start, sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ICAL),
            lecture(4, start, sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT),
            lecture(5, start, sourceKind = CustomEventEntity.SOURCE_BIB_BOOKING)
        )
        assertTrue(result.running.isEmpty())
        assertFalse(result.nextWakeIsLectureStart)
    }

    @Test
    fun `ueberlange und kaputte Eintraege werden ignoriert`() {
        val allDay = lecture(1, start = at(2026, 10, 26, 0, 0), end = at(2026, 10, 27, 0, 0))
        val broken = lecture(2, start = at(2026, 10, 26, 10, 0), end = at(2026, 10, 26, 10, 0))
        assertTrue(plan(allDay, broken).running.isEmpty())
    }

    @Test
    fun `weggewischte Vorlesung wird nicht erneut gezeigt`() {
        val running = lecture(1, start = at(2026, 10, 26, 10, 15))
        val key = LiveLecturePlanner.plan(listOf(running), now, zone).running.single().key
        assertTrue(plan(running, dismissed = setOf(key)).running.isEmpty())
    }

    // --- Wiederkehrende Termine ------------------------------------------------------

    @Test
    fun `wiederkehrende Vorlesung wird expandiert`() {
        // Master liegt Wochen zurück, die heutige Occurrence läuft gerade.
        val master = lecture(
            7,
            start = at(2026, 9, 28, 10, 15),
            end = at(2026, 9, 28, 11, 45),
            recurrenceRule = RecurrenceRule(
                freq = RecurrenceRule.Freq.WEEKLY,
                interval = 1,
                byDays = listOf(DayOfWeek.MONDAY),
                until = LocalDate.of(2027, 2, 1)
            ).toJsonString()
        )
        val result = plan(master)
        val occ = result.running.single()
        assertEquals(7L, occ.masterId)
        assertEquals(at(2026, 10, 26, 10, 15), occ.start)
        assertEquals(at(2026, 10, 26, 11, 45), occ.end)
    }

    // --- Weckzeitpunkt ---------------------------------------------------------------

    @Test
    fun `naechster Weckzeitpunkt ist der naechste Vorlesungsbeginn`() {
        val later = lecture(2, start = at(2026, 10, 26, 14, 0))
        val tomorrow = lecture(3, start = at(2026, 10, 27, 8, 30))
        val result = plan(tomorrow, later)
        assertEquals(at(2026, 10, 26, 14, 0), result.nextWakeAt)
        assertTrue(result.nextWakeIsLectureStart)
    }

    @Test
    fun `Vorlesungen jenseits von 48 Stunden werden nicht geweckt`() {
        val inThreeDays = lecture(2, start = at(2026, 10, 29, 10, 0))
        val result = plan(inThreeDays)
        assertTrue(result.running.isEmpty())
        // Stattdessen Nachplanung am Horizont-Ende.
        assertEquals(now.plus(LiveLecturePlanner.HORIZON), result.nextWakeAt)
        assertFalse(result.nextWakeIsLectureStart)
    }

    @Test
    fun `Vorlesung knapp innerhalb von 48 Stunden wird geweckt`() {
        val start = now.plus(Duration.ofHours(47))
        val result = plan(lecture(2, start = start))
        assertEquals(start, result.nextWakeAt)
        assertTrue(result.nextWakeIsLectureStart)
    }

    @Test
    fun `laufende Vorlesung ist nicht der naechste Weckzeitpunkt`() {
        val running = lecture(1, start = at(2026, 10, 26, 10, 15))
        val next = lecture(2, start = at(2026, 10, 26, 12, 0))
        val result = plan(running, next)
        assertEquals(at(2026, 10, 26, 12, 0), result.nextWakeAt)
    }

    @Test
    fun `occurrences liefert alle Vorkommen im Fenster sortiert`() {
        val a = lecture(1, start = at(2026, 10, 27, 8, 0))
        val b = lecture(2, start = at(2026, 10, 26, 10, 15))
        val c = lecture(3, start = at(2026, 10, 30, 8, 0))
        val occ = LiveLecturePlanner.occurrences(listOf(a, b, c), now, zone)
        assertEquals(listOf(2L, 1L), occ.map { it.masterId })
    }

    // --- Timeout, IDs, Texte ---------------------------------------------------------

    @Test
    fun `Timeout ist die Restlaufzeit bis zum Ende`() {
        val occ = plan(lecture(1, start = at(2026, 10, 26, 10, 15), end = at(2026, 10, 26, 11, 45)))
            .running.single()
        assertEquals(Duration.ofMinutes(75).toMillis(), LiveLecturePlanner.timeoutMillis(occ, now))
    }

    @Test
    fun `Timeout ist nie null oder negativ`() {
        val occ = LiveLectureOccurrence(1, "X", null, now.minusSeconds(60), now.minusSeconds(1))
        assertTrue(LiveLecturePlanner.timeoutMillis(occ, now) > 0)
    }

    @Test
    fun `Notification-IDs liegen im eigenen Bereich`() {
        for (id in listOf(0L, 1L, 42L, 123_456L, 199_999_999L, 200_000_000L, Long.MAX_VALUE, -5L)) {
            val systemId = LiveLecturePlanner.notificationId(id)
            assertTrue(
                "ID $systemId für $id außerhalb des Bereichs",
                systemId in LiveLecturePlanner.NOTIFICATION_ID_MIN..LiveLecturePlanner.NOTIFICATION_ID_MAX
            )
        }
        // Bereich kollidiert weder mit Bib (ab ca. -770 Mio) noch mit Aufgaben (ab -1 Mrd).
        assertTrue(LiveLecturePlanner.NOTIFICATION_ID_MAX < -768_435_455)
        assertTrue(LiveLecturePlanner.NOTIFICATION_ID_MIN > -1_000_000_000)
    }

    @Test
    fun `Notification-IDs sind je Termin verschieden und stabil`() {
        assertNotEquals(LiveLecturePlanner.notificationId(1), LiveLecturePlanner.notificationId(2))
        assertEquals(LiveLecturePlanner.notificationId(17), LiveLecturePlanner.notificationId(17))
        assertEquals(LiveLecturePlanner.NOTIFICATION_ID_MAX - 1, LiveLecturePlanner.notificationId(1))
    }

    @Test
    fun `Inhaltszeile zeigt Raum und Ende`() {
        val occ = LiveLectureOccurrence(1, "Analysis", "F101", now, at(2026, 10, 26, 11, 45))
        assertEquals("F101 · bis 11:45", LiveLecturePlanner.contentText(occ, zone))
    }

    @Test
    fun `Inhaltszeile ohne Raum zeigt nur das Ende`() {
        val occ = LiveLectureOccurrence(1, "Analysis", "  ", now, at(2026, 10, 26, 11, 45))
        assertEquals("bis 11:45", LiveLecturePlanner.contentText(occ, zone))
    }

    @Test
    fun `abgelaufene Wegwisch-Merker werden aufgeraeumt`() {
        val old = LiveLectureOccurrence(1, "A", null, now.minusSeconds(7200), now.minusSeconds(60))
        val current = LiveLectureOccurrence(2, "B", null, now.minusSeconds(60), now.plusSeconds(600))
        val pruned = LiveLecturePlanner.pruneDismissed(setOf(old.key, current.key, "kaputt"), now)
        assertEquals(setOf(current.key), pruned)
    }

    @Test
    fun `kein Weckzeitpunkt-Sonderfall ohne Ereignisse`() {
        val result = plan()
        assertTrue(result.running.isEmpty())
        assertEquals(now.plus(LiveLecturePlanner.HORIZON), result.nextWakeAt)
        assertNull(result.running.firstOrNull())
    }
}
