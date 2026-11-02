package de.transio.hiuni.core.notifications.lecture

import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.calendar.data.RecurrenceExpander
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Ein konkretes Vorkommen einer Vorlesung (bei Serien: die expandierte Instanz).
 * [masterId] ist die `custom_events`-ID, bei Serien die des Masters. [courseLsfId]
 * verknüpft mit dem Kurs und damit mit dessen [CourseNotificationPrefs].
 */
data class LiveLectureOccurrence(
    val masterId: Long,
    val title: String,
    val location: String?,
    val start: Instant,
    val end: Instant,
    val courseLsfId: String? = null
) {
    /**
     * Stabiler Schlüssel je Vorkommen, für den Wegwisch-Merker. Das Ende steht mit
     * drin, damit abgelaufene Merker ohne DB-Zugriff aufgeräumt werden können.
     */
    val key: String get() = "$masterId@${start.toEpochMilli()}@${end.toEpochMilli()}"
}

/**
 * Ergebnis eines Planungslaufs.
 *
 * @property running Vorlesungen, die jetzt laufen und angezeigt werden sollen.
 * @property nextWakeAt nächster Weck-Alarm. Entweder der nächste Vorlesungsbeginn
 *   im Horizont oder, wenn keiner ansteht, eine Nachplanung am Horizont-Ende.
 * @property nextWakeIsLectureStart `true`, wenn [nextWakeAt] ein Vorlesungsbeginn
 *   ist (exakter Alarm), `false` bei der ungenauen Nachplanung.
 */
data class LiveLecturePlan(
    val running: List<LiveLectureOccurrence>,
    val nextWakeAt: Instant,
    val nextWakeIsLectureStart: Boolean
)

/**
 * Reine Planungslogik der Laufende-Vorlesung-Mitteilung, Android-frei und damit als
 * JVM-Test abgesichert.
 *
 * ### Welche Termine
 *
 * Nur LSF-Stundenplan-Termine ([CustomEventEntity.SOURCE_LSF_STUNDENPLAN]), einmalig
 * oder per `recurrenceRule` wiederkehrend. Eigene Termine bleiben bewusst außen vor:
 * dort steht von „Geburtstag" bis „Zahnarzt" alles, eine „läuft noch"-Mitteilung
 * wäre eher störend. Pins, Bib-Buchungen und Learnweb-Deadlines sind keine
 * Veranstaltungen mit Dauer. Zusätzlich fallen Einträge ohne positive Dauer oder
 * länger als [MAX_DURATION] heraus (ganztägige Platzhalter).
 *
 * Je Kurs lässt sich die Anzeige abschalten ([CourseNotificationPrefs.liveEnabled]):
 * Vorkommen dieser Kurse fallen schon beim Planen heraus, zeigen also weder eine
 * Mitteilung noch bestimmen sie den Weckzeitpunkt. Termine ohne Kurs-Zuordnung
 * folgen nur dem globalen Schalter.
 *
 * ### Ablauf
 *
 * Statt je Vorlesung einen eigenen Alarm zu halten, gibt es genau EINEN Weck-Alarm:
 * beim Wecken laufen alle Vorkommen im Fenster `[now, now + HORIZON)` durch den
 * Planer, laufende werden angezeigt, und der Alarm wird auf den nächsten Beginn
 * gelegt. Dadurch gibt es keine verwaisten Alarme für gelöschte oder verschobene
 * Termine.
 */
object LiveLecturePlanner {

    /** Planungshorizont: Vorlesungen, die in den nächsten 48 Stunden beginnen. */
    val HORIZON: Duration = Duration.ofHours(48)

    /**
     * Toleranz vor dem Beginn. Ein Alarm, der ein paar Sekunden früh feuert, zeigt
     * die Vorlesung schon an, statt den Weck-Alarm erneut auf denselben Beginn zu legen.
     */
    val START_TOLERANCE: Duration = Duration.ofMinutes(1)

    /** Längere Einträge sind Platzhalter (ganztägig, Blockwoche), keine Vorlesung. */
    val MAX_DURATION: Duration = Duration.ofHours(12)

