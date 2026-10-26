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
    val isPast: Boolean = false,
    // TMDB-Details, beim Refresh einmal geholt (siehe MoviesRepositoryImpl.enrichWithTmdb),
    // damit der Detail-Screen beim Öffnen nicht selbst bei TMDB nachfragen muss.
    // Alle null, wenn TMDB nicht konfiguriert ist oder kein Treffer gefunden wurde.
    val backdropUrl: String? = null,
    val tmdbRating: Double? = null,
    val tmdbVoteCount: Int? = null,
    /** Hauptbesetzung, max. 6 Namen in TMDB-Reihenfolge. */
    val castNames: List<String>? = null
)

/**
 * Kommende Vorstellung: nicht als `film-past` markiert UND (Datum unbekannt ODER heute/später).
 * Spiegelt die Bedingung von [MovieDao.observeUpcoming].
 */
internal fun MovieEntity.isUpcoming(today: LocalDate): Boolean =
    !isPast && (date == null || !date.isBefore(today))
