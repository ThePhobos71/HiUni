package de.transio.hiuni.core.notifications.lecture

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-Memory-Variante für Scheduler- und ViewModel-Tests, gleiche Semantik wie der DataStore-Store. */
class FakeCourseNotificationPrefsStore(
    initial: Map<String, CourseNotificationPrefs> = emptyMap()
) : CourseNotificationPrefsStore {

    val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<Map<String, CourseNotificationPrefs>> = flow

    override suspend fun current(): Map<String, CourseNotificationPrefs> = flow.value

    override suspend fun setLiveEnabled(key: String, enabled: Boolean) {
        update(key) { it.copy(liveEnabled = enabled) }
    }

    override suspend fun setReminderMinutes(key: String, minutes: Int?) {
        update(key) { it.copy(reminderMinutes = minutes?.takeIf { m -> m > 0 }) }
    }

    override suspend fun setLiveEnabledFor(keys: Collection<String>, enabled: Boolean) {
        keys.forEach { key -> update(key) { it.copy(liveEnabled = enabled) } }
    }

    private fun update(key: String, change: (CourseNotificationPrefs) -> CourseNotificationPrefs) {
        val next = change(flow.value.prefsFor(key))
        flow.value = if (next == CourseNotificationPrefs.DEFAULT) flow.value - key else flow.value + (key to next)
    }
}
