package de.transio.hiuni.feature.exams.studyplan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Tests für den reinen Lernplan-Planer [StudyPlanner].
 *
 * Referenz: Montag, 2. November 2026, 08:00 Uhr in Berlin. Standard-Start ist
 * morgen (Dienstag, 3. November).
 */
class StudyPlannerTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val today: LocalDate = LocalDate.of(2026, 11, 2)
    private val tomorrow: LocalDate = today.plusDays(1)
    private val now: Instant = at(today, 8, 0)
    private val defaults = StudyPlanSettings()

    private fun at(date: LocalDate, hour: Int, minute: Int): Instant =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun exam(
        id: Long,
        date: LocalDate,
        credits: Int? = 5,
        hour: Int = 10
    ) = StudyPlanExam(
        examId = id,
        title = "Modul $id",
        examStart = LocalDateTime.of(date, LocalTime.of(hour, 0)),
        credits = credits
    )

    /** Vorlesungen Mo bis Fr, 10:00 bis 12:00 und 14:00 bis 15:30, im Zeitraum [from, to). */
    private fun lectures(from: LocalDate, to: LocalDate): List<BusyInterval> {
        val out = mutableListOf<BusyInterval>()
        var day = from
        while (day.isBefore(to)) {
            if (day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY) {
                out += BusyInterval(at(day, 10, 0), at(day, 12, 0))
                out += BusyInterval(at(day, 14, 0), at(day, 15, 30))
            }
            day = day.plusDays(1)
        }
        return out
    }

    private fun plan(
        exams: List<StudyPlanExam>,
        busy: List<BusyInterval> = emptyList(),
        settings: StudyPlanSettings = defaults,
        startDate: LocalDate = tomorrow,
        now: Instant = this.now
    ) = StudyPlanner.plan(exams, busy, settings, startDate, now, zone)

    private fun Instant.localDate(): LocalDate = atZone(zone).toLocalDate()
    private fun Instant.localTime(): LocalTime = atZone(zone).toLocalTime()

    private fun assertNoOverlaps(blocks: List<PlannedStudyBlock>) {
        val sorted = blocks.sortedBy { it.start }
        sorted.zipWithNext().forEach { (a, b) ->
            assertFalse("Blöcke überlappen: $a / $b", b.start.isBefore(a.end))
        }
    }

    // ── Lücken und Puffer ────────────────────────────────────────────────────

    @Test
    fun `Bloecke liegen in den Luecken um Vorlesungen und halten den Puffer ein`() {
        val examDate = LocalDate.of(2026, 11, 20)
        val busy = lectures(today, examDate)
        val result = plan(listOf(exam(1, examDate, credits = 10)), busy)

        assertTrue(result.blocks.isNotEmpty())
        val buffer = Duration.ofMinutes(defaults.bufferMinutes.toLong())
        for (block in result.blocks) {
            for (b in busy) {
                val clear = !block.end.isAfter(b.start.minus(buffer)) ||
                    !block.start.isBefore(b.end.plus(buffer))
                assertTrue("Block $block verletzt Puffer zu $b", clear)
            }
            assertFalse(block.start.localTime().isBefore(defaults.windowStart))
            assertFalse(block.end.localTime().isAfter(defaults.windowEnd))
        }
        assertNoOverlaps(result.blocks)
    }

    @Test
    fun `ein voller Tag nutzt genau die Luecken zwischen den Vorlesungen`() {
        // Klausur Mittwoch, Lerntag nur Dienstag. Hoher Bedarf, damit der Tag voll wird.
        val examDate = LocalDate.of(2026, 11, 4)
        val result = plan(listOf(exam(1, examDate, credits = 30)), lectures(today, examDate))

        val tuesday = result.blocks.filter { it.start.localDate() == tomorrow }
            .map { it.start.localTime() to it.end.localTime() }
        assertEquals(
            listOf(
                LocalTime.of(9, 0) to LocalTime.of(9, 45),
                LocalTime.of(12, 15) to LocalTime.of(13, 45),
                LocalTime.of(15, 45) to LocalTime.of(17, 15)
            ),
            tuesday
        )
    }

    // ── Tagesmaximum ─────────────────────────────────────────────────────────

    @Test
    fun `Tagesmaximum wird eingehalten und der letzte Block notfalls gekuerzt`() {
        val examDate = LocalDate.of(2026, 11, 20)
        val result = plan(listOf(exam(1, examDate, credits = 30)))

        result.blocks.groupBy { it.start.localDate() }.forEach { (day, blocks) ->
            assertTrue("Tag $day über dem Maximum", blocks.sumOf { it.minutes } <= 240)
        }
        val firstDay = result.blocks.filter { it.start.localDate() == tomorrow }
            .map { it.start.localTime() to it.end.localTime() }
        assertEquals(
            listOf(
                LocalTime.of(9, 0) to LocalTime.of(10, 30),
                LocalTime.of(10, 45) to LocalTime.of(12, 15),
                LocalTime.of(12, 30) to LocalTime.of(13, 30)
            ),
            firstDay
        )
    }

    @Test
    fun `kleineres Tagesmaximum begrenzt die Bloecke pro Tag`() {
        val examDate = LocalDate.of(2026, 11, 20)
        val result = plan(
            listOf(exam(1, examDate, credits = 30)),
            settings = defaults.copy(maxMinutesPerDay = 180)
        )
        result.blocks.groupBy { it.start.localDate() }.forEach { (_, blocks) ->
            assertTrue(blocks.sumOf { it.minutes } <= 180)
            assertTrue(blocks.size <= 2)
        }
    }

    // ── Freie Tage ───────────────────────────────────────────────────────────

    @Test
    fun `freie Tage bleiben frei`() {
        val examDate = LocalDate.of(2026, 11, 20)
        val defaultResult = plan(listOf(exam(1, examDate, credits = 30)))
        assertTrue(defaultResult.blocks.none { it.start.localDate().dayOfWeek == DayOfWeek.SUNDAY })
        assertTrue(defaultResult.blocks.any { it.start.localDate().dayOfWeek == DayOfWeek.SATURDAY })

        val weekendFree = plan(
            listOf(exam(1, examDate, credits = 30)),
            settings = defaults.copy(freeDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        )
        assertTrue(weekendFree.blocks.isNotEmpty())
        assertTrue(
            weekendFree.blocks.none {
                it.start.localDate().dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            }
        )
    }

    // ── Klausurtag ───────────────────────────────────────────────────────────

    @Test
    fun `nichts am oder nach dem Klausurtag, letzter Block am Vortag`() {
        val examDate = LocalDate.of(2026, 11, 20)
        val result = plan(listOf(exam(1, examDate)))

        assertTrue(result.blocks.all { it.start.localDate().isBefore(examDate) })
        assertEquals(examDate.minusDays(1), result.blocks.maxOf { it.start.localDate() })
    }

    @Test
    fun `ist der Vortag frei, liegt der letzte Block am Tag davor`() {
        // Klausur Montag, Sonntag ist frei: letzter Block am Samstag.
        val examDate = LocalDate.of(2026, 11, 23)
        val result = plan(listOf(exam(1, examDate)))
        assertEquals(LocalDate.of(2026, 11, 21), result.blocks.maxOf { it.start.localDate() })
    }

    // ── LP-Gewichtung ────────────────────────────────────────────────────────

    @Test
    fun `Lernumfang ist proportional zu den LP`() {
        val examDate = LocalDate.of(2026, 12, 1)
        val result = plan(listOf(exam(1, examDate, credits = 10), exam(2, examDate, credits = 5)))

        // 10 LP * 3 Std = 30 Std = 20 Blöcke, 5 LP = 10 Blöcke.
        assertEquals(20, result.blocks.count { it.examId == 1L })
        assertEquals(10, result.blocks.count { it.examId == 2L })
        assertTrue(result.exams.all { it.issue == null })
        assertNull(result.emptyReason)
        assertNoOverlaps(result.blocks)
    }

    @Test
    fun `unbekannte LP nutzen das Fallback-Gewicht`() {
        val examDate = LocalDate.of(2026, 12, 1)
        val result = plan(
            listOf(
                exam(1, examDate, credits = null),
                exam(2, examDate, credits = 0),
                exam(3, examDate, credits = StudyPlanner.FALLBACK_CREDITS)
            )
        )
        val counts = result.blocks.groupingBy { it.examId }.eachCount()
        assertEquals(counts[3L], counts[1L])
        assertEquals(counts[3L], counts[2L])
    }

    @Test
    fun `naeher an der Klausur liegen mehr Bloecke`() {
        val examDate = LocalDate.of(2026, 12, 1)
        val result = plan(listOf(exam(1, examDate)))
        val middle = LocalDate.of(2026, 11, 17)
        val early = result.blocks.count { it.start.localDate().isBefore(middle) }
        val late = result.blocks.count { !it.start.localDate().isBefore(middle) }
        assertEquals(10, early + late)
        assertTrue("früh=$early spät=$late", late > early)
    }

    // ── Determinismus ────────────────────────────────────────────────────────

    @Test
    fun `gleiche Eingabe liefert denselben Plan, unabhaengig von der Reihenfolge`() {
        val examDate = LocalDate.of(2026, 11, 27)
        val exams = listOf(exam(1, examDate, credits = 10), exam(2, examDate.minusDays(3), credits = 5))
        val busy = lectures(today, examDate)

        val first = plan(exams, busy)
        val second = plan(exams.reversed(), busy.reversed())
        assertEquals(first, plan(exams, busy))
        assertEquals(first, second)
    }

    // ── Leere Pläne ──────────────────────────────────────────────────────────

    @Test
    fun `ohne Klausuren ist der Plan leer mit Grund`() {
        assertEquals(StudyPlanEmptyReason.NO_EXAMS, plan(emptyList()).emptyReason)
        // Vergangene Klausur zählt nicht.
        assertEquals(
            StudyPlanEmptyReason.NO_EXAMS,
            plan(listOf(exam(1, today.minusDays(2)))).emptyReason
        )
    }

    @Test
    fun `ohne freie Zeit ist der Plan leer mit Grund`() {
        val examDate = LocalDate.of(2026, 11, 13)
        val busy = (0L..12L).map { offset ->
            val day = today.plusDays(offset)
            BusyInterval(at(day, 0, 0), at(day.plusDays(1), 0, 0))
        }
        val result = plan(listOf(exam(1, examDate)), busy)

        assertTrue(result.blocks.isEmpty())
        assertEquals(StudyPlanEmptyReason.NO_FREE_TIME, result.emptyReason)
        assertEquals(ExamPlanIssue.NO_FREE_TIME, result.exams.single().issue)
    }

    // ── Klausur schon morgen ─────────────────────────────────────────────────

    @Test
    fun `Klausur morgen mit Start morgen ist zu kurzfristig`() {
        val result = plan(listOf(exam(1, tomorrow)))
        assertTrue(result.blocks.isEmpty())
        assertEquals(StudyPlanEmptyReason.TOO_SOON, result.emptyReason)
        assertEquals(ExamPlanIssue.TOO_SOON, result.exams.single().issue)
    }

    @Test
    fun `Klausur morgen mit Start heute plant die freie Zeit ab jetzt`() {
        val lateNow = at(today, 13, 7)
        val result = plan(listOf(exam(1, tomorrow)), startDate = today, now = lateNow)

        assertTrue(result.blocks.isNotEmpty())
        assertTrue(result.blocks.all { it.start.localDate() == today })
        // Auf die nächste Viertelstunde aufgerundet.
        assertEquals(at(today, 13, 15), result.blocks.minOf { it.start })
    }

    // ── Überschneidende Klausuren ────────────────────────────────────────────

    @Test
    fun `ueberschneidende Klausuren teilen sich knappe Zeit fair und ohne Doppelbelegung`() {
        val examA = exam(1, LocalDate.of(2026, 11, 5), credits = 10)
        val examB = exam(2, LocalDate.of(2026, 11, 6), credits = 10)
        val result = plan(listOf(examB, examA))

        assertNoOverlaps(result.blocks)
        val blocksA = result.blocks.filter { it.examId == 1L }
        val blocksB = result.blocks.filter { it.examId == 2L }
        assertTrue(blocksA.all { it.start.localDate().isBefore(examA.examDate) })
        assertTrue(blocksB.all { it.start.localDate().isBefore(examB.examDate) })
        // Gleiche LP: gleich viele Blöcke, beide nur teilweise.
        assertEquals(blocksA.size, blocksB.size)
        assertTrue(blocksA.isNotEmpty())
        assertTrue(result.exams.all { it.issue == ExamPlanIssue.PARTIAL })
        // Am Klausurtag von A wird erst nach deren Ende (10:00 + 2 Std + Puffer) gelernt.
        val onExamDayA = blocksB.filter { it.start.localDate() == examA.examDate }
        assertTrue(onExamDayA.isNotEmpty())
        assertTrue(onExamDayA.all { !it.start.isBefore(at(examA.examDate, 12, 15)) })
    }

    @Test
    fun `Zusammenfassung pro Klausur nennt Ziel und geplante Minuten`() {
        val examDate = LocalDate.of(2026, 12, 1)
        val result = plan(listOf(exam(1, examDate, credits = 5)))
        val summary = result.exams.single()
        assertEquals(5 * 3 * 60, summary.targetMinutes)
        assertEquals(result.blocks.sumOf { it.minutes }.toInt(), summary.plannedMinutes)
        assertEquals(10, summary.blockCount)
        assertEquals(examDate, summary.examDate)
    }
}
