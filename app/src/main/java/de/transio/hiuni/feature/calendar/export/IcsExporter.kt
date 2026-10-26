package de.transio.hiuni.feature.calendar.export

import de.transio.hiuni.feature.calendar.data.RecurrenceRule
import de.transio.hiuni.feature.calendar.data.toIsoAbbrev
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Zeitangabe eines exportierten Termins: entweder mit Uhrzeit ([Timed]) oder
 * ganztägig ([AllDay], z.B. Klausuren ohne bekannte Uhrzeit).
 */
sealed interface IcsTiming {
    /** [end] null oder nicht nach [start] = Termin ohne Dauer (kein DTEND). */
    data class Timed(val start: Instant, val end: Instant?) : IcsTiming

    /** Ganztägig; [endExclusive] folgt der RFC-Konvention (Folgetag). */
    data class AllDay(val date: LocalDate, val endExclusive: LocalDate = date.plusDays(1)) : IcsTiming
}

/**
 * Wiederholung im Export. Spiegelt [RecurrenceRule], inklusive deren Semantik:
 * [untilExclusive] ist wie `RecurrenceRule.until` ein *exklusives* Datum. Der
 * Exporter rechnet es in das inklusive RFC-UNTIL um.
 */
data class IcsRecurrence(
    val freq: RecurrenceRule.Freq,
    val interval: Int = 1,
    val byDays: List<DayOfWeek>? = null,
    val untilExclusive: LocalDate? = null
)

/** Neutrales Export-Modell, unabhängig von Room-Entities. */
data class IcsEvent(
    val uid: String,
    val summary: String,
    val timing: IcsTiming,
    val description: String? = null,
    val location: String? = null,
    val recurrence: IcsRecurrence? = null,
    val reminderMinutesBefore: Int? = null
)

/**
 * Erzeugt eine RFC-5545-konforme iCalendar-Datei aus [IcsEvent]s. Reine Kotlin-
 * Logik ohne Android-Abhängigkeiten, damit sie vollständig unit-testbar ist.
 *
 * Zeitzonen-Entscheidung: Termine mit Uhrzeit werden als lokale Zeit mit
 * `TZID=Europe/Berlin` geschrieben, dazu eine eingebettete VTIMEZONE mit den
 * DST-Regeln. Reine UTC-Zeiten (`...Z`) würden bei wöchentlichen Serien über die
 * Zeitumstellung hinweg falsch: die RRULE wiederholt dann die UTC-Uhrzeit, und
 * die 10:15-Vorlesung stünde nach Ende Oktober um 9:15 im Kalender. Mit TZID
 * bleibt sie bei 10:15 Ortszeit. UTC wird nur dort verwendet, wo der RFC es
 * verlangt: DTSTAMP und das UNTIL einer RRULE mit TZID-DTSTART.
 */
object IcsExporter {

    /** Export-Zone. Die App ist auf die Uni Hildesheim zugeschnitten. */
    val ZONE: ZoneId = ZoneId.of("Europe/Berlin")
    private const val TZID = "Europe/Berlin"
    private const val CRLF = "\r\n"
    private const val MAX_LINE_OCTETS = 75

