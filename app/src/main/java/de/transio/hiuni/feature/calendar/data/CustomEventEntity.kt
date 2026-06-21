package de.transio.hiuni.feature.calendar.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "custom_events",
    indices = [Index(value = ["startTime"])]
)
data class CustomEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startTime: Instant,
    val endTime: Instant,
    val sourceKind: String = SOURCE_USER,
    val sourceReference: String? = null,
    val reminderMinutesBefore: Int? = null
) {
    companion object {
        const val SOURCE_USER = "USER"
        const val SOURCE_MENSA_PIN = "MENSA_PIN"
        const val SOURCE_MOVIE_PIN = "MOVIE_PIN"
    }
}
