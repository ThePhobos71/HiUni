package de.transio.hiuni.feature.exams.studyplan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistiert die [StudyPlanSettings] im App-weiten Preferences-DataStore. Fehlende
 * Schlüssel fallen auf die Defaults des Data-Class-Konstruktors zurück; so bleiben
 * die Defaults an genau einer Stelle definiert.
 */
@Singleton
class StudyPlanSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    val settings: Flow<StudyPlanSettings> = dataStore.data
        .map { prefs -> prefs.toSettings() }
        .distinctUntilChanged()

    suspend fun current(): StudyPlanSettings = settings.first()

    /** Wendet [transform] auf die aktuellen Einstellungen an und speichert das Ergebnis. */
    suspend fun update(transform: (StudyPlanSettings) -> StudyPlanSettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[KEY_WINDOW_START] = next.windowStart.toSecondOfDay() / 60
            prefs[KEY_WINDOW_END] = next.windowEnd.toSecondOfDay() / 60
            prefs[KEY_MAX_MINUTES] = next.maxMinutesPerDay
            prefs[KEY_BLOCK_MINUTES] = next.blockMinutes
            prefs[KEY_BUFFER_MINUTES] = next.bufferMinutes
            prefs[KEY_FREE_DAYS] = next.freeDays.map { it.name }.toSet()
            prefs[KEY_HOURS_PER_CREDIT] = next.hoursPerCredit
            prefs[KEY_REMINDER] = next.reminderMinutesBefore ?: REMINDER_OFF
            prefs[KEY_START_OFFSET] = next.startOffsetDays
        }
    }

    private fun Preferences.toSettings(): StudyPlanSettings {
        val d = StudyPlanSettings()
        return StudyPlanSettings(
            windowStart = this[KEY_WINDOW_START]?.let(::minutesToTime) ?: d.windowStart,
            windowEnd = this[KEY_WINDOW_END]?.let(::minutesToTime) ?: d.windowEnd,
            maxMinutesPerDay = this[KEY_MAX_MINUTES] ?: d.maxMinutesPerDay,
            blockMinutes = this[KEY_BLOCK_MINUTES] ?: d.blockMinutes,
            bufferMinutes = this[KEY_BUFFER_MINUTES] ?: d.bufferMinutes,
            freeDays = this[KEY_FREE_DAYS]
                ?.mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
                ?.toSet()
                ?: d.freeDays,
            hoursPerCredit = this[KEY_HOURS_PER_CREDIT] ?: d.hoursPerCredit,
            reminderMinutesBefore = when (val stored = this[KEY_REMINDER]) {
                null -> d.reminderMinutesBefore
                REMINDER_OFF -> null
                else -> stored
            },
            startOffsetDays = this[KEY_START_OFFSET] ?: d.startOffsetDays
        )
    }

    private fun minutesToTime(minutes: Int): LocalTime =
        LocalTime.of((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))

    private companion object {
        const val REMINDER_OFF = -1
        val KEY_WINDOW_START = intPreferencesKey("study_plan_window_start_min")
        val KEY_WINDOW_END = intPreferencesKey("study_plan_window_end_min")
        val KEY_MAX_MINUTES = intPreferencesKey("study_plan_max_minutes_per_day")
        val KEY_BLOCK_MINUTES = intPreferencesKey("study_plan_block_minutes")
        val KEY_BUFFER_MINUTES = intPreferencesKey("study_plan_buffer_minutes")
        val KEY_FREE_DAYS = stringSetPreferencesKey("study_plan_free_days")
        val KEY_HOURS_PER_CREDIT = intPreferencesKey("study_plan_hours_per_credit")
        val KEY_REMINDER = intPreferencesKey("study_plan_reminder_minutes")
        val KEY_START_OFFSET = intPreferencesKey("study_plan_start_offset_days")
    }
}
