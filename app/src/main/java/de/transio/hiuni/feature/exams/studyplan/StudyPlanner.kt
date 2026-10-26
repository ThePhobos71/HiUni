package de.transio.hiuni.feature.exams.studyplan

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Lokaler, deterministischer Lernplan-Planer. Kein Netz, kein Zufall: gleiche
 * Eingabe liefert immer denselben Plan.
 *
 * ### Ablauf
 *
 * 1. **Klausuren filtern:** nur Klausuren, die nach `now` beginnen. Letzter Lerntag
 *    einer Klausur ist ihr Vortag; liegt der vor dem Starttag, ist sie zu kurzfristig
 *    ([ExamPlanIssue.TOO_SOON]).
 * 2. **Slots pro Tag:** Für jeden Tag zwischen Start und letztem Lerntag (ohne freie
 *    Tage) wird das Lernfenster um alle belegten Zeiten (plus Puffer davor und
 *    danach) gekürzt. Die Klausuren selbst gelten als belegt, und zwar vom Tagesbeginn
 *    bis zu ihrem Ende ([EXAM_DURATION_MINUTES]): am Tag einer Klausur wird erst
 *    danach gelernt. In den freien Lücken entstehen chronologisch Blöcke mit
 *    Blocklänge und Puffer dazwischen, bis das Tagesmaximum erreicht ist. Ein Block
 *    darf kürzer sein (kleine Lücke oder Rest des Tagesbudgets), aber nie kürzer als
 *    [MIN_BLOCK_MINUTES]. Heute beginnt das Fenster frühestens zur nächsten
 *    Viertelstunde.
 * 3. **Bedarf:** pro Klausur `LP * Stunden pro LP`, umgerechnet in Blöcke (LP
 *    unbekannt oder 0: [FALLBACK_CREDITS]). Reicht die Zeit nicht, werden die
 *    Klausuren proportional gekürzt: Für jede Klausur (nach Datum) wird geprüft, ob
 *    der Bedarf aller Klausuren bis zu ihr in die Slots bis zu ihrem Vortag passt.
 *    Jede Klausur bekommt den knappsten Faktor aller Prüfungen ab ihrer eigenen,
 *    so teilen sich überschneidende Klausuren knappe Zeit im Verhältnis ihrer LP.
 * 4. **Verteilung:** Klausuren nach Datum (frühere zuerst). Die Lerntage einer
 *    Klausur bekommen ein linear steigendes Gewicht (erster Tag 1, Vortag 3), daraus
 *    ergibt sich für jeden Block ein Wunschtag: mehr Blöcke kurz vor der Klausur,
 *    einzelne Wiederholungen früher. Der letzte Block liegt immer auf dem letzten
 *    Lerntag (in der Regel der Vortag). Ist der Wunschtag voll, wird abwechselnd
 *    davor und danach gesucht (erst früher, dann später). Innerhalb eines Tages
 *    nimmt ein Block den frühesten freien Slot.
 */
object StudyPlanner {

    /** Gewicht für Klausuren ohne bekannte LP. */
    const val FALLBACK_CREDITS = 5

    /** Kürzester sinnvoller Lernblock in Minuten. */
    const val MIN_BLOCK_MINUTES = 45

    /** Angenommene Klausurdauer, solange LSF keine Dauer liefert. */
    const val EXAM_DURATION_MINUTES = 120L

    /** Zusatzgewicht des letzten Lerntags gegenüber dem ersten (linear dazwischen). */
    private const val RAMP = 2.0

    private const val QUARTER_HOUR = 15L

