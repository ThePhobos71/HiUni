package de.transio.hiuni.feature.learnweb.data

import biweekly.Biweekly
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Eine einzelne im iCal-Feed enthaltene Calendar-Komponente (VEVENT). Nach dem
 * Parse vollständig zeitlich aufgelöst — biweekly handhabt TZID-Auflösung intern,
 * wir konvertieren auf `epochMillis` für die `Instant`-zentrische DB-Welt.
 *
 * Felder:
 * - [uid] — stabile Moodle-ID (z.B. `event_4875@uni-hildesheim.de`). Wir nutzen sie
 *   als `sourceReference` für die Calendar-Spiegelung, damit derselbe Server-Event
 *   beim nächsten Sync wieder dem gleichen Row-Eintrag zugeordnet wird.
 * - [endEpoch] kann `null` sein, wenn der VEVENT nur DTSTART hatte (Punkt-Termin).
 * - [url] — Moodle setzt für viele Event-Typen einen direkten Link (Assignment,
 *   Quiz, Activity); für reine Calendar-User-Events kann das fehlen.
 * - [courseName] best-effort aus DESCRIPTION/CATEGORIES extrahiert.
 */
data class ParsedICalEvent(
    val uid: String,
    val title: String,
    val description: String?,
    val startEpoch: Long,
    val endEpoch: Long?,
    val url: String?,
    val courseName: String?
)

/**
 * Parst den Moodle-iCal-Subscription-Feed (Multi-VEVENT) in eine Liste von
 * [ParsedICalEvent]. Verwendet `biweekly` — gleiches Pattern wie der LSF-
 * Stundenplan-Import in [LsfStundenplanRepositoryImpl].
 *
 * Robustheit: jedes einzelne VEVENT wird in einem eigenen try-catch
 * verarbeitet, damit ein einzelnes kaputtes Event nicht den ganzen Feed wegwirft.
 * Wenn der gesamte Parse fehlschlägt (Top-Level-IOException o.ä.), liefern wir
 * eine leere Liste — der Repo behandelt das als „keine iCal-Events", was den
 * Bestand stehen lässt (keine versehentliche Komplett-Löschung).
 */
@Singleton
class LearnwebICalParser @Inject constructor() {

    /**
     * @param ical Rohinhalt des Calendar-Exports. Darf null/leer sein —
     *   in diesem Fall geben wir eine leere Liste zurück.
     */
    fun parseFeed(ical: String?): List<ParsedICalEvent> {
        if (ical.isNullOrBlank()) return emptyList()
        val calendar = try {
            Biweekly.parse(ical).first()
        } catch (t: Throwable) {
            Timber.w(t, "LearnwebICalParser: Parse-Fehler — gebe leere Liste zurück")
            return emptyList()
        }
        if (calendar == null) {
            Timber.w("LearnwebICalParser: leerer iCalendar-Stream")
            return emptyList()
        }
        val results = mutableListOf<ParsedICalEvent>()
        for (event in calendar.events) {
            try {
                val uid = event.uid?.value?.trim()?.takeIf { it.isNotBlank() } ?: continue
                val dtstart = event.dateStart?.value ?: continue
                val title = event.summary?.value?.trim()?.takeIf { it.isNotBlank() } ?: continue
                val description = event.description?.value?.trim()?.takeIf { it.isNotBlank() }
                val endEpoch = event.dateEnd?.value?.time
                val url = event.url?.value?.trim()?.takeIf { it.isNotBlank() }
                // CATEGORIES rendert Moodle für Course-Events oft als Kursname;
                // erste Category wird übernommen. Wenn nichts da, prüfen wir das
                // LOCATION-Feld als zweiten Hinweis.
                val courseName = extractCourseName(event)

                results += ParsedICalEvent(
                    uid = uid,
                    title = title,
                    description = description,
                    startEpoch = dtstart.time,
                    endEpoch = endEpoch,
                    url = url,
                    courseName = courseName
                )
            } catch (t: Throwable) {
                Timber.w(t, "LearnwebICalParser: VEVENT überspringen wegen Parse-Fehler")
            }
        }
        Timber.d("LearnwebICalParser: ${results.size} iCal-Events extrahiert")
        return results
    }

    private fun extractCourseName(event: biweekly.component.VEvent): String? {
        // CATEGORIES: jedes Categories-Property hält 1..n Werte. Erstes
        // Non-Blank gewinnt.
        event.categories.forEach { cats ->
            cats.values.firstOrNull { !it.isNullOrBlank() }?.let { return it.trim() }
        }
        // LOCATION als zweites Fallback — Moodle nutzt das gelegentlich für
        // den Kursnamen, wenn keine räumliche Info da ist.
        event.location?.value?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }
}
