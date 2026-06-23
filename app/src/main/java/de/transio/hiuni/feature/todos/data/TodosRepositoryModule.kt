package de.transio.hiuni.feature.todos.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TodosRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTodosRepository(impl: TodosRepositoryImpl): TodosRepository
}
