package de.transio.hiuni.feature.mensa.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Reine, Android-/Netz-freie Entscheidungslogik für den Lieblingsgericht-Alarm
 * (Muster wie [de.transio.hiuni.feature.bib.data.BibFreeSlotNotifier]).
 *
 * WAS EIN TREFFER IST: Ein gemerktes Stichwort kommt im Namen eines Gerichts vor
 * (Groß-/Kleinschreibung egal). Stichwörter statt exakter Gerichte, weil die Mensa
 * dasselbe Essen unterschiedlich benennt ("Currywurst mit Pommes", "Currywurst vom
 * Schwein"). Je Stichwort, Mensa und Tag zählt höchstens ein Treffer.
 *
 * NUR EINMAL MELDEN: Jeder Treffer hat einen Schlüssel ([hitKey]). Der Aufrufer
 * persistiert die Menge der gemeldeten Schlüssel und gibt sie beim nächsten Check
 * wieder herein. Ein Schlüssel bleibt bis zu seinem Tag bestehen, auch wenn das
 * Gericht zwischendurch aus dem Plan verschwindet: taucht es am selben Tag wieder
 * auf, piept es nicht erneut. Schlüssel vergangener Tage und gelöschter Stichwörter
 * werden aufgeräumt.
 */
object FavoriteMealNotifier {

    /** Kürzere Stichwörter ("ei") würden fast jedes Gericht treffen. */
    const val MIN_KEYWORD_LENGTH = 3

    /** Ab so vielen Tagen Abstand steht das Datum mit im Text. */
    private const val DATE_FROM_DAYS = 7L

    data class Hit(val keyword: String, val meal: MealEntity)

    data class Push(val title: String, val body: String, val refKey: String)

    /**
     * @property push     die zu feuernde Mitteilung, `null` wenn nichts Neues da ist.
     * @property newHits  die bisher ungemeldeten Treffer, nach Datum sortiert.
     * @property notified die neue Menge gemeldeter Schlüssel (ersetzt die alte).
     */
    data class Decision(val push: Push?, val newHits: List<Hit>, val notified: Set<String>)

    fun matches(keyword: String, meal: MealEntity): Boolean {
        val needle = keyword.trim()
        return needle.isNotEmpty() && meal.name.contains(needle, ignoreCase = true)
    }

    /** Stabiler Schlüssel eines Treffers: `152|currywurst|2026-11-19`. */
    fun hitKey(locationId: Int, keyword: String, date: LocalDate): String =
        "$locationId|${normalize(keyword)}|$date"

    /** Vorschlag fürs Stichwort: der Gerichtsname bis zur ersten Beilage oder Klammer. */
    fun suggestKeyword(mealName: String): String {
        val cut = listOf(" mit ", " auf ", " an ", ",", "(")
            .map { mealName.indexOf(it, ignoreCase = true) }
            .filter { it > 0 }
            .minOrNull()
        return (if (cut != null) mealName.substring(0, cut) else mealName).trim()
    }

    /** Je Stichwort das früheste treffende Gericht, für die Anzeige in der Liste. */
    fun nextHits(keywords: List<String>, meals: List<MealEntity>): Map<String, MealEntity> =
        keywords.mapNotNull { keyword ->
            meals.filter { matches(keyword, it) }
                .minWithOrNull(compareBy<MealEntity> { it.date }.thenBy { it.name })
                ?.let { keyword to it }
        }.toMap()

    /**
     * @param keywords gemerkte Stichwörter.
     * @param meals    Speiseplan-Fenster der gewählten Mensa.
     * @param notified bereits gemeldete Schlüssel des letzten Checks.
     * @param today    heutiges Datum (lokale Zeit).
     * @param notify   `false` schreibt nur die Schlüssel fort, ohne Mitteilung
     *                 (frisch hinzugefügtes Stichwort, der Nutzer sitzt in der App).
     */
    fun decide(
        keywords: List<String>,
        meals: List<MealEntity>,
        notified: Set<String>,
        today: LocalDate,
        notify: Boolean = true
    ): Decision {
        val active = keywords.map { it.trim() }.filter { it.isNotEmpty() }
        val activeNormalized = active.map { normalize(it) }.toSet()

        val hits = active.flatMap { keyword ->
            meals.filter { !it.date.isBefore(today) && matches(keyword, it) }
                .groupBy { it.locationId to it.date }
                .map { (_, sameDay) -> Hit(keyword, sameDay.minBy { it.name }) }
        }.sortedWith(compareBy<Hit> { it.meal.date }.thenBy { it.keyword })

        val newHits = hits.filter { keyOf(it) !in notified }
        val kept = notified.filter { key ->
            val date = runCatching { LocalDate.parse(key.substringAfterLast('|')) }.getOrNull()
            val keyword = key.substringAfter('|', "").substringBeforeLast('|', "")
            date != null && !date.isBefore(today) && keyword in activeNormalized
        }
        val nextNotified = kept.toSet() + hits.map { keyOf(it) }

        val push = if (notify && newHits.isNotEmpty()) pushFor(newHits, today) else null
        return Decision(push, newHits, nextNotified)
    }

    private fun keyOf(hit: Hit): String = hitKey(hit.meal.locationId, hit.keyword, hit.meal.date)

    private fun normalize(keyword: String): String = keyword.trim().lowercase()

    private fun pushFor(newHits: List<Hit>, today: LocalDate): Push {
        val refKey = "mensa_fav_" + newHits.map { keyOf(it) }.sorted().hashCode()
        if (newHits.size == 1) {
            val hit = newHits.single()
            return Push(
                title = "${hit.keyword} ${dayPhrase(hit.meal.date, today)}",
                body = hit.meal.name,
                refKey = refKey
            )
        }
        return Push(
            title = "${newHits.size} Lieblingsgerichte im Speiseplan",
            body = newHits.joinToString(", ") { "${it.keyword} ${dayPhrase(it.meal.date, today)}" },
            refKey = refKey
        )
    }

    /** `heute`, `morgen`, `am Donnerstag` oder ab einer Woche `am Dienstag, 24.11.`. */
    private fun dayPhrase(date: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> "heute"
            days == 1L -> "morgen"
            days < DATE_FROM_DAYS -> "am ${weekday(date.dayOfWeek)}"
            else -> "am ${weekday(date.dayOfWeek)}, %02d.%02d.".format(date.dayOfMonth, date.monthValue)
        }
    }

    private fun weekday(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Montag"
        DayOfWeek.TUESDAY -> "Dienstag"
        DayOfWeek.WEDNESDAY -> "Mittwoch"
        DayOfWeek.THURSDAY -> "Donnerstag"
        DayOfWeek.FRIDAY -> "Freitag"
        DayOfWeek.SATURDAY -> "Samstag"
        DayOfWeek.SUNDAY -> "Sonntag"
    }
}
