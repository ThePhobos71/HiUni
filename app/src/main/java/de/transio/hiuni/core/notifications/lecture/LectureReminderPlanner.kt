package de.transio.hiuni.core.notifications.lecture

import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Eine geplante Vorlesungs-Erinnerung für genau ein Vorkommen.
 *
 * @property id Alarm- und Mitteilungs-ID im Bereich [LectureReminderPlanner.ID_MIN] ..
 *   [LectureReminderPlanner.ID_MAX], stabil je Vorkommen.
 */
data class LectureReminder(
    val id: Long,
    val masterId: Long,
    val title: String,
    val body: String,
    val occurrenceStart: Instant,
    val triggerAt: Instant
)

/**
 * Reine Planungslogik der Vorlesungs-Erinnerungen („Analysis I, beginnt um 10:15 ·
 * F101"), Android-frei und als JVM-Test abgesichert.
 *
 * ### Welche Termine
 *
 * Dieselben wie bei der Laufende-Vorlesung-Mitteilung ([LiveLecturePlanner.isEligible]),
 * aber nur Vorkommen von Kursen mit gesetztem Vorlauf
 * ([CourseNotificationPrefs.reminderMinutes]). Der Vorlauf liegt bewusst NICHT in
 * `custom_events.reminderMinutesBefore`: der LSF-Sync schreibt die Termine neu, und
 * die Einstellung gehört zum Kurs, nicht zum einzelnen Termin.
 *
 * ### Rollierendes Fenster
 *
 * Statt für ein ganzes Semester je Vorlesung einen Alarm zu stellen, werden nur
 * Erinnerungen geplant, deren Trigger in `(now, now + HORIZON]` liegt. Der Weck-Alarm
 * des [LiveLectureScheduler] plant spätestens nach [LiveLecturePlanner.HORIZON] (48 h)
 * neu, das Fenster ist mit 72 h länger: ein verspäteter (ungenauer) Weck-Alarm
 * verliert so keine Erinnerung.
 *
 * Die Erinnerungen laufen als `NotificationKind.EVENT`: gleicher Kanal und Schalter
 * wie die Termin-Erinnerungen, und die Aktion „Später" funktioniert.
 */
object LectureReminderPlanner {

    /** Planungsfenster für Erinnerungs-Trigger. */
    val HORIZON: Duration = Duration.ofHours(72)

    /**
     * Eigener ID-Bereich `-799_998_999 .. -770_000_000`. Liegt zwischen den
     * Bib-Meldungen (bis ca. -768_435_455) und dem Weck-Alarm der Laufenden
     * Vorlesung (-799_999_999), darunter beginnen deren Mitteilungen (ab
     * -800_000_000). Kalender (positiv), Noten/Kurse (-100 Mio bis -468 Mio), Mail
     * (-2), Test (-1) und Aufgaben (ab -1_000_000_001) bleiben unberührt. Passt in
     * den Int-Request-Code des NotificationSchedulers.
     */
    const val ID_MAX: Long = -770_000_000L
    private const val ID_SPAN: Long = 29_999_000L
    const val ID_MIN: Long = ID_MAX - (ID_SPAN - 1)

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Erinnerungen für alle Vorkommen mit Trigger `start - Vorlauf` in
     * `(now, now + horizon]`, sortiert nach Trigger.
     *
     * @param reminderMinutesByCourse Kurs-Schlüssel (LSF-ID) → Vorlauf, siehe
     *   [reminderMinutesByKey].
     */
    fun plan(
        events: List<CustomEventEntity>,
        reminderMinutesByCourse: Map<String, Int>,
        now: Instant,
        zone: ZoneId,
        horizon: Duration = HORIZON
    ): List<LectureReminder> {
        if (reminderMinutesByCourse.isEmpty()) return emptyList()
        val windowEnd = now.plus(horizon)
        val lookahead = horizon.plusMinutes(CourseNotificationPrefs.MAX_REMINDER_MINUTES.toLong())
        return LiveLecturePlanner.occurrences(events, now, zone, lookahead)
            .mapNotNull { occurrence ->
                val minutes = occurrence.courseLsfId?.let(reminderMinutesByCourse::get) ?: return@mapNotNull null
                val triggerAt = occurrence.start.minus(Duration.ofMinutes(minutes.toLong()))
                if (!triggerAt.isAfter(now) || triggerAt.isAfter(windowEnd)) return@mapNotNull null
                LectureReminder(
                    id = reminderId(occurrence.masterId, occurrence.start),
                    masterId = occurrence.masterId,
                    title = occurrence.title,
                    body = body(occurrence, zone),
                    occurrenceStart = occurrence.start,
                    triggerAt = triggerAt
                )
            }
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.triggerAt }, { it.id }))
    }

    /** Stabile ID je Vorkommen (Termin + Beginn), damit Serien-Vorkommen sich nicht überschreiben. */
    fun reminderId(masterId: Long, occurrenceStart: Instant): Long {
        val startMinutes = Math.floorDiv(occurrenceStart.toEpochMilli(), 60_000L)
        val hash = masterId * 1_000_003L + startMinutes
        return ID_MAX - Math.floorMod(hash, ID_SPAN)
    }

    fun isReminderId(id: Long): Boolean = id in ID_MIN..ID_MAX

    /** Inhaltszeile: „Beginnt um 10:15 · F101" bzw. ohne Raum nur „Beginnt um 10:15". */
    fun body(occurrence: LiveLectureOccurrence, zone: ZoneId): String {
        val at = "Beginnt um ${TIME_FORMAT.format(occurrence.start.atZone(zone))}"
        val room = occurrence.location?.trim()?.takeIf(String::isNotEmpty)
        return if (room != null) "$at · $room" else at
    }
}
