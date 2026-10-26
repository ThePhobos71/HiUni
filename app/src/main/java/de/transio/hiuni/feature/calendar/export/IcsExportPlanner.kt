package de.transio.hiuni.feature.calendar.export

import de.transio.hiuni.core.common.Semester
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.calendar.data.RecurrenceExpander
import de.transio.hiuni.feature.calendar.data.RecurrenceRule
import de.transio.hiuni.feature.lsf.data.ExamEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Was exportiert werden kann. Die Zuordnung läuft über
 * [CustomEventEntity.sourceKind]; Klausuren kommen nicht aus dem Kalender,
 * sondern aus der `exams`-Tabelle (siehe [IcsExportPlanner.select]).
 */
enum class IcsExportCategory(val label: String) {
    LECTURES("Vorlesungen"),
    OWN("Eigene Termine"),
    EXAMS("Klausuren"),
    PINS("Pins"),
    DEADLINES("Abgaben");

    companion object {
        val DEFAULT: Set<IcsExportCategory> = setOf(LECTURES, OWN)

        /** Kategorie zu einem Kalender-sourceKind, null für unbekannte Quellen. */
        fun forSourceKind(kind: String): IcsExportCategory? = when (kind) {
            CustomEventEntity.SOURCE_LSF_STUNDENPLAN -> LECTURES
            CustomEventEntity.SOURCE_USER -> OWN
            CustomEventEntity.SOURCE_SPORT_PIN,
            CustomEventEntity.SOURCE_MOVIE_PIN,
            CustomEventEntity.SOURCE_BIB_BOOKING,
            CustomEventEntity.SOURCE_MENSA_PIN -> PINS
            CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT,
            CustomEventEntity.SOURCE_LEARNWEB_ICAL -> DEADLINES
            else -> null
        }
    }
}

/** Export-Zeitraum als Datumsfenster `[start, endExclusive)` in Europe/Berlin. */
data class ExportWindow(val start: LocalDate, val endExclusive: LocalDate)

enum class IcsExportRange(val label: String) {
    SEMESTER("Dieses Semester"),
    NEXT_FOUR_WEEKS("Nächste 4 Wochen");

    fun window(today: LocalDate): ExportWindow = when (this) {
        SEMESTER -> {
            val semester = Semester.fromDate(today)
            when (semester.period) {
                Semester.Period.SS -> ExportWindow(
                    LocalDate.of(semester.year, 4, 1),
                    LocalDate.of(semester.year, 10, 1)
                )
                Semester.Period.WS -> ExportWindow(
                    LocalDate.of(semester.year, 10, 1),
                    LocalDate.of(semester.year + 1, 4, 1)
                )
            }
        }
        NEXT_FOUR_WEEKS -> ExportWindow(today, today.plusWeeks(4))
    }
}

sealed interface IcsExportResult {
    /** Nichts ausgewählt oder keine Termine im Zeitraum: keine Datei schreiben. */
    data object Empty : IcsExportResult

    data class Ready(val ics: String, val eventCount: Int, val fileName: String) : IcsExportResult
}

/**
 * Reine Auswahl- und Mapping-Logik für den .ics-Export: filtert Kalender-Events
 * nach Kategorie und Zeitraum, schneidet Serien auf das Fenster zu und bildet
 * Klausuren ab. Kein Android, kein Room, damit vollständig unit-testbar.
 *
 * Fenster-Regel: ein Termin gehört dazu, wenn er im Fenster *beginnt*.
 */
object IcsExportPlanner {

    /** LSF liefert keine Klausurdauer; zwei Stunden sind der übliche Rahmen. */
    private val EXAM_DEFAULT_DURATION: Duration = Duration.ofMinutes(120)

    private val UID_UNSAFE = Regex("[^A-Za-z0-9._-]")

    fun select(
        events: List<CustomEventEntity>,
        exams: List<ExamEntity>,
        categories: Set<IcsExportCategory>,
        window: ExportWindow
    ): List<IcsEvent> {
        val zone = IcsExporter.ZONE
        val from = window.start.atStartOfDay(zone).toInstant()
        val to = window.endExclusive.atStartOfDay(zone).toInstant()
        fun inWindow(start: Instant) = !start.isBefore(from) && start.isBefore(to)

        val fromCalendar = events
            .filter { IcsExportCategory.forSourceKind(it.sourceKind) in categories }
            .mapNotNull { event ->
                val rule = RecurrenceRule.fromJsonString(event.recurrenceRule)
                if (rule == null) {
                    if (!inWindow(event.startTime)) return@mapNotNull null
                    event.toIcs(IcsTiming.Timed(event.startTime, event.endTime), recurrence = null)
                } else {
                    seriesInWindow(event, rule, window, from, to, ::inWindow)
                }
            }

        val fromExams = if (IcsExportCategory.EXAMS in categories) {
            exams.mapNotNull { it.toIcs(window) }
        } else {
            emptyList()
        }

        return (fromCalendar + fromExams).sortedBy { it.sortKey() }
    }

