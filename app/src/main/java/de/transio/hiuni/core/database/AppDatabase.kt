package de.transio.hiuni.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.transio.hiuni.feature.calendar.data.CustomEventDao
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.courses.data.CourseDao
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.email.data.EmailDao
import de.transio.hiuni.feature.email.data.EmailEntity
import de.transio.hiuni.feature.lsf.data.ExamDao
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.mensa.data.MealDao
import de.transio.hiuni.feature.mensa.data.MealEntity
import de.transio.hiuni.core.notifications.data.NotificationLogDao
import de.transio.hiuni.core.notifications.data.NotificationLogEntity
import de.transio.hiuni.feature.mensacard.data.MensaCardTransactionDao
import de.transio.hiuni.feature.mensacard.data.MensaCardTransactionEntity
import de.transio.hiuni.feature.movies.data.MovieDao
import de.transio.hiuni.feature.movies.data.MovieEntity
import de.transio.hiuni.feature.sport.data.SportDao
import de.transio.hiuni.feature.sport.data.SportEventEntity
import de.transio.hiuni.feature.todos.data.TodoDao
import de.transio.hiuni.feature.todos.data.TodoEntity

@Database(
    entities = [
        CustomEventEntity::class,
        MealEntity::class,
        MovieEntity::class,
        CourseEntity::class,
        EmailEntity::class,
        MensaCardTransactionEntity::class,
        TodoEntity::class,
        NotificationLogEntity::class,
        SportEventEntity::class,
        ExamEntity::class
    ],
    version = 25,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customEventDao(): CustomEventDao
    abstract fun mealDao(): MealDao
    abstract fun movieDao(): MovieDao
    abstract fun courseDao(): CourseDao
    abstract fun emailDao(): EmailDao
    abstract fun mensaCardTransactionDao(): MensaCardTransactionDao
    abstract fun todoDao(): TodoDao
    abstract fun notificationLogDao(): NotificationLogDao
    abstract fun sportDao(): SportDao
    abstract fun examDao(): ExamDao

    companion object {
        const val DATABASE_NAME = "hiuni.db"
    }
}
