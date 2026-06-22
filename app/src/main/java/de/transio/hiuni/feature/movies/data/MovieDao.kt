package de.transio.hiuni.feature.movies.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface MovieDao {

    /**
     * Aktuelle Filme: nicht als `film-past` markiert UND (Datum unbekannt ODER heute/in der Zukunft).
     * Filme deren Datum bereits vorbei ist, gelten als Archiv und werden hier ausgeblendet.
     */
    @Query(
        "SELECT * FROM movies " +
            "WHERE isPast = 0 AND (date IS NULL OR date >= :today) " +
            "ORDER BY date ASC, time ASC"
    )
    fun observeUpcoming(today: LocalDate): Flow<List<MovieEntity>>

    @Query(
        "SELECT * FROM movies " +
            "WHERE isPast = 1 OR (date IS NOT NULL AND date < :today) " +
            "ORDER BY date DESC, time DESC"
    )
    fun observeArchive(today: LocalDate): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies ORDER BY date ASC, time ASC")
    fun observeAll(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE filmId = :filmId AND sessionId = :sessionId LIMIT 1")
    suspend fun findById(filmId: String, sessionId: String): MovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(movies: List<MovieEntity>)

    @Query("DELETE FROM movies")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(movies: List<MovieEntity>) {
        deleteAll()
        upsertAll(movies)
    }
}
