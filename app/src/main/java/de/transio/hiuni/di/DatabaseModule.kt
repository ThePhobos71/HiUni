package de.transio.hiuni.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.database.ALL_MIGRATIONS
import de.transio.hiuni.core.database.AppDatabase
import de.transio.hiuni.core.notifications.data.NotificationLogDao
import de.transio.hiuni.core.security.DatabaseKeyProvider
import de.transio.hiuni.feature.calendar.data.CustomEventDao
import de.transio.hiuni.feature.courses.data.CourseDao
import de.transio.hiuni.feature.email.data.EmailDao
import de.transio.hiuni.feature.mensa.data.MealDao
import de.transio.hiuni.feature.mensacard.data.MensaCardTransactionDao
import de.transio.hiuni.feature.movies.data.MovieDao
import de.transio.hiuni.feature.todos.data.TodoDao
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keyProvider: DatabaseKeyProvider
    ): AppDatabase {
        // SQLCipher Native Libs einmalig laden (System.loadLibrary).
        System.loadLibrary("sqlcipher")

        // Pre-SQLCipher Plaintext-DB einmalig wegwerfen.
        // Bestehende User-Daten (Custom-Events, Courses) gehen verloren — der
        // Plaintext-Pfad existierte nur in Dev-Builds vor Encryption-Rollout.
        val migrationFlag = context.applicationContext
            .getSharedPreferences(MIGRATION_PREF_FILE, Context.MODE_PRIVATE)
        if (!migrationFlag.getBoolean(KEY_ENCRYPTION_MIGRATED, false)) {
            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            if (dbFile.exists()) {
                Timber.i("Encryption rollout: deleting unencrypted DB at ${dbFile.path}")
                context.deleteDatabase(AppDatabase.DATABASE_NAME)
            }
            migrationFlag.edit().putBoolean(KEY_ENCRYPTION_MIGRATED, true).apply()
        }

        val factory = SupportOpenHelperFactory(keyProvider.getOrCreateKey())
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .openHelperFactory(factory)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @Provides
    fun provideCustomEventDao(database: AppDatabase): CustomEventDao =
        database.customEventDao()

    @Provides
    fun provideMealDao(database: AppDatabase): MealDao =
        database.mealDao()

    @Provides
    fun provideMovieDao(database: AppDatabase): MovieDao =
        database.movieDao()

    @Provides
    fun provideCourseDao(database: AppDatabase): CourseDao =
        database.courseDao()

    @Provides
    fun provideEmailDao(database: AppDatabase): EmailDao =
        database.emailDao()

    @Provides
    fun provideMensaCardTransactionDao(database: AppDatabase): MensaCardTransactionDao =
        database.mensaCardTransactionDao()

    @Provides
    fun provideTodoDao(database: AppDatabase): TodoDao =
        database.todoDao()

    @Provides
    fun provideNotificationLogDao(database: AppDatabase): NotificationLogDao =
        database.notificationLogDao()

    @Provides
    fun provideSportDao(database: AppDatabase): de.transio.hiuni.feature.sport.data.SportDao =
        database.sportDao()

    @Provides
    fun provideExamDao(database: AppDatabase): de.transio.hiuni.feature.lsf.data.ExamDao =
        database.examDao()

    private const val MIGRATION_PREF_FILE = "de.transio.hiuni.db_migration"
    private const val KEY_ENCRYPTION_MIGRATED = "encryption_v1_migrated"
}
