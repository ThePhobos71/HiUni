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
 *   `sourceKind = SOURCE_LEARNWEB_ASSIGNMENT` und `sourceReference = eventId`.
 * - Bestehende Einträge werden via [CustomEventDao.findBySourceReference]
 *   geupdated, ohne die `id` zu verlieren — Foreign-Reminder-IDs würden sonst
 *   verwaisen.
 * - Verschwundene Assignments (vom Server entfernt) werden via
 *   [CustomEventDao.pruneBySourceKind] gelöscht.
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
     * räumt verwaiste Spiegelungen weg.
     */
    suspend fun mirror(assignments: List<LearnwebAssignment>) {
        val reminderMinutes = runCatching { settings.notificationMinutesBefore.first() }
            .getOrDefault(SettingsDataStore.DEFAULT_NOTIFICATION_MINUTES)

        for (a in assignments) {
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
     * Calendar-Eintrag hängt. Behält das Buch-Emoji als visuellen Anker, damit
     * die LEARNWEB_ASSIGNMENT-Events im Day/Week-Grid auf den ersten Blick von
     * LSF-Stundenplan-Events unterscheidbar sind.
     */
    private fun buildDisplayTitle(raw: String): String {
        val trimmed = raw.removeSuffix(" ist fällig.").trim()
        // „📚 “ — U+1F4DA + space. Wenn der Titel schon mit einem Emoji
        // beginnt (theoretisch, aktuell nie der Fall), nehmen wir den so wie er
        // ist.
        return if (trimmed.startsWith("📚")) trimmed else "📚 $trimmed"
    }
}
