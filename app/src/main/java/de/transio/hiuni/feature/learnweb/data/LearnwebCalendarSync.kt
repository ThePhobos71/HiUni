package de.transio.hiuni.feature.learnweb.data

import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.feature.calendar.data.CustomEventDao
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spiegelt Learnweb-Assignment-Deadlines in den `custom_events`-Kalender. Wird
 * vom [LearnwebRepository] nach jedem erfolgreichen Refresh aufgerufen.
 *
 * - Jeder Assignment-Eintrag erhält genau einen `CustomEventEntity` mit
 *   `sourceKind = SOURCE_LEARNWEB_ASSIGNMENT` und `sourceReference = eventId`,
 *   außer der iCal-Feed deckt dieselbe Event-ID schon ab (siehe Dedup unten).
 * - Bestehende Einträge werden via [CustomEventDao.findBySourceReference]
 *   geupdated, ohne die `id` zu verlieren — Foreign-Reminder-IDs würden sonst
 *   verwaisen.
 * - Verschwundene Assignments (vom Server entfernt) werden via
 *   [CustomEventDao.pruneBySourceKind] gelöscht.
 *
 * ### Dedup Scraper vs. iCal-Feed
 *
 * Eine Abgabe steht in beiden Quellen: der Scraper liest sie als
 * `data-event-id="4875"`, der Feed liefert sie als VEVENT mit UID
 * `4875@<host>` (bzw. `event_4875@<host>`). Beides ist dieselbe Moodle-
 * Calendar-Event-ID (siehe [LearnwebICalParser.moodleEventIdFromUid]). Pro ID
 * steht in `custom_events` genau eine Zeile:
 *
 * - Der Feed-Eintrag gewinnt: er bringt Kursname, Beschreibung und die exakte
 *   Uhrzeit mit, der Scraper rät bei fehlender Uhrzeit 23:59.
 * - Die Scraper-Spiegelung wird nur geschrieben, solange KEIN Feed-Eintrag mit
 *   derselben ID existiert. Gibt es einen, wird eine evtl. vorhandene
 *   Scraper-Zeile gelöscht. Das prüft [mirror] gegen den DB-Stand, daher ist
 *   die Reihenfolge der beiden Syncs egal.
 * - Lässt der Feed die Abgabe fallen, ruft [mirrorICalEvents] am Ende
 *   [mirror] auf und die Scraper-Zeile kommt aus `learnweb_assignments` zurück.
 *   Lässt der Scraper sie fallen, bleibt der Feed-Eintrag unberührt (Prune
 *   wirkt nur auf die eigene `sourceKind`).
 * - Reminder sind davon unabhängig: sie hängen an `learnweb_assignments`
 *   ([de.transio.hiuni.core.sync.LearnwebAssignmentReminderScheduler]), nicht an
 *   diesen Spiegelungen, und feuern deshalb pro Abgabe genau einmal je Slot.
 *
 * Read-Only-Convention: Da `CustomEventEntity` kein dediziertes `isReadOnly`-
 * Feld hat, guarded das UI das per `sourceKind`-Check (siehe CalendarScreen).
 */
