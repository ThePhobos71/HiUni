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
         *
         * Steht dieselbe Moodle-Event-ID auch als [SOURCE_LEARNWEB_ICAL] im
         * Kalender, gibt es diese Zeile nicht (der Feed-Eintrag gewinnt, siehe
         * dort). Reminder hängen nicht an dieser Zeile, sondern an
         * `learnweb_assignments`.
         */
        const val SOURCE_LEARNWEB_ASSIGNMENT = "LEARNWEB_ASSIGNMENT"
        /**
         * Spiegelung eines Moodle-Calendar-Events aus dem iCal-Subscription-Feed
         * (Phase 4 der Learnweb-Integration). `sourceReference` ist die
         * VEVENT-UID aus dem Feed (z.B. `event_4875@moodle...`).
         *
         * Inhaltlich reicher als [SOURCE_LEARNWEB_ASSIGNMENT]: der Feed liefert
         * Description, exakte Start/End-Zeit, optional URL + Kursname.
         *
         * Dedup mit [SOURCE_LEARNWEB_ASSIGNMENT]: Eine Abgabe steht in beiden
         * Quellen unter derselben Moodle-Calendar-Event-ID (Scraper:
         * `data-event-id="4875"`, Feed: UID `4875@<host>` bzw. `event_4875@<host>`).
         * Pro ID hält `custom_events` genau eine Zeile, und zwar diese hier
         * (Feed gewinnt). Die Scraper-Zeile wird nur geschrieben, solange es
         * keinen Feed-Eintrag mit der ID gibt, und kehrt zurück, sobald der
         * Feed die Abgabe nicht mehr liefert. Umgesetzt in
         * `LearnwebCalendarSync` (Write-Time, unabhängig von der Sync-Reihenfolge).
         */
        const val SOURCE_LEARNWEB_ICAL = "LEARNWEB_ICAL"
    }
}
