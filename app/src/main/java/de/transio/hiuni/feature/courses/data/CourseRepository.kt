package de.transio.hiuni.feature.courses.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface CourseRepository {
    fun observeAll(): Flow<List<CourseEntity>>
    suspend fun findById(id: String): CourseEntity?
    suspend fun findByLsfId(lsfId: String): CourseEntity?
    suspend fun upsert(course: CourseEntity)
    suspend fun deleteById(id: String)
}

@Singleton
class CourseRepositoryImpl @Inject constructor(
    private val dao: CourseDao
) : CourseRepository {
    override fun observeAll(): Flow<List<CourseEntity>> = dao.observeAll()
    override suspend fun findById(id: String): CourseEntity? = dao.findById(id)
    override suspend fun findByLsfId(lsfId: String): CourseEntity? = dao.findByLsfId(lsfId)
    override suspend fun upsert(course: CourseEntity) = dao.upsert(course)
    override suspend fun deleteById(id: String) = dao.deleteById(id)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CourseRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindCourseRepository(impl: CourseRepositoryImpl): CourseRepository
}
