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

data class MensaUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedMealtime: Mealtime = Mealtime.autoSelect(),
    val availableDates: List<LocalDate> = emptyList(),
    val mealsByCategory: Map<String, List<MealEntity>> = emptyMap(),
    val activeCategory: String? = null,
    val announcements: List<Announcement> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val isSearchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<MealEntity> = emptyList()
) {
    val visibleMeals: List<MealEntity>
        get() = if (activeCategory == null) {
            mealsByCategory.values.flatten()
        } else {
            mealsByCategory[activeCategory].orEmpty()
        }

    val categories: List<String>
        get() = mealsByCategory.keys.toList()
}