    fun plan(
        exams: List<StudyPlanExam>,
        busy: List<BusyInterval>,
        settings: StudyPlanSettings,
        startDate: LocalDate,
        now: Instant,
        zone: ZoneId
    ): StudyPlan {
        val today = now.atZone(zone).toLocalDate()
        val firstDay = maxOf(startDate, today)
        val upcoming = exams
            .filter { it.examStart.atZone(zone).toInstant().isAfter(now) }
            .distinctBy { it.examId }
            .sortedWith(compareBy<StudyPlanExam>({ it.examStart }, { it.examId }))
        if (upcoming.isEmpty()) {
            return StudyPlan(emptyList(), emptyList(), StudyPlanEmptyReason.NO_EXAMS)
        }

        val buffer = Duration.ofMinutes(settings.bufferMinutes.coerceAtLeast(0).toLong())
        val blocked = (busy + upcoming.map { examBusyInterval(it, zone) })
            .filter { it.end.isAfter(it.start) }
            .map { BusyInterval(it.start.minus(buffer), it.end.plus(buffer)) }
            .sortedWith(compareBy<BusyInterval>({ it.start }, { it.end }))

        // ── Slots pro Tag ──
        val lastPlanDay = upcoming.maxOf { it.examDate }.minusDays(1)
        val days = mutableListOf<LocalDate>()
        val slotsByDay = mutableMapOf<LocalDate, List<Slot>>()
        var day = firstDay
        while (!day.isAfter(lastPlanDay)) {
            if (day.dayOfWeek !in settings.freeDays) {
                val slots = daySlots(day, settings, blocked, now, zone)
                if (slots.isNotEmpty()) {
                    days += day
                    slotsByDay[day] = slots
                }
            }
            day = day.plusDays(1)
        }
        val taken = slotsByDay.mapValues { BooleanArray(it.value.size) }

        // ── Bedarf und proportionale Kürzung ──
        val blockMinutes = settings.blockMinutes.coerceAtLeast(MIN_BLOCK_MINUTES)
        val targetMinutes = upcoming.associate { exam ->
            val credits = exam.credits?.takeIf { it > 0 } ?: FALLBACK_CREDITS
            exam.examId to credits * settings.hoursPerCredit.coerceAtLeast(1) * 60
        }
        val wantedBlocks = targetMinutes.mapValues { (_, minutes) ->
            ceil(minutes.toDouble() / blockMinutes).toInt()
        }
        val plannable = upcoming.filter { !it.examDate.minusDays(1).isBefore(firstDay) }
        val capacityUntil = plannable.map { exam ->
            val lastDay = exam.examDate.minusDays(1)
            days.filter { !it.isAfter(lastDay) }.sumOf { slotsByDay.getValue(it).size }
        }
        val prefixFactor = DoubleArray(plannable.size)
        var cumulative = 0
        plannable.forEachIndexed { i, exam ->
            cumulative += wantedBlocks.getValue(exam.examId)
            prefixFactor[i] = if (cumulative == 0) 1.0 else min(1.0, capacityUntil[i].toDouble() / cumulative)
        }
        val scaledBlocks = plannable.mapIndexed { i, exam ->
            var factor = 1.0
            for (j in i until plannable.size) factor = min(factor, prefixFactor[j])
            val wanted = wantedBlocks.getValue(exam.examId)
            exam.examId to if (factor >= 1.0) wanted else max(1, floor(wanted * factor).toInt())
        }.toMap()

        // ── Verteilung ──
        val blocks = mutableListOf<PlannedStudyBlock>()
        val summaries = mutableListOf<ExamPlanSummary>()
        for (exam in upcoming) {
            val lastDay = exam.examDate.minusDays(1)
            val target = targetMinutes.getValue(exam.examId)
            if (lastDay.isBefore(firstDay)) {
                summaries += summary(exam, target, emptyList(), ExamPlanIssue.TOO_SOON)
                continue
            }
            val eligible = days.filter { !it.isAfter(lastDay) }
            val examBlocks = mutableListOf<PlannedStudyBlock>()
            val count = scaledBlocks.getValue(exam.examId)
            if (eligible.isNotEmpty() && count > 0) {
                val cumWeights = cumulativeWeights(eligible.size)
                for (k in count downTo 1) {
                    val ideal = if (k == count) {
                        eligible.lastIndex
                    } else {
                        idealIndex(cumWeights, (k - 0.5) / count)
                    }
                    val slot = takeNearest(eligible, ideal, slotsByDay, taken) ?: continue
                    examBlocks += PlannedStudyBlock(
                        examId = exam.examId,
                        title = exam.title,
                        examDate = exam.examDate,
                        start = slot.start,
                        end = slot.end
                    )
                }
            }
            val issue = when {
                examBlocks.isEmpty() -> ExamPlanIssue.NO_FREE_TIME
                examBlocks.size < wantedBlocks.getValue(exam.examId) -> ExamPlanIssue.PARTIAL
                else -> null
            }
            blocks += examBlocks
            summaries += summary(exam, target, examBlocks, issue)
        }

        val sortedBlocks = blocks.sortedWith(compareBy<PlannedStudyBlock>({ it.start }, { it.examId }))
        val emptyReason = when {
            sortedBlocks.isNotEmpty() -> null
            summaries.all { it.issue == ExamPlanIssue.TOO_SOON } -> StudyPlanEmptyReason.TOO_SOON
            else -> StudyPlanEmptyReason.NO_FREE_TIME
        }
        return StudyPlan(sortedBlocks, summaries, emptyReason)
    }

    /**
     * Belegte Zeit einer Klausur: vom Beginn des Klausurtags bis zum angenommenen
     * Ende. Vor einer Klausur wird am selben Tag nicht für andere Fächer gelernt.
     */
    fun examBusyInterval(exam: StudyPlanExam, zone: ZoneId): BusyInterval = BusyInterval(
        start = exam.examDate.atStartOfDay(zone).toInstant(),
        end = exam.examStart.atZone(zone).toInstant().plus(Duration.ofMinutes(EXAM_DURATION_MINUTES))
    )

