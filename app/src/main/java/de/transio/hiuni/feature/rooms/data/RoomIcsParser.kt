package de.transio.hiuni.feature.rooms.data

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Kleine, Android-freie Auswertung des LSF-iCal-Exports eines Raumplans: Welche
 * Zeitspannen sind an einem bestimmten Datum belegt?
 *
 * Bewusst eigene Zeilenlogik statt biweekly, weil der LSF-Export Eigenheiten hat,
 * an denen biweekly scheitert (beobachtet 2026-11-14):
 *  - `DTEND;TZID=Europe/Berlin:20261019T240000` (24 Uhr als Uhrzeit),
 *  - `EXDATE;TZID=Europe/Berlin:20261228T160000,` (Komma am Ende) oder leeres EXDATE,
 *  - EXDATE auch bei Einzelterminen ohne RRULE (der Termin fällt dann aus).
 *
 * Unterstützte Serien: `FREQ=WEEKLY` mit `INTERVAL` (14-täglich) und einem oder
 * mehreren `BYDAY`, `FREQ=DAILY`, einfache `MONTHLY`/`YEARLY`, jeweils mit `UNTIL`
 * oder `COUNT`. Unbekannte Frequenzen werden vorsichtig als "findet statt" gewertet
 * (höchstens ein Jahr ab Beginn), damit ein Raum im Zweifel nicht fälschlich frei ist.
 *
 * Alle Zeiten werden als lokale Zeit in Europe/Berlin behandelt.
 */
object RoomIcsParser {

    val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

    private const val MINUTES_PER_DAY = 24 * 60
    private const val UNKNOWN_RULE_HORIZON_DAYS = 366L
    private const val COUNT_SCAN_LIMIT_DAYS = 3660L

    data class Rule(
        val frequency: String,
        val interval: Int = 1,
        val until: LocalDateTime? = null,
        val count: Int? = null,
        val byDay: Set<DayOfWeek> = emptySet()
    )

    data class Event(
        val start: LocalDateTime,
        val end: LocalDateTime,
        val rule: Rule? = null,
        /** Ausgefallene Tage (EXDATE). Je Serie gibt es höchstens einen Termin pro Tag. */
        val exDates: Set<LocalDate> = emptySet()
    )

    /** Belegte, zusammengefasste Spannen an [date] aus dem Roh-iCal [ics]. */
    fun busySpansOn(ics: String, date: LocalDate): List<BusySpan> =
        busySpansOn(parseEvents(ics), date)

