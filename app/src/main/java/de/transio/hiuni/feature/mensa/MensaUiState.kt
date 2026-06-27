package de.transio.hiuni.feature.mensa

import de.transio.hiuni.feature.mensa.data.Announcement
import de.transio.hiuni.feature.mensa.data.MealEntity
import java.time.LocalDate
import java.time.LocalTime

enum class Mealtime(val label: String, val from: LocalTime, val to: LocalTime, val apiToken: String) {
    MITTAG("Mittag", LocalTime.of(11, 30), LocalTime.of(14, 30), "noon"),
    ABEND("Abend", LocalTime.of(17, 30), LocalTime.of(20, 0), "evening");

    companion object {
        fun autoSelect(now: LocalTime = LocalTime.now()): Mealtime =
            if (now.isBefore(LocalTime.of(14, 0))) MITTAG else ABEND
    }
}

/**
 * Ernährungs-/Präferenzen-Filter über die `tags`-Spalte der Mensa-Gerichte.
 * Ersetzt die alte „Essen 1 / Essen 2 / Beilage"-Kategorie-Pille, die für den
 * User wenig bedeutete — Präferenzen sind das, wonach man tatsächlich filtert.
 *
 * Positive Filterung: jedes Gericht muss mindestens ein Tag-Match haben.
 */
enum class DietFilter(val label: String, private val matcher: (List<String>) -> Boolean) {
    VEGAN("Vegan", { tags -> tags.any { it.contains("vegan", ignoreCase = true) } }),
    VEGETARISCH("Vegetarisch", { tags ->
        tags.any { it.contains("veget", ignoreCase = true) || it.contains("vegan", ignoreCase = true) }
    }),
    FISCH("Fisch", { tags -> tags.any { it.contains("fisch", ignoreCase = true) } }),
    GEFLUEGEL("Geflügel", { tags -> tags.any { it.contains("geflügel", ignoreCase = true) } }),
    SCHWEIN("Schwein", { tags -> tags.any { it.contains("schwein", ignoreCase = true) } }),
    RIND("Rind", { tags -> tags.any { it.contains("rind", ignoreCase = true) } }),
    KLIMA("Klimaessen", { tags -> tags.any { it.contains("klima", ignoreCase = true) } });

    fun matches(meal: MealEntity): Boolean = matcher(splitTags(meal))

    companion object {
        private fun splitTags(meal: MealEntity): List<String> =
            meal.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
}

data class MensaUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedMealtime: Mealtime = Mealtime.autoSelect(),
    val availableDates: List<LocalDate> = emptyList(),
    val mealsByCategory: Map<String, List<MealEntity>> = emptyMap(),
    val activeDietFilter: DietFilter? = null,
    val announcements: List<Announcement> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val isSearchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<MealEntity> = emptyList(),
    /** Aktuell gewählte Mensa-Location. Wird fürs Opening-Hours-Lookup gebraucht. */
    val mensaLocationId: Int? = null
) {
    val visibleMeals: List<MealEntity>
        get() {
            val all = mealsByCategory.values.flatten()
            val filter = activeDietFilter ?: return all
            return all.filter { filter.matches(it) }
        }

    /**
     * Welche Diet-Filter haben heute überhaupt Treffer? Damit das UI keine
     * leeren "Schwein"-Pillen anbietet, wenn nirgends Schwein gekocht wird.
     */
    val availableDietFilters: List<DietFilter>
        get() {
            val all = mealsByCategory.values.flatten()
            return DietFilter.entries.filter { f -> all.any { f.matches(it) } }
        }
}