    // ── Interna ──────────────────────────────────────────────────────────────

    private data class Slot(val start: Instant, val end: Instant)

    private fun daySlots(
        day: LocalDate,
        settings: StudyPlanSettings,
        blocked: List<BusyInterval>,
        now: Instant,
        zone: ZoneId
    ): List<Slot> {
        if (!settings.windowEnd.isAfter(settings.windowStart)) return emptyList()
        var windowStart = day.atTime(settings.windowStart).atZone(zone).toInstant()
        val windowEnd = day.atTime(settings.windowEnd).atZone(zone).toInstant()
        if (windowStart.isBefore(now)) windowStart = roundUpToQuarter(now)
        if (!windowEnd.isAfter(windowStart)) return emptyList()

        // Freie Lücken = Fenster minus belegte Zeiten (nach Start sortiert).
        val gaps = mutableListOf<Slot>()
        var cursor = windowStart
        for (b in blocked) {
            if (!b.end.isAfter(windowStart)) continue
            if (!b.start.isBefore(windowEnd)) break
            if (b.start.isAfter(cursor)) gaps += Slot(cursor, minOf(b.start, windowEnd))
            if (b.end.isAfter(cursor)) cursor = b.end
            if (!cursor.isBefore(windowEnd)) break
        }
        if (cursor.isBefore(windowEnd)) gaps += Slot(cursor, windowEnd)

        val blockMinutes = settings.blockMinutes.coerceAtLeast(MIN_BLOCK_MINUTES).toLong()
        val minBlock = min(MIN_BLOCK_MINUTES.toLong(), blockMinutes)
        val pause = Duration.ofMinutes(settings.bufferMinutes.coerceAtLeast(0).toLong())
        var budget = settings.maxMinutesPerDay.toLong()
        val slots = mutableListOf<Slot>()
        for (gap in gaps) {
            var t = gap.start
            while (budget >= minBlock) {
                val available = Duration.between(t, gap.end).toMinutes()
                val length = minOf(blockMinutes, budget, available)
                if (length < minBlock) break
                val end = t.plus(Duration.ofMinutes(length))
                slots += Slot(t, end)
                budget -= length
                t = end.plus(pause)
            }
            if (budget < minBlock) break
        }
        return slots
    }

    private fun roundUpToQuarter(instant: Instant): Instant {
        val minute = instant.truncatedTo(ChronoUnit.MINUTES)
        val epochMinutes = minute.epochSecond / 60
        val remainder = epochMinutes % QUARTER_HOUR
        val needsRound = remainder != 0L || minute.isBefore(instant)
        if (!needsRound) return minute
        val base = if (remainder == 0L) minute else minute.minus(Duration.ofMinutes(remainder))
        return base.plus(Duration.ofMinutes(QUARTER_HOUR))
    }

    /** Kumulierte, linear steigende Tagesgewichte (1 bis 1 + [RAMP]). */
    private fun cumulativeWeights(n: Int): DoubleArray {
        val cum = DoubleArray(n)
        var sum = 0.0
        for (i in 0 until n) {
            val w = if (n == 1) 1.0 else 1.0 + RAMP * i / (n - 1)
            sum += w
            cum[i] = sum
        }
        return cum
    }

    private fun idealIndex(cumWeights: DoubleArray, quantile: Double): Int {
        val total = cumWeights.last()
        for (i in cumWeights.indices) {
            if (cumWeights[i] / total >= quantile) return i
        }
        return cumWeights.lastIndex
    }

    /** Nimmt den frühesten freien Slot am Wunschtag oder am nächstgelegenen Tag (früher zuerst). */
    private fun takeNearest(
        eligible: List<LocalDate>,
        ideal: Int,
        slotsByDay: Map<LocalDate, List<Slot>>,
        taken: Map<LocalDate, BooleanArray>
    ): Slot? {
        var offset = 0
        while (ideal - offset >= 0 || ideal + offset <= eligible.lastIndex) {
            val candidates = if (offset == 0) listOf(ideal) else listOf(ideal - offset, ideal + offset)
            for (index in candidates) {
                if (index !in eligible.indices) continue
                val date = eligible[index]
                val flags = taken.getValue(date)
                val free = flags.indexOfFirst { !it }
                if (free >= 0) {
                    flags[free] = true
                    return slotsByDay.getValue(date)[free]
                }
            }
            offset++
        }
        return null
    }

    private fun summary(
        exam: StudyPlanExam,
        targetMinutes: Int,
        blocks: List<PlannedStudyBlock>,
        issue: ExamPlanIssue?
    ) = ExamPlanSummary(
        examId = exam.examId,
        title = exam.title,
        examDate = exam.examDate,
        targetMinutes = targetMinutes,
        plannedMinutes = blocks.sumOf { it.minutes }.toInt(),
        blockCount = blocks.size,
        issue = issue
    )
}
