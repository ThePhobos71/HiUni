package de.transio.hiuni.feature.mensa.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface MealDao {

    @Query(
        "SELECT * FROM meals " +
            "WHERE date = :date AND locationId = :locationId " +
            "ORDER BY category ASC, name ASC"
    )
    fun observeForDate(date: LocalDate, locationId: Int): Flow<List<MealEntity>>

    @Query(
        "SELECT * FROM meals " +
            "WHERE date BETWEEN :from AND :to AND locationId = :locationId " +
            "ORDER BY date ASC, category ASC, name ASC"
    )
    fun observeRange(from: LocalDate, to: LocalDate, locationId: Int): Flow<List<MealEntity>>

    @Query("SELECT DISTINCT date FROM meals WHERE locationId = :locationId AND date >= :from ORDER BY date ASC")
    fun observeAvailableDates(locationId: Int, from: LocalDate): Flow<List<LocalDate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(meals: List<MealEntity>)

    @Query("DELETE FROM meals WHERE locationId = :locationId AND date < :before")
    suspend fun pruneOlderThan(locationId: Int, before: LocalDate)

    @Query("DELETE FROM meals WHERE locationId = :locationId AND date BETWEEN :from AND :to")
    suspend fun deleteRange(locationId: Int, from: LocalDate, to: LocalDate)

    @Transaction
    suspend fun replaceWindow(locationId: Int, from: LocalDate, to: LocalDate, meals: List<MealEntity>) {
        deleteRange(locationId, from, to)
        upsertAll(meals)
    }

    @Query("SELECT * FROM meals WHERE sourceId = :sourceId AND locationId = :locationId LIMIT 1")
    suspend fun findById(sourceId: String, locationId: Int): MealEntity?
}
