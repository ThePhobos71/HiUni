package de.transio.hiuni.feature.calendar.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "custom_events",
    indices = [Index(value = ["startTime"])]
)
data class CustomEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startTime: Instant,
    val endTime: Instant,
    val sourceKind: String = SOURCE_USER,
    val sourceReference: String? = null,
    val reminderMinutesBefore: Int? = null,
    /**
     * Verknüpft den Kalender-Event mit einer LSF-Veranstaltung (publishid). Wird beim
     * Stundenplan-Sync via Modulcode-Match befüllt — null wenn der Event nicht zu einem
     * importierten Kurs gehört (USER-Events, andere Quellen).
     */
    val courseLsfId: String? = null,
    /**
     * Recurrence-Rule als JSON-String (RFC 5545 light). `null` = einmaliges Event.
     * Schema:
     * ```
     * {"freq":"WEEKLY","interval":1,"byDays":["MO","WE"],"until":"2026-07-31"}
     * ```
     * - `freq`: "DAILY" | "WEEKLY" | "MONTHLY"
     * - `interval`: Int, ≥1
     * - `byDays`: optionale Wochentage (nur WEEKLY); null/leer → Wochentag von `startTime`
     * - `until`: ISO-LocalDate (YYYY-MM-DD), exklusiv; null → Cap auf 2 Jahre nach startTime
     *
     * Siehe [de.transio.hiuni.feature.calendar.data.RecurrenceRule] für Parser/Expansion.
     * Master-Event bleibt in DB persistent, Occurrences werden in-memory expandiert
     * (kein eigener PK — Edits gehen immer ans Master via [id]).
     */
    val recurrenceRule: String? = null
) {
    companion object {
        const val SOURCE_USER = "USER"
        const val SOURCE_MENSA_PIN = "MENSA_PIN"
        const val SOURCE_MOVIE_PIN = "MOVIE_PIN"
        const val SOURCE_SPORT_PIN = "SPORT_PIN"
        const val SOURCE_LSF_STUNDENPLAN = "LSF_STUNDENPLAN"
        const val SOURCE_BIB_BOOKING = "BIB_BOOKING"
        /**
         * Spiegelung einer Moodle-Assignment-Deadline (Phase 3 der Learnweb-
         * Integration). `sourceReference` ist die Moodle-Calendar-Event-ID als
         * String. Events dieser Quelle sind read-only: der nächste
         * [LearnwebRepository.refresh] überschreibt User-Edits und prunt Items,
         * deren Server-Event-ID verschwunden ist.
         */
        const val SOURCE_LEARNWEB_ASSIGNMENT = "LEARNWEB_ASSIGNMENT"
        /**
         * Spiegelung eines Moodle-Calendar-Events aus dem iCal-Subscription-Feed
         * (Phase 4 der Learnweb-Integration). `sourceReference` ist die
         * VEVENT-UID aus dem Feed (z.B. `event_4875@moodle...`).
         *
         * Inhaltlich oft reicher als [SOURCE_LEARNWEB_ASSIGNMENT] — der Feed
         * liefert Description, exakte Start/End-Zeit, optional URL + Kursname.
         * Beide Quellen laufen aktuell parallel: ein Assignment kann sowohl als
         * `LEARNWEB_ASSIGNMENT` (Scraper) als auch `LEARNWEB_ICAL` (Feed) im
         * Kalender erscheinen, in der Praxis aber zu unterschiedlichen Zeiten
         * dedupliziert (der Scraper holt nur Assignments, der Feed alle Events).
         * Dedup wird bewusst nicht gemacht — der User sieht zwei Einträge wenn
         * sie wirklich identisch sind, das ist heute akzeptabel.
         */
        const val SOURCE_LEARNWEB_ICAL = "LEARNWEB_ICAL"
    }
}