    private val localFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val utcFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)

    /**
     * Statische VTIMEZONE für Europe/Berlin (EU-Regel seit 1996: letzter Sonntag
     * im März 02:00 → MESZ, letzter Sonntag im Oktober 03:00 → MEZ).
     */
    private val berlinTimezone = listOf(
        "BEGIN:VTIMEZONE",
        "TZID:$TZID",
        "X-LIC-LOCATION:$TZID",
        "BEGIN:DAYLIGHT",
        "TZOFFSETFROM:+0100",
        "TZOFFSETTO:+0200",
        "TZNAME:CEST",
        "DTSTART:19700329T020000",
        "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU",
        "END:DAYLIGHT",
        "BEGIN:STANDARD",
        "TZOFFSETFROM:+0200",
        "TZOFFSETTO:+0100",
        "TZNAME:CET",
        "DTSTART:19701025T030000",
        "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU",
        "END:STANDARD",
        "END:VTIMEZONE"
    )

    /**
     * Baut die komplette Datei. [dtStamp] ist der Erzeugungszeitpunkt (DTSTAMP),
     * als Parameter, damit Tests deterministisch bleiben.
     */
    fun export(events: List<IcsEvent>, dtStamp: Instant): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//HiUni//DE",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "X-WR-CALNAME:HiUni",
            "X-WR-TIMEZONE:$TZID"
        )
        // VTIMEZONE nur, wenn ein Termin die TZID auch referenziert.
        if (events.any { it.timing is IcsTiming.Timed }) lines += berlinTimezone
        val stamp = utcFormat.format(dtStamp)
        events.forEach { lines += eventLines(it, stamp) }
        lines += "END:VCALENDAR"
        return lines.joinToString(separator = "") { foldLine(it) + CRLF }
    }

    private fun eventLines(event: IcsEvent, stamp: String): List<String> = buildList {
        add("BEGIN:VEVENT")
        add("UID:${escapeText(event.uid)}")
        add("DTSTAMP:$stamp")
        when (val timing = event.timing) {
            is IcsTiming.Timed -> {
                add("DTSTART;TZID=$TZID:${local(timing.start)}")
                val end = timing.end
                if (end != null && end.isAfter(timing.start)) {
                    add("DTEND;TZID=$TZID:${local(end)}")
                }
            }
            is IcsTiming.AllDay -> {
                add("DTSTART;VALUE=DATE:${dateFormat.format(timing.date)}")
                add("DTEND;VALUE=DATE:${dateFormat.format(timing.endExclusive)}")
            }
        }
        event.recurrence?.let { add(rrule(it)) }
        add("SUMMARY:${escapeText(event.summary)}")
        event.location?.takeIf { it.isNotBlank() }?.let { add("LOCATION:${escapeText(it)}") }
        event.description?.takeIf { it.isNotBlank() }?.let { add("DESCRIPTION:${escapeText(it)}") }
        event.reminderMinutesBefore?.takeIf { it >= 0 }?.let { minutes ->
            add("BEGIN:VALARM")
            add("ACTION:DISPLAY")
            add("DESCRIPTION:${escapeText(event.summary)}")
            add(if (minutes == 0) "TRIGGER:PT0M" else "TRIGGER:-PT${minutes}M")
            add("END:VALARM")
        }
        add("END:VEVENT")
    }

    private fun local(instant: Instant): String = localFormat.format(instant.atZone(ZONE))

    /**
     * RRULE in fester Reihenfolge FREQ, INTERVAL, BYDAY, UNTIL. UNTIL ist laut
     * RFC inklusiv und muss bei DTSTART mit TZID in UTC stehen: aus dem exklusiven
     * App-Datum wird der letzte Moment davor (23:59:59 Ortszeit des Vortags).
     */
    private fun rrule(rule: IcsRecurrence): String = buildString {
        append("RRULE:FREQ=").append(rule.freq.name)
        if (rule.interval > 1) append(";INTERVAL=").append(rule.interval)
        val days = rule.byDays?.takeIf { rule.freq == RecurrenceRule.Freq.WEEKLY && it.isNotEmpty() }
        if (days != null) {
            append(";BYDAY=")
            append(days.distinct().sortedBy { it.value }.joinToString(",") { it.toIsoAbbrev() })
        }
        rule.untilExclusive?.let { until ->
            val lastMoment = until.atStartOfDay(ZONE).toInstant().minusSeconds(1)
            append(";UNTIL=").append(utcFormat.format(lastMoment))
        }
    }

    /**
     * TEXT-Escaping nach RFC 5545 Abschnitt 3.3.11: Backslash, Semikolon und Komma
     * bekommen einen Backslash, Zeilenumbrüche (CRLF, CR, LF) werden zu `\n`.
     * Übrige Steuerzeichen sind in TEXT nicht erlaubt und fallen weg.
     */
    fun escapeText(value: String): String {
        val normalized = value.replace("\r\n", "\n").replace('\r', '\n')
        val out = StringBuilder(normalized.length + 8)
        for (c in normalized) {
            when {
                c == '\\' -> out.append("\\\\")
                c == ';' -> out.append("\\;")
                c == ',' -> out.append("\\,")
                c == '\n' -> out.append("\\n")
                c == '\t' -> out.append(' ')
                c.isISOControl() -> Unit
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    /**
     * Line-Folding nach RFC 5545 Abschnitt 3.1: physische Zeilen höchstens 75
     * Oktette (ohne CRLF), Fortsetzungszeilen beginnen mit einem Leerzeichen,
     * das mitzählt. Getrennt wird nur an Codepoint-Grenzen, damit kein UTF-8-
     * Mehrbytezeichen (Umlaut, Euro, Surrogat-Paar) zerrissen wird.
     *
     * Rückgabe ohne abschließendes CRLF; Trennstellen sind `CRLF + " "`.
     */
    fun foldLine(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= MAX_LINE_OCTETS) return line
        val out = StringBuilder(line.length + line.length / 70 * 3)
        var used = 0
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val charCount = Character.charCount(cp)
            val octets = utf8Length(cp)
            if (used + octets > MAX_LINE_OCTETS) {
                out.append(CRLF).append(' ')
                used = 1
            }
            out.append(line, i, i + charCount)
            used += octets
            i += charCount
        }
        return out.toString()
    }

    private fun utf8Length(codePoint: Int): Int = when {
        codePoint < 0x80 -> 1
        codePoint < 0x800 -> 2
        codePoint < 0x10000 -> 3
        else -> 4
    }
}
