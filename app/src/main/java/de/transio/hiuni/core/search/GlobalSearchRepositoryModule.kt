package de.transio.hiuni.core.search

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DI-Binding für die App-weite Suche. Hilt würde zwar das @Singleton-Impl auch
 * direkt injizieren, aber das explizite Interface-Binding hält die Aufrufer
 * (ViewModel) testbar — bei Bedarf lässt sich der Repo per `@TestInstallIn` mit
 * einem Fake ersetzen.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GlobalSearchRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindGlobalSearchRepository(
        impl: GlobalSearchRepositoryImpl
    ): GlobalSearchRepository
}
