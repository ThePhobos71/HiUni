package de.transio.hiuni.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.database.AppDatabase
import de.transio.hiuni.core.database.MIGRATION_1_2
import de.transio.hiuni.core.database.MIGRATION_2_3
import de.transio.hiuni.feature.calendar.data.CustomEventDao
import de.transio.hiuni.feature.mensa.data.MealDao
import de.transio.hiuni.feature.movies.data.MovieDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideCustomEventDao(database: AppDatabase): CustomEventDao =
        database.customEventDao()

    @Provides
    fun provideMealDao(database: AppDatabase): MealDao =
        database.mealDao()

    @Provides
    fun provideMovieDao(database: AppDatabase): MovieDao =
        database.movieDao()
}
