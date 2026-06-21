package de.transio.hiuni.feature.calendar

import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import java.time.LocalDate

enum class CalendarViewMode { LIST, DAY, WEEK }

data class CalendarUiState(
    val viewMode: CalendarViewMode = CalendarViewMode.LIST,
    val selectedDate: LocalDate = LocalDate.now(),
    val events: List<CustomEventEntity> = emptyList(),
    val isLoading: Boolean = true,
    val editing: CustomEventEntity? = null,
    val isAddSheetOpen: Boolean = false
)