@Singleton
class LearnwebCalendarSync @Inject constructor(
    private val customEventDao: CustomEventDao,
    private val settings: SettingsDataStore
) {

    /**
     * Idempotente Spiegelung: schreibt für jedes Assignment einen Calendar-Event,
     * räumt verwaiste Spiegelungen weg. Assignments, die schon als iCal-Eintrag
     * im Kalender stehen, bekommen keine eigene Zeile (Dedup, siehe Klassendoku).
     */
    suspend fun mirror(assignments: List<LearnwebAssignment>) {
        val reminderMinutes = runCatching { settings.notificationMinutesBefore.first() }
            .getOrDefault(SettingsDataStore.DEFAULT_NOTIFICATION_MINUTES)

        // Dedup: alle Moodle-Event-IDs, die der iCal-Feed bereits abdeckt. Deren
        // Scraper-Zeilen fliegen raus, auch solche aus älteren Syncs, die gar
        // nicht mehr in [assignments] stehen.
        val coveredByICal = customEventDao
            .sourceReferencesFor(CustomEventEntity.SOURCE_LEARNWEB_ICAL)
            .mapNotNull { LearnwebICalParser.moodleEventIdFromUid(it) }
            .toSet()
        if (coveredByICal.isNotEmpty()) {
            customEventDao.deleteBySourceReferences(
                CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT,
                coveredByICal.map { it.toString() }
            )
        }

        for (a in assignments) {
            if (a.eventId in coveredByICal) continue
            val ref = a.eventId.toString()
            val existing = customEventDao.findBySourceReference(
                CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT,
                ref
            )
            val due = Instant.ofEpochMilli(a.dueEpoch)
            val displayTitle = buildDisplayTitle(a.title)
            val entity = CustomEventEntity(
                id = existing?.id ?: 0L,
                title = displayTitle,
                description = a.title,
                // location bewusst null: Day/Week-Grid würde sonst „· https://…"
                // dranhängen. Der Browser-Link landet im `LearnwebAssignment.url`-
                // Feld und wird beim Klick via Lookup gegen `sourceReference`
                // aufgelöst (siehe CalendarScreen.onClickEvent).
                location = null,
                startTime = due,
                endTime = due, // Punkt-Termin, keine Dauer
                sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT,
                sourceReference = ref,
                reminderMinutesBefore = reminderMinutes,
                courseLsfId = null,
                recurrenceRule = null
            )
            if (existing == null) {
                customEventDao.insert(entity)
            } else {
                customEventDao.update(entity)
            }
        }

        val keepRefs = assignments.map { it.eventId.toString() }
        if (keepRefs.isEmpty()) {
            // Komplett-Leer-Sync: bewusst NICHT prunen, damit ein einmaliger
            // Moodle-Schluckauf nicht alle gespiegelten Deadlines killt. Der
            // Repository-Layer ruft uns nur bei nicht-leerem Sync auf, aber
            // doppelt-belt-and-suspenders ist hier billig.
            Timber.w("LearnwebCalendarSync: keep-Liste leer — überspringe Prune")
            return
        }
        customEventDao.pruneBySourceKind(
            CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT,
            keepRefs
        )
    }

    /**
     * Kürzt das redundante „ist fällig."-Suffix raus, das Moodle an jeden
     * Calendar-Eintrag hängt. Kein Emoji-Präfix (Konvention: keine Emojis in
     * UI-Strings) — die Unterscheidung zu LSF-Stundenplan-Events läuft im
     * UI über den `sourceKind`-Check.
     *
     * Kein Dedup-Risiko beim Rename: Der Merge-Key ist `sourceKind` +
     * `sourceReference` (eventId), NICHT der Titel (siehe
     * CustomEventDao.findBySourceReference). Bereits gespiegelte Events mit
     * altem „📚 “-Präfix werden beim nächsten Sync über die stabile
     * sourceReference gefunden und ihr `title` in-place ge-update — kein
     * Duplikat. Der bisherige Leading-Emoji-Strip entfernt zusätzlich einen
     * evtl. von Moodle selbst gesetzten Buch-Glyph.
     */
    private fun buildDisplayTitle(raw: String): String {
        val trimmed = raw.removeSuffix(" ist fällig.").trim()
        return trimmed.removePrefix("📚").trim()
    }

    // ---- Phase 4: iCal-Subscription-Feed-Spiegelung ----------------------

    /**
     * Spiegelt die aus dem Moodle-iCal-Feed extrahierten Events in `custom_events`
     * mit `sourceKind = SOURCE_LEARNWEB_ICAL`. Logik analog zu [mirror]:
     *
     * - `sourceReference` ist die VEVENT-UID (stabile Moodle-ID)
     * - Existierende Spiegelungen werden ge-update (id behalten)
     * - Verschwundene UIDs werden geprunt — aber nur, wenn die Soll-Liste
     *   nicht-leer ist (Schutz gegen versehentliche Komplettlöschung bei Feed-
     *   Schluckauf)
     *
     * Dedup mit [CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT]: Der Feed-Eintrag
     * gewinnt. Am Ende läuft [mirror] mit [assignments] erneut, das entfernt
     * Scraper-Zeilen mit derselben Moodle-Event-ID und holt Abgaben zurück, die
     * der Feed nicht mehr liefert (Details siehe Klassendoku).
     *
     * @param assignments derselbe Snapshot, den der Repository-Layer an [mirror]
     *   übergibt (anstehende Abgaben aus `learnweb_assignments`).
     */
    suspend fun mirrorICalEvents(
        events: List<ParsedICalEvent>,
        assignments: List<LearnwebAssignment>
    ) {
        val reminderMinutes = runCatching { settings.notificationMinutesBefore.first() }
            .getOrDefault(SettingsDataStore.DEFAULT_NOTIFICATION_MINUTES)

        for (e in events) {
            val existing = customEventDao.findBySourceReference(
                CustomEventEntity.SOURCE_LEARNWEB_ICAL,
                e.uid
            )
            val startInstant = Instant.ofEpochMilli(e.startEpoch)
            // Wenn kein DTEND da ist, behandeln wir den Event als Punkt-Termin —
            // gleiche Konvention wie für LEARNWEB_ASSIGNMENT.
            val endInstant = e.endEpoch?.let { Instant.ofEpochMilli(it) } ?: startInstant
            val entity = CustomEventEntity(
                id = existing?.id ?: 0L,
                title = buildICalDisplayTitle(e.title),
                description = buildICalDescription(e),
                location = null,
                startTime = startInstant,
                endTime = endInstant,
                sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ICAL,
                sourceReference = e.uid,
                reminderMinutesBefore = existing?.reminderMinutesBefore ?: reminderMinutes,
                courseLsfId = null,
                recurrenceRule = null
            )
            if (existing == null) {
                customEventDao.insert(entity)
            } else {
                customEventDao.update(entity)
            }
        }

        val keepRefs = events.map { it.uid }
        if (keepRefs.isEmpty()) {
            // Symmetrisch zu mirror(): leer ⇒ nicht prunen, damit ein einzelner
            // Feed-Fehler nicht alle gespiegelten Termine killt.
            Timber.w("LearnwebCalendarSync: iCal keep-Liste leer — überspringe Prune")
        } else {
            customEventDao.pruneBySourceKind(
                CustomEventEntity.SOURCE_LEARNWEB_ICAL,
                keepRefs
            )
        }

        // Abgleich mit der Scraper-Spiegelung: Duplikate raus, vom Feed
        // fallengelassene Abgaben wieder rein.
        mirror(assignments)
    }

    /**
     * Gibt den bereinigten iCal-Titel zurück. Kein Emoji-Präfix (Konvention:
     * keine Emojis in UI-Strings) — analog zu [buildDisplayTitle].
     *
     * Kein Dedup-Risiko beim Rename: Merge-Key ist `sourceKind` +
     * `sourceReference` (VEVENT-UID), nicht der Titel. Alt-Einträge mit
     * „📅 “-Präfix werden über die stabile UID gefunden und in-place ge-update.
     * Der Leading-Strip entfernt zusätzlich einen evtl. von Moodle gesetzten
     * Kalender-Glyph.
     */
    private fun buildICalDisplayTitle(raw: String): String {
        // Abgaben kommen seit der Dedup meist über den Feed in den Kalender:
        // gleiches „ist fällig"-Kürzen wie in [buildDisplayTitle], damit der
        // Titel nicht davon abhängt, welche Quelle die Zeile hält.
        val trimmed = raw.trim().removePrefix("📅").trim()
        return trimmed.removeSuffix(" ist fällig.").removeSuffix(" ist fällig").trim()
    }

    /**
     * Stellt die für UI-Detail-Sheets sinnvolle Description zusammen: Kursname
     * (falls aus CATEGORIES extrahiert) + Original-Moodle-Description. URL gehört
     * NICHT rein — die wird über das Click-Handling separat aus dem Event-Entity
     * aufgelöst (vgl. resolveLearnwebICalUrl im CalendarViewModel).
     */
    private fun buildICalDescription(e: ParsedICalEvent): String? {
        val parts = mutableListOf<String>()
        e.courseName?.let { parts += it }
        e.description?.let { parts += it }
        return parts.joinToString("\n").takeIf { it.isNotBlank() }
    }
}
