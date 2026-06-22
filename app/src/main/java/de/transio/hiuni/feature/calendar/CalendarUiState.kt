package de.transio.hiuni.feature.calendar

import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import java.time.LocalDate

enum class CalendarViewMode { DAY, WEEK, MONTH }

data class CalendarUiState(
    val viewMode: CalendarViewMode = CalendarViewMode.DAY,
    val selectedDate: LocalDate = LocalDate.now(),
    val events: List<CustomEventEntity> = emptyList(),
    val isLoading: Boolean = true,
    val editing: CustomEventEntity? = null,
    val isAddSheetOpen: Boolean = false
)
