package de.transio.hiuni.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.transio.hiuni.feature.calendar.data.CustomEventDao
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.mensa.data.MealDao
import de.transio.hiuni.feature.mensa.data.MealEntity

@Database(
    entities = [
        CustomEventEntity::class,
        MealEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customEventDao(): CustomEventDao
    abstract fun mealDao(): MealDao

    companion object {
        const val DATABASE_NAME = "hiuni.db"
    }
}