    /**
     * Eigener System-ID-Bereich `-999_999_999 .. -800_000_000`. Liegt zwischen den
     * Vorlesungs-Erinnerungen ([LectureReminderPlanner], bis -799_998_999) und den
     * Aufgaben (ab -1_000_000_001) und
     * ist damit frei von Kalender- (positiv), Noten-/Kurs- (-100 Mio bis -468 Mio),
     * Mail- (-2) und Test-IDs (-1). Zusätzlich posten wir mit eigenem Tag
     * ([LiveLectureNotifier]), sodass selbst eine Kollision keine fremde Mitteilung träfe.
     */
    const val NOTIFICATION_ID_MAX = -800_000_000
    private const val NOTIFICATION_ID_SPAN = 200_000_000L
    const val NOTIFICATION_ID_MIN = NOTIFICATION_ID_MAX - (NOTIFICATION_ID_SPAN - 1).toInt()

    /** Text neben dem Countdown im Mitteilungs-Kopf: „läuft noch · 23:12". */
    const val SUB_TEXT = "läuft noch"

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun isEligible(event: CustomEventEntity): Boolean {
        if (event.sourceKind != CustomEventEntity.SOURCE_LSF_STUNDENPLAN) return false
        val duration = Duration.between(event.startTime, event.endTime)
        return !duration.isNegative && !duration.isZero && duration <= MAX_DURATION
    }

    /**
     * Alle Vorkommen geeigneter Termine, die das Fenster `[now, now + horizon)`
     * überlappen, also auch gerade laufende. Sortiert nach Beginn.
     */
    fun occurrences(
        events: List<CustomEventEntity>,
        now: Instant,
        zone: ZoneId,
        horizon: Duration = HORIZON
    ): List<LiveLectureOccurrence> {
        val to = now.plus(horizon)
        return events
            .filter(::isEligible)
            .flatMap { RecurrenceExpander.expand(it, now, to, zone) }
            .map {
                LiveLectureOccurrence(
                    masterId = it.id,
                    title = it.title,
                    location = it.location?.trim()?.takeIf(String::isNotEmpty),
                    start = it.startTime,
                    end = it.endTime,
                    courseLsfId = it.courseLsfId?.takeIf(String::isNotBlank)
                )
            }
            .distinctBy { it.key }
            .sortedWith(compareBy({ it.start }, { it.masterId }))
    }

    /**
     * @param liveDisabledCourses Kurs-Schlüssel (LSF-IDs) mit ausgeschalteter
     *   Live-Anzeige, siehe [liveDisabledKeys].
     */
    fun plan(
        events: List<CustomEventEntity>,
        now: Instant,
        zone: ZoneId,
        dismissed: Set<String> = emptySet(),
        horizon: Duration = HORIZON,
        liveDisabledCourses: Set<String> = emptySet()
    ): LiveLecturePlan {
        val all = occurrences(events, now, zone, horizon)
            .filter { it.courseLsfId == null || it.courseLsfId !in liveDisabledCourses }
        val startsBy = now.plus(START_TOLERANCE)
        val running = all
            .filter { !it.start.isAfter(startsBy) && it.end.isAfter(now) }
            .filter { it.key !in dismissed }
        val next = all.firstOrNull { it.start.isAfter(startsBy) }
        return if (next != null) {
            LiveLecturePlan(running, next.start, nextWakeIsLectureStart = true)
        } else {
            LiveLecturePlan(running, now.plus(horizon), nextWakeIsLectureStart = false)
        }
    }

    /** System-ID der Mitteilung je Termin, stabil und im eigenen Bereich. */
    fun notificationId(masterId: Long): Int =
        (NOTIFICATION_ID_MAX - Math.floorMod(masterId, NOTIFICATION_ID_SPAN)).toInt()

    /** Restlaufzeit für `setTimeoutAfter`, mindestens 1 ms (0 hieße „kein Timeout"). */
    fun timeoutMillis(occurrence: LiveLectureOccurrence, now: Instant): Long =
        Duration.between(now, occurrence.end).toMillis().coerceAtLeast(1L)

    /** Inhaltszeile unter dem Titel: „F101 · bis 11:45" bzw. nur „bis 11:45". */
    fun contentText(occurrence: LiveLectureOccurrence, zone: ZoneId): String {
        val until = "bis ${TIME_FORMAT.format(occurrence.end.atZone(zone))}"
        val room = occurrence.location?.trim()?.takeIf(String::isNotEmpty)
        return if (room != null) "$room · $until" else until
    }

    /** Behält nur Wegwisch-Merker, deren Vorlesung noch nicht vorbei ist. */
    fun pruneDismissed(keys: Set<String>, now: Instant): Set<String> =
        keys.filterTo(mutableSetOf()) { key ->
            val endMillis = key.substringAfterLast('@', "").toLongOrNull()
            endMillis != null && key.count { it == '@' } == 2 && endMillis > now.toEpochMilli()
        }
}
