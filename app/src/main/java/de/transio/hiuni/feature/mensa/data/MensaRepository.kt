package de.transio.hiuni.feature.mensa.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.runCatchingApp
import de.transio.hiuni.core.datastore.SettingsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface MensaRepository {
    fun observeForDate(date: LocalDate): Flow<List<MealEntity>>
    fun observeAvailableDates(from: LocalDate): Flow<List<LocalDate>>
    fun observeAnnouncements(date: LocalDate): Flow<List<Announcement>>
    suspend fun refresh(daysAhead: Int = 13): AppResult<Unit>
    suspend fun currentLocationId(): Int
}

@Singleton
class MensaRepositoryImpl @Inject constructor(
    private val dao: MealDao,
    private val api: MensaApiService,
    private val settings: SettingsDataStore
) : MensaRepository {

    // Announcements are not persisted — STW notices are timely, on next refresh they reload.
    private val announcementsState = MutableStateFlow<List<Announcement>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeForDate(date: LocalDate): Flow<List<MealEntity>> =
        settings.mensaLocationId.flatMapLatest { id -> dao.observeForDate(date, id) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAvailableDates(from: LocalDate): Flow<List<LocalDate>> =
        settings.mensaLocationId.flatMapLatest { id -> dao.observeAvailableDates(id, from) }

    override fun observeAnnouncements(date: LocalDate): Flow<List<Announcement>> =
        announcementsState.asStateFlow().map { list ->
            list.filter { it.covers(date) }.distinctBy { it.text }
        }

    override suspend fun refresh(daysAhead: Int): AppResult<Unit> = runCatchingApp {
        val locationId = settings.mensaLocationId.first()
        val from = LocalDate.now()
        val to = from.plusDays(daysAhead.toLong())
        val result = api.fetchMenu(locationId, from, to)
        dao.replaceWindow(locationId, from, to, result.meals)
        dao.pruneOlderThan(locationId, from)
        announcementsState.value = result.announcements
    }

    override suspend fun currentLocationId(): Int = settings.mensaLocationId.first()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MensaRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMensaRepository(impl: MensaRepositoryImpl): MensaRepository
}
