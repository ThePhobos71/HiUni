package de.transio.hiuni.core.notifications.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationLogRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNotificationLogRepository(
        impl: NotificationLogRepositoryImpl
    ): NotificationLogRepository
}
