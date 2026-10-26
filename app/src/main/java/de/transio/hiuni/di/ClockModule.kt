package de.transio.hiuni.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.common.SystemZoneClock
import java.time.Clock

/**
 * Stellt die Uhr für zeitabhängige Flows bereit. In Tests wird stattdessen eine
 * virtuelle Uhr direkt in den Konstruktor gereicht.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    fun provideClock(): Clock = SystemZoneClock
}
