package de.transio.hiuni.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.notifications.data.NotificationCategory
import de.transio.hiuni.core.notifications.data.NotificationSettingsStore
import de.transio.hiuni.core.notifications.lecture.CourseNotificationPrefs
import de.transio.hiuni.core.notifications.lecture.CourseNotificationPrefsStore
import de.transio.hiuni.core.notifications.lecture.prefsFor
import de.transio.hiuni.feature.courses.data.CourseRepository
import de.transio.hiuni.feature.courses.semesterSortKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Eine Zeile der Vorlesungs-Übersicht: ein Kurs mit seinen beiden Schaltern. */
data class LectureNotificationRow(
    val key: String,
    val name: String,
    /** Veranstaltungsart bzw. Modulkürzel als Unterzeile, falls vorhanden. */
    val detail: String?,
    val liveEnabled: Boolean,
    val reminderMinutes: Int?
)

data class LectureNotificationsUiState(
    val semester: String? = null,
    val rows: List<LectureNotificationRow> = emptyList(),
    /** Globaler Schalter „Laufende Vorlesung"; aus heißt: die Kurs-Schalter wirken gerade nicht. */
    val liveCategoryEnabled: Boolean = true,
    val isLoaded: Boolean = false
) {
    val total: Int get() = rows.size
    val liveCount: Int get() = rows.count { it.liveEnabled }
    val reminderCount: Int get() = rows.count { it.reminderMinutes != null }

    /** Übersichtszeile in den Einstellungen. */
    val summary: String
        get() = if (rows.isEmpty()) {
            "Noch keine Kurse im aktuellen Semester"
        } else {
            "Live-Anzeige für $liveCount von $total Kursen, Erinnerung für $reminderCount"
        }
}

/**
 * Vorlesungs-Mitteilungen je Kurs für die Einstellungen (Übersichtszeile und Liste).
 * Zeigt die Kurse des aktuellen (neuesten) Semesters, gleiche Semester-Wahl wie der
 * Kurse-Tab. Schreibt nur in den [CourseNotificationPrefsStore]; der
 * Vorlesungs-Planer beobachtet ihn und plant sofort neu.
 */
@HiltViewModel
class LectureNotificationsViewModel @Inject constructor(
    courseRepository: CourseRepository,
    private val prefsStore: CourseNotificationPrefsStore,
    settingsStore: NotificationSettingsStore
) : ViewModel() {

    val state: StateFlow<LectureNotificationsUiState> = combine(
        courseRepository.observeAll(),
        prefsStore.observeAll(),
        settingsStore.observeEnabled(NotificationCategory.LIVE_LECTURE)
    ) { courses, prefs, liveCategory ->
        val semester = courses.map { it.semester }
            .filter { it.isNotBlank() }
            .distinct()
            .maxByOrNull(::semesterSortKey)
        val rows = courses
            .filter { semester == null || it.semester == semester }
            .map { course ->
                val key = CourseNotificationPrefs.keyFor(course)
                val coursePrefs = prefs.prefsFor(key)
                LectureNotificationRow(
                    key = key,
                    name = course.name,
                    detail = course.courseType?.takeIf(String::isNotBlank)
                        ?: course.moduleAbbreviation?.takeIf(String::isNotBlank),
                    liveEnabled = coursePrefs.liveEnabled,
                    reminderMinutes = coursePrefs.reminderMinutes
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        LectureNotificationsUiState(
            semester = semester,
            rows = rows,
            liveCategoryEnabled = liveCategory,
            isLoaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), LectureNotificationsUiState())

    fun setLiveEnabled(key: String, enabled: Boolean) {
        viewModelScope.launch { prefsStore.setLiveEnabled(key, enabled) }
    }

    /** `null` = Erinnerung aus. */
    fun setReminderMinutes(key: String, minutes: Int?) {
        viewModelScope.launch { prefsStore.setReminderMinutes(key, minutes) }
    }

    /** „Alle an" / „Alle aus" für die Live-Anzeige der aktuell gelisteten Kurse. */
    fun setAllLive(enabled: Boolean) {
        viewModelScope.launch {
            val keys = state.first { it.isLoaded }.rows.map { it.key }
            prefsStore.setLiveEnabledFor(keys, enabled)
        }
    }
}
