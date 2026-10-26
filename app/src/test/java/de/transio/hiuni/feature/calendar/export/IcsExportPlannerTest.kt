package de.transio.hiuni.feature.calendar.export

import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.calendar.data.RecurrenceRule
import de.transio.hiuni.feature.lsf.data.ExamEntity
import org.junit.Assert.assertEquals
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
 * Tests für [IcsExportPlanner]: Auswahl nach Kategorie (sourceKind), Zeitraum,
 * Zuschnitt von Serien auf das Fenster, Klausur-Mapping und leere Auswahl.
 */
class IcsExportPlannerTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val now: Instant = Instant.parse("2026-10-26T09:30:00Z")
    private val today: LocalDate = LocalDate.of(2026, 10, 26)

    private fun at(y: Int, m: Int, d: Int, h: Int = 10, min: Int = 0): Instant =
        LocalDateTime.of(y, m, d, h, min).atZone(berlin).toInstant()

    private fun event(
        id: Long,
        kind: String,
        start: Instant = at(2026, 10, 28),
        end: Instant = start.plus(Duration.ofMinutes(90)),
        ref: String? = null,
        rule: RecurrenceRule? = null,
        reminder: Int? = null
    ) = CustomEventEntity(
        id = id,
        title = "Event $id",
        location = "F101",
        startTime = start,
        endTime = end,
        sourceKind = kind,
        sourceReference = ref,
        reminderMinutesBefore = reminder,
        recurrenceRule = rule?.toJsonString()
    )

    private fun exam(
        number: String,
        date: LocalDate?,
        time: LocalTime?,
        rooms: List<String> = listOf("SC.A.0.09", "SC.B.0.37")
    ) = ExamEntity(
        veranstaltungsNumber = number,
        pruefungstext = "Pflichtmodule / $number Mathe 1",
        moduleName = "Mathe 1",
        parentModule = null,
        examDate = date,
        examTime = time,
        rooms = rooms,
        semester = "WiSe 26/27",
        semesterCode = "20262",
        registrationDate = null,
        cancellationDeadline = null,
        pruefer = "Prof. Muster",
        courseId = null
    )

    private val window = ExportWindow(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 4, 1))

    private fun select(
        events: List<CustomEventEntity>,
        categories: Set<IcsExportCategory>,
        exams: List<ExamEntity> = emptyList(),
        w: ExportWindow = window
    ) = IcsExportPlanner.select(events, exams, categories, w)

    /* ──────────────────────────────────────────────────────────────────
     * Kategorien
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `Default-Auswahl ist Vorlesungen und eigene Termine`() {
        assertEquals(
            setOf(IcsExportCategory.LECTURES, IcsExportCategory.OWN),
            IcsExportCategory.DEFAULT
        )
    }

    @Test
    fun `sourceKind wird der passenden Kategorie zugeordnet`() {
        assertEquals(IcsExportCategory.LECTURES, IcsExportCategory.forSourceKind(CustomEventEntity.SOURCE_LSF_STUNDENPLAN))
        assertEquals(IcsExportCategory.OWN, IcsExportCategory.forSourceKind(CustomEventEntity.SOURCE_USER))
        listOf(
            CustomEventEntity.SOURCE_SPORT_PIN,
            CustomEventEntity.SOURCE_MOVIE_PIN,
            CustomEventEntity.SOURCE_BIB_BOOKING,
            CustomEventEntity.SOURCE_MENSA_PIN
        ).forEach { assertEquals(it, IcsExportCategory.PINS, IcsExportCategory.forSourceKind(it)) }
        listOf(
            CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT,
            CustomEventEntity.SOURCE_LEARNWEB_ICAL
        ).forEach { assertEquals(it, IcsExportCategory.DEADLINES, IcsExportCategory.forSourceKind(it)) }
        assertNull(IcsExportCategory.forSourceKind("IRGENDWAS"))
    }

    @Test
    fun `nur Events der gewählten Kategorien landen im Export`() {
        val events = listOf(
            event(1, CustomEventEntity.SOURCE_LSF_STUNDENPLAN, ref = "abc#1"),
            event(2, CustomEventEntity.SOURCE_USER),
            event(3, CustomEventEntity.SOURCE_SPORT_PIN, ref = "sport:42"),
            event(4, CustomEventEntity.SOURCE_BIB_BOOKING, ref = "bib:7"),
            event(5, CustomEventEntity.SOURCE_LEARNWEB_ICAL, ref = "event_4875@learnweb"),
            event(6, CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT, ref = "4876")
        )
        fun uids(cats: Set<IcsExportCategory>) = select(events, cats).map { it.uid }.toSet()

        assertEquals(
            setOf("lsf_stundenplan-abc-1@hiuni", "user-2@hiuni"),
            uids(IcsExportCategory.DEFAULT)
        )
        assertEquals(
            setOf("sport_pin-sport-42@hiuni", "bib_booking-bib-7@hiuni"),
            uids(setOf(IcsExportCategory.PINS))
        )
        assertEquals(
            setOf("learnweb_ical-event_4875-learnweb@hiuni", "learnweb_assignment-4876@hiuni"),
            uids(setOf(IcsExportCategory.DEADLINES))
        )
    }

    @Test
    fun `Event-Felder werden übernommen inklusive Erinnerung`() {
        val start = at(2026, 11, 2, 8, 30)
        val e = event(9, CustomEventEntity.SOURCE_USER, start = start, reminder = 15)
            .copy(title = "Sprechstunde", description = "Bei Prof. X")
        val out = select(listOf(e), setOf(IcsExportCategory.OWN)).single()
        assertEquals("Sprechstunde", out.summary)
        assertEquals("Bei Prof. X", out.description)
        assertEquals("F101", out.location)
        assertEquals(15, out.reminderMinutesBefore)
        assertEquals(IcsTiming.Timed(start, start.plus(Duration.ofMinutes(90))), out.timing)
        assertNull(out.recurrence)
    }

    /* ──────────────────────────────────────────────────────────────────
     * Zeiträume
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `Dieses Semester im Winter läuft von Oktober bis Ende März`() {
        assertEquals(
            ExportWindow(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 4, 1)),
            IcsExportRange.SEMESTER.window(LocalDate.of(2026, 10, 26))
        )
        assertEquals(
            ExportWindow(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 4, 1)),
            IcsExportRange.SEMESTER.window(LocalDate.of(2027, 2, 1))
        )
    }

    @Test
    fun `Dieses Semester im Sommer läuft von April bis Ende September`() {
        assertEquals(
            ExportWindow(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 10, 1)),
            IcsExportRange.SEMESTER.window(LocalDate.of(2026, 5, 10))
        )
    }

    @Test
    fun `Nächste 4 Wochen beginnt heute und endet nach 28 Tagen`() {
        assertEquals(
            ExportWindow(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 23)),
            IcsExportRange.NEXT_FOUR_WEEKS.window(today)
        )
    }

    @Test
    fun `Einzeltermine außerhalb des Fensters fallen raus`() {
        val w = ExportWindow(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 23))
        val events = listOf(
            event(1, CustomEventEntity.SOURCE_USER, start = at(2026, 10, 25, 23, 0)), // davor
            event(2, CustomEventEntity.SOURCE_USER, start = at(2026, 10, 26, 0, 0)), // erster Tag
            event(3, CustomEventEntity.SOURCE_USER, start = at(2026, 11, 22, 22, 0)), // letzter Tag
            event(4, CustomEventEntity.SOURCE_USER, start = at(2026, 11, 23, 0, 0)), // danach
            // Null-Dauer-Deadline am ersten Tag um Mitternacht zählt mit.
            event(5, CustomEventEntity.SOURCE_USER, start = at(2026, 10, 26, 0, 0), end = at(2026, 10, 26, 0, 0))
        )
        assertEquals(
            setOf("user-2@hiuni", "user-3@hiuni", "user-5@hiuni"),
            select(events, setOf(IcsExportCategory.OWN), w = w).map { it.uid }.toSet()
        )
    }

    /* ──────────────────────────────────────────────────────────────────
     * Serien
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `Serie wird auf das Fenster zugeschnitten und bleibt ein Eintrag`() {
        // Startet Mo 7.9.2026 (vor dem Fenster), wöchentlich Mo+Mi, bis 1.3.2027.
        val master = event(
            7, CustomEventEntity.SOURCE_USER,
            start = at(2026, 9, 7, 10, 15),
            end = at(2026, 9, 7, 11, 45),
            rule = RecurrenceRule(
                freq = RecurrenceRule.Freq.WEEKLY,
                byDays = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                until = LocalDate.of(2027, 3, 1)
            )
        )
        val w = ExportWindow(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 23))
        val out = select(listOf(master), setOf(IcsExportCategory.OWN), w = w).single()
        // Erstes Vorkommen im Fenster: Mo 26.10. 10:15 (bereits Winterzeit).
        assertEquals(
            IcsTiming.Timed(at(2026, 10, 26, 10, 15), at(2026, 10, 26, 11, 45)),
            out.timing
        )
        assertEquals(
            IcsRecurrence(
                freq = RecurrenceRule.Freq.WEEKLY,
                interval = 1,
                byDays = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                untilExclusive = LocalDate.of(2026, 11, 23)
            ),
            out.recurrence
        )
    }

    @Test
    fun `Serie mit früherem Ende behält ihr eigenes until`() {
        val master = event(
            8, CustomEventEntity.SOURCE_USER,
            start = at(2026, 10, 27, 14, 0),
            rule = RecurrenceRule(freq = RecurrenceRule.Freq.DAILY, until = LocalDate.of(2026, 11, 1))
        )
        val out = select(listOf(master), setOf(IcsExportCategory.OWN)).single()
        assertEquals(LocalDate.of(2026, 11, 1), out.recurrence?.untilExclusive)
        assertEquals(at(2026, 10, 27, 14, 0), (out.timing as IcsTiming.Timed).start)
    }

    @Test
    fun `DTSTART springt auf den ersten passenden BYDAY-Tag`() {
        // Master liegt auf einem Dienstag, Regel nennt aber nur Donnerstag.
        val master = event(
            9, CustomEventEntity.SOURCE_USER,
            start = at(2026, 10, 27, 12, 0),
            rule = RecurrenceRule(
                freq = RecurrenceRule.Freq.WEEKLY,
                byDays = listOf(DayOfWeek.THURSDAY)
            )
        )
        val out = select(listOf(master), setOf(IcsExportCategory.OWN)).single()
        assertEquals(at(2026, 10, 29, 12, 0), (out.timing as IcsTiming.Timed).start)
        // Ohne eigenes until endet die Serie am Fensterende.
        assertEquals(window.endExclusive, out.recurrence?.untilExclusive)
    }

    @Test
    fun `Serie ohne Vorkommen im Fenster fällt raus`() {
        val master = event(
            10, CustomEventEntity.SOURCE_USER,
            start = at(2026, 5, 4),
            rule = RecurrenceRule(freq = RecurrenceRule.Freq.WEEKLY, until = LocalDate.of(2026, 7, 31))
        )
        assertTrue(select(listOf(master), setOf(IcsExportCategory.OWN)).isEmpty())
    }

    /* ──────────────────────────────────────────────────────────────────
     * Klausuren
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `Klausuren nur mit Kategorie Klausuren`() {
        val exams = listOf(exam("5395", LocalDate.of(2027, 2, 15), LocalTime.of(10, 0)))
        assertTrue(select(emptyList(), IcsExportCategory.DEFAULT, exams).isEmpty())
        assertEquals(1, select(emptyList(), setOf(IcsExportCategory.EXAMS), exams).size)
    }

    @Test
    fun `Klausur mit Uhrzeit wird zum Termin mit angenommener Dauer`() {
        val out = select(
            emptyList(),
            setOf(IcsExportCategory.EXAMS),
            listOf(exam("5395", LocalDate.of(2027, 2, 15), LocalTime.of(10, 0)))
        ).single()
        assertEquals("exam-5395-20262@hiuni", out.uid)
        assertEquals("Klausur: Mathe 1", out.summary)
        assertEquals("SC.A.0.09, SC.B.0.37", out.location)
        assertTrue(out.description!!.contains("Prof. Muster"))
        val start = at(2027, 2, 15, 10, 0)
        assertEquals(IcsTiming.Timed(start, start.plus(Duration.ofMinutes(120))), out.timing)
    }

    @Test
    fun `Klausur ohne Uhrzeit wird ganztägig, ohne Datum entfällt sie`() {
        val out = select(
            emptyList(),
            setOf(IcsExportCategory.EXAMS),
            listOf(
                exam("1111", LocalDate.of(2027, 2, 16), null, rooms = emptyList()),
                exam("2222", null, null),
                exam("3333", LocalDate.of(2027, 4, 1), LocalTime.of(8, 0)) // nach dem Fenster
            )
        )
        assertEquals(1, out.size)
        assertEquals(IcsTiming.AllDay(LocalDate.of(2027, 2, 16)), out.single().timing)
        assertNull(out.single().location)
    }

    /* ──────────────────────────────────────────────────────────────────
     * Gesamtablauf
     * ────────────────────────────────────────────────────────────────── */

    @Test
    fun `leere Kategorie-Auswahl ergibt keinen Export`() {
        val result = IcsExportPlanner.build(
            events = listOf(event(1, CustomEventEntity.SOURCE_USER)),
            exams = emptyList(),
            categories = emptySet(),
            range = IcsExportRange.SEMESTER,
            today = today,
            now = now
        )
        assertEquals(IcsExportResult.Empty, result)
    }

    @Test
    fun `keine Treffer im Zeitraum ergibt keinen Export`() {
        val result = IcsExportPlanner.build(
            events = listOf(event(1, CustomEventEntity.SOURCE_USER, start = at(2027, 6, 1))),
            exams = emptyList(),
            categories = IcsExportCategory.DEFAULT,
            range = IcsExportRange.NEXT_FOUR_WEEKS,
            today = today,
            now = now
        )
        assertEquals(IcsExportResult.Empty, result)
    }

    @Test
    fun `Treffer ergeben fertiges ics mit Anzahl und Dateiname`() {
        val result = IcsExportPlanner.build(
            events = listOf(
                event(1, CustomEventEntity.SOURCE_USER),
                event(2, CustomEventEntity.SOURCE_LSF_STUNDENPLAN, ref = "x#1"),
                event(3, CustomEventEntity.SOURCE_MOVIE_PIN, ref = "movie:1")
            ),
            exams = emptyList(),
            categories = IcsExportCategory.DEFAULT,
            range = IcsExportRange.SEMESTER,
            today = today,
            now = now
        )
        result as IcsExportResult.Ready
        assertEquals(2, result.eventCount)
        assertEquals("HiUni_WS_2026-27.ics", result.fileName)
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(result.ics).count())
        assertTrue(result.ics.contains("DTSTAMP:20261026T093000Z"))
    }

    @Test
    fun `Dateiname für vier Wochen nennt das Startdatum`() {
        val result = IcsExportPlanner.build(
            events = listOf(event(1, CustomEventEntity.SOURCE_USER)),
            exams = emptyList(),
            categories = IcsExportCategory.DEFAULT,
            range = IcsExportRange.NEXT_FOUR_WEEKS,
            today = today,
            now = now
        ) as IcsExportResult.Ready
        assertEquals("HiUni_ab_2026-10-26.ics", result.fileName)
    }
}
