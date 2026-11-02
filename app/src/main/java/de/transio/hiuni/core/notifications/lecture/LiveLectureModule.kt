package de.transio.hiuni.core.notifications.lecture

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Bindet die Android-Seite der Vorlesungs-Mitteilungen und den Kurs-Einstellungs-Store an ihre Schnittstellen. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LiveLectureModule {

    @Binds
    @Singleton
    abstract fun bindNotifier(impl: AndroidLiveLectureNotifier): LiveLectureNotifier

    @Binds
    @Singleton
    abstract fun bindAlarms(impl: AndroidLiveLectureAlarms): LiveLectureAlarms

    @Binds
    @Singleton
    abstract fun bindCoursePrefs(impl: DataStoreCourseNotificationPrefsStore): CourseNotificationPrefsStore
}
