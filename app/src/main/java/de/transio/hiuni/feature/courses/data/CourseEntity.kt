package de.transio.hiuni.feature.courses.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val professor: String,
    val credits: Int,
    val semester: String,
    val nextExamDate: LocalDate? = null,
    val attendedSessions: Int = 0,
    val totalSessions: Int = 0,
    val grade: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (totalSessions > 0) {
            (attendedSessions.toFloat() / totalSessions).coerceIn(0f, 1f)
        } else 0f
}
