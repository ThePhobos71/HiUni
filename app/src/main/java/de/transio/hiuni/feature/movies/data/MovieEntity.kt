package de.transio.hiuni.feature.movies.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "movies",
    indices = [
        Index(value = ["date"]),
        Index(value = ["filmId", "sessionId"], unique = true)
    ]
)
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val filmId: String,
    val sessionId: String,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val location: String? = null,
    val posterUrl: String? = null,
    val posterSlug: String? = null,
    val trailerUrl: String? = null,
    val director: String? = null,
    val country: String? = null,
    val genre: String? = null,
    val durationMinutes: Int? = null,
    val fsk: String? = null,
    val awards: String? = null,
    val nominations: String? = null,
    val specialInfo: String? = null,
    val languageVersion: String? = null,
    val isPast: Boolean = false
)
