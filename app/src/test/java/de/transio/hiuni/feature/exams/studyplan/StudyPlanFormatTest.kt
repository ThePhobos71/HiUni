package de.transio.hiuni.feature.exams.studyplan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class StudyPlanFormatTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun block(date: LocalDate, minutes: Long): PlannedStudyBlock {
        val start: Instant = LocalDateTime.of(date, LocalTime.of(9, 0)).atZone(zone).toInstant()
        return PlannedStudyBlock(1, "Mathe", date.plusDays(1), start, start.plusSeconds(minutes * 60))
    }

    @Test
    fun `Stunden werden deutsch formatiert`() {
        assertEquals("45 Min", StudyPlanFormat.hours(45))
        assertEquals("18 Std", StudyPlanFormat.hours(18 * 60))
        assertEquals("1,5 Std", StudyPlanFormat.hours(90))
    }

    @Test
    fun `Zusammenfassung nennt Anzahl, Stunden und letztes Datum`() {
        val plan = StudyPlan(
            blocks = listOf(
                block(LocalDate.of(2026, 11, 10), 90),
                block(LocalDate.of(2026, 11, 13), 90)
            ),
            exams = emptyList(),
            emptyReason = null
        )
        assertEquals("2 Lerneinheiten", StudyPlanFormat.headline(plan))
        assertEquals("3 Std · bis Fr. 13. Nov.", StudyPlanFormat.subline(plan, zone))
    }

    @Test
    fun `Status pro Klausur`() {
        val base = ExamPlanSummary(1, "Mathe", LocalDate.of(2026, 11, 20), 900, 540, 6, null)
        assertNull(StudyPlanFormat.issueLabel(base))
        assertEquals("nur 9 Std von 15 Std", StudyPlanFormat.issueLabel(base.copy(issue = ExamPlanIssue.PARTIAL)))
        assertEquals("zu kurzfristig", StudyPlanFormat.issueLabel(base.copy(issue = ExamPlanIssue.TOO_SOON)))
        assertEquals("6 Einheiten · 9 Std", StudyPlanFormat.examLine(base))
    }
}
