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
    val isAddSheetOpen: Boolean = false,
    /**
     * Wenn ein User per Long-Press auf einen Tag tippt, wird dieser hier zwischengespeichert
     * und vom AddEditEventSheet als Start-Datum vorbelegt. `null` = Default (heute + 1h).
     */
    val initialDateForAdd: LocalDate? = null,
    /**
     * lsfId → Anzeige-Kurzform (Modulkürzel falls vorhanden, sonst Modulname).
     * Wird benutzt, um lange LSF-Eventtitel ("3204 Logistik und Produktion 1") gegen
     * eine knackige Abkürzung ("IT-EINF1") zu tauschen, wo platzkritisch.
     */
    val courseShortNameByLsfId: Map<String, String> = emptyMap()
) {
    fun displayTitleFor(event: CustomEventEntity): String =
        event.courseLsfId?.let { courseShortNameByLsfId[it] } ?: event.title
}