    fun build(
        events: List<CustomEventEntity>,
        exams: List<ExamEntity>,
        categories: Set<IcsExportCategory>,
        range: IcsExportRange,
        today: LocalDate,
        now: Instant
    ): IcsExportResult {
        if (categories.isEmpty()) return IcsExportResult.Empty
        val selected = select(events, exams, categories, range.window(today))
        if (selected.isEmpty()) return IcsExportResult.Empty
        return IcsExportResult.Ready(
            ics = IcsExporter.export(selected, now),
            eventCount = selected.size,
            fileName = fileName(range, today)
        )
    }

    private fun fileName(range: IcsExportRange, today: LocalDate): String = when (range) {
        IcsExportRange.SEMESTER -> {
            val label = Semester.fromDate(today).displayLabel()
                .replace(' ', '_')
                .replace('/', '-')
            "HiUni_$label.ics"
        }
        IcsExportRange.NEXT_FOUR_WEEKS -> "HiUni_ab_$today.ics"
    }

    /**
     * Serie als ein VEVENT mit RRULE, zugeschnitten auf das Fenster: DTSTART ist
     * das erste Vorkommen im Fenster (über den App-eigenen [RecurrenceExpander],
     * damit Export und Kalenderansicht identisch rechnen), UNTIL das frühere aus
     * eigenem Serienende und Fensterende. Gleichzeitig synchronisiert das DTSTART
     * mit BYDAY, was RFC 5545 verlangt.
     */
    private fun seriesInWindow(
        master: CustomEventEntity,
        rule: RecurrenceRule,
        window: ExportWindow,
        from: Instant,
        to: Instant,
        inWindow: (Instant) -> Boolean
    ): IcsEvent? {
        val firstStart = RecurrenceExpander.expand(master, from, to, IcsExporter.ZONE)
            .map { it.startTime }
            .filter(inWindow)
            .minOrNull()
            ?: return null
        val duration = Duration.between(master.startTime, master.endTime)
        val until = rule.until?.takeIf { it.isBefore(window.endExclusive) } ?: window.endExclusive
        return master.toIcs(
            timing = IcsTiming.Timed(firstStart, firstStart.plus(duration)),
            recurrence = IcsRecurrence(
                freq = rule.freq,
                interval = rule.interval,
                byDays = rule.byDays,
                untilExclusive = until
            )
        )
    }

    private fun CustomEventEntity.toIcs(timing: IcsTiming, recurrence: IcsRecurrence?) = IcsEvent(
        uid = "${sourceKind.lowercase()}-${uidPart(sourceReference ?: id.toString())}@hiuni",
        summary = title,
        timing = timing,
        description = description,
        location = location,
        recurrence = recurrence,
        reminderMinutesBefore = reminderMinutesBefore
    )

    private fun ExamEntity.toIcs(window: ExportWindow): IcsEvent? {
        val date = examDate ?: return null
        if (date.isBefore(window.start) || !date.isBefore(window.endExclusive)) return null
        val time = examTime
        val timing = if (time == null) {
            IcsTiming.AllDay(date)
        } else {
            val start = date.atTime(time).atZone(IcsExporter.ZONE).toInstant()
            IcsTiming.Timed(start, start.plus(EXAM_DEFAULT_DURATION))
        }
        val description = buildList {
            add(pruefungstext)
            pruefer?.takeIf { it.isNotBlank() }?.let { add("Prüfer: $it") }
            if (time != null) add("Dauer im LSF nicht angegeben, zwei Stunden eingetragen.")
        }.joinToString("\n")
        return IcsEvent(
            uid = "exam-${uidPart(veranstaltungsNumber)}-${uidPart(semesterCode)}@hiuni",
            summary = "Klausur: $moduleName",
            timing = timing,
            description = description,
            location = rooms.filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() }
        )
    }

    private fun uidPart(raw: String): String = raw.replace(UID_UNSAFE, "-")

    private fun IcsEvent.sortKey(): Instant = when (val t = timing) {
        is IcsTiming.Timed -> t.start
        is IcsTiming.AllDay -> t.date.atStartOfDay(IcsExporter.ZONE).toInstant()
    }
}