    fun parseEvents(ics: String): List<Event> {
        val events = mutableListOf<Event>()
        var props: MutableList<Property>? = null
        for (line in unfold(ics)) {
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> props = mutableListOf()
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    props?.let { toEvent(it) }?.let(events::add)
                    props = null
                }
                props != null -> parseProperty(line)?.let { props.add(it) }
            }
        }
        return events
    }

    /** Belegte Spannen an [date], sortiert und mit überlappenden/angrenzenden zusammengefasst. */
    fun busySpansOn(events: List<Event>, date: LocalDate): List<BusySpan> {
        val dayStart = date.atStartOfDay()
        val dayEnd = date.plusDays(1).atStartOfDay()
        val spans = mutableListOf<BusySpan>()
        for (event in events) {
            val duration = Duration.between(event.start, event.end)
            if (duration.isZero || duration.isNegative) continue
            // Mehrtägige Termine können an einem früheren Tag beginnen.
            val spanDays = ChronoUnit.DAYS.between(event.start.toLocalDate(), event.end.toLocalDate())
                .coerceAtLeast(0)
            for (back in spanDays downTo 0) {
                val occurrenceDate = date.minusDays(back)
                if (occurrenceDate in event.exDates) continue
                if (!occursOn(event, occurrenceDate)) continue
                val occStart = occurrenceDate.atTime(event.start.toLocalTime())
                val occEnd = occStart.plus(duration)
                val from = maxOf(occStart, dayStart)
                val to = minOf(occEnd, dayEnd)
                if (!from.isBefore(to)) continue
                spans += BusySpan(minuteOfDay(from, dayStart), minuteOfDay(to, dayStart))
            }
        }
        return merge(spans)
    }

    /** Findet ein Termin der Serie an [date] statt (ohne EXDATE-Prüfung)? */
    fun occursOn(event: Event, date: LocalDate): Boolean {
        val startDate = event.start.toLocalDate()
        if (date.isBefore(startDate)) return false
        val rule = event.rule ?: return date == startDate
        val occStart = date.atTime(event.start.toLocalTime())
        if (rule.until != null && occStart.isAfter(rule.until)) return false
        if (!matchesPattern(event, rule, date)) return false
        val count = rule.count ?: return true
        // COUNT: Termine vom Beginn bis einschließlich [date] zählen.
        var seen = 0
        var day = startDate
        val limit = startDate.plusDays(COUNT_SCAN_LIMIT_DAYS)
        while (!day.isAfter(date) && !day.isAfter(limit)) {
            if (matchesPattern(event, rule, day)) seen++
            if (seen > count) return false
            day = day.plusDays(1)
        }
        return seen in 1..count
    }

    private fun matchesPattern(event: Event, rule: Rule, date: LocalDate): Boolean {
        val startDate = event.start.toLocalDate()
        val interval = rule.interval.coerceAtLeast(1).toLong()
        return when (rule.frequency) {
            "DAILY" -> ChronoUnit.DAYS.between(startDate, date) % interval == 0L
            "WEEKLY" -> {
                val days = rule.byDay.ifEmpty { setOf(startDate.dayOfWeek) }
                if (date.dayOfWeek !in days) return false
                val weeks = ChronoUnit.DAYS.between(monday(startDate), monday(date)) / 7
                weeks % interval == 0L
            }
            "MONTHLY" -> date.dayOfMonth == startDate.dayOfMonth &&
                ChronoUnit.MONTHS.between(YearMonth.from(startDate), YearMonth.from(date)) % interval == 0L
            "YEARLY" -> date.dayOfMonth == startDate.dayOfMonth &&
                date.month == startDate.month &&
                (date.year - startDate.year) % interval == 0L
            else -> !date.isAfter(startDate.plusDays(UNKNOWN_RULE_HORIZON_DAYS))
        }
    }

    private fun monday(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun minuteOfDay(t: LocalDateTime, dayStart: LocalDateTime): Int =
        Duration.between(dayStart, t).toMinutes().toInt().coerceIn(0, MINUTES_PER_DAY)

    fun merge(spans: List<BusySpan>): List<BusySpan> {
        if (spans.isEmpty()) return emptyList()
        val sorted = spans.sortedWith(compareBy({ it.startMinute }, { it.endMinute }))
        val out = mutableListOf(sorted.first())
        for (span in sorted.drop(1)) {
            val last = out.last()
            if (span.startMinute <= last.endMinute) {
                out[out.lastIndex] = BusySpan(last.startMinute, maxOf(last.endMinute, span.endMinute))
            } else {
                out += span
            }
        }
        return out
    }

    // ── Zeilenebene ─────────────────────────────────────────────────────

    private data class Property(val name: String, val params: Map<String, String>, val value: String)

    /** RFC 5545: Zeilen, die mit Leerzeichen oder Tab beginnen, setzen die vorige fort. */
    private fun unfold(ics: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in ics.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + raw.substring(1)
            } else if (raw.isNotBlank()) {
                out += raw.trimEnd()
            }
        }
        return out
    }

    private fun parseProperty(line: String): Property? {
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq <= 0) null else p.substring(0, eq).uppercase() to p.substring(eq + 1).trim('"')
        }.toMap()
        return Property(head.first().uppercase(), params, line.substring(colon + 1).trim())
    }

    private fun toEvent(props: List<Property>): Event? {
        if (props.any { it.name == "STATUS" && it.value.equals("CANCELLED", ignoreCase = true) }) return null
        val startProp = props.firstOrNull { it.name == "DTSTART" } ?: return null
        val start = parseDateTime(startProp.value, startProp.params) ?: return null
        val end = props.firstOrNull { it.name == "DTEND" }
            ?.let { parseDateTime(it.value, it.params) }
            ?: props.firstOrNull { it.name == "DURATION" }
                ?.let { runCatching { start.plus(Duration.parse(it.value)) }.getOrNull() }
            ?: return null
        val rule = props.firstOrNull { it.name == "RRULE" }?.let { parseRule(it.value) }
        val exDates = props.filter { it.name == "EXDATE" }
            .flatMap { p ->
                p.value.split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .mapNotNull { parseDateTime(it, p.params)?.toLocalDate() }
            }
            .toSet()
        return Event(start = start, end = end, rule = rule, exDates = exDates)
    }

    private fun parseRule(value: String): Rule? {
        val parts = value.split(';').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq).uppercase() to part.substring(eq + 1)
        }.toMap()
        val frequency = parts["FREQ"]?.uppercase() ?: return null
        return Rule(
            frequency = frequency,
            interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            until = parts["UNTIL"]?.let { parseDateTime(it, emptyMap()) },
            count = parts["COUNT"]?.toIntOrNull(),
            byDay = parts["BYDAY"].orEmpty()
                .split(',')
                .mapNotNull { dayOf(it.trim().takeLast(2)) }
                .toSet()
        )
    }

    private fun dayOf(code: String): DayOfWeek? = when (code.uppercase()) {
        "MO" -> DayOfWeek.MONDAY
        "TU" -> DayOfWeek.TUESDAY
        "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY
        "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY
        else -> null
    }

    private val DATE_TIME = Regex("""^(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2})?(Z)?)?$""")

    /**
     * `20261026T140000` (lokal oder mit TZID), `20261221T235900Z` (UTC) oder
     * `20261026` (ganzer Tag). Stunde 24 wird zu 0 Uhr des Folgetags.
     */
    internal fun parseDateTime(value: String, params: Map<String, String>): LocalDateTime? {
        val m = DATE_TIME.matchEntire(value.trim()) ?: return null
        val (y, mo, d) = m.destructured
        val date = runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull() ?: return null
        val hour = m.groupValues[4].toIntOrNull() ?: return date.atStartOfDay()
        val minute = m.groupValues[5].toIntOrNull() ?: 0
        val second = m.groupValues[6].toIntOrNull() ?: 0
        if (hour !in 0..24 || minute !in 0..59 || second !in 0..59) return null
        val local = if (hour == 24) {
            date.plusDays(1).atTime(LocalTime.of(0, minute, second))
        } else {
            date.atTime(hour, minute, second)
        }
        if (m.groupValues[7] == "Z") {
            return local.atOffset(ZoneOffset.UTC).atZoneSameInstant(BERLIN).toLocalDateTime()
        }
        val tzid = params["TZID"] ?: return local
        val zone = runCatching { ZoneId.of(tzid) }.getOrNull() ?: return local
        if (zone == BERLIN) return local
        return ZonedDateTime.of(local, zone).withZoneSameInstant(BERLIN).toLocalDateTime()
    }
}
