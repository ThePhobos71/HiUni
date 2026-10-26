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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface MensaRepository {
    fun observeForDate(date: LocalDate): Flow<List<MealEntity>>
    fun observeAvailableDates(from: LocalDate): Flow<List<LocalDate>>
    fun observeAnnouncements(date: LocalDate): Flow<List<Announcement>>
    /**
     * Zuletzt bekannte Öffnungszeiten der aktuell gewählten Location (persistiert,
     * leer = noch nie geladen, dann greifen die Fallback-Slots in [MensaHours]).
     */
    fun observeOpeningHours(): Flow<List<OpeningHourBlock>>
    /**
     * Liefert das Such-Korpus: alle Mahlzeiten ab heute bis +`daysAhead` Tage für
     * die aktuell gewählte Mensa-Location. Klein genug (~70 Einträge) für In-Memory-Match.
     */
    fun observeSearchWindow(daysAhead: Int = 13): Flow<List<MealEntity>>
    /**
     * Holt das STW-ON Menü für die nächsten `daysAhead` Tage. `force = false` überspringt,
     * wenn der letzte Refresh < 6 h alt ist. Pull-to-Refresh setzt `force = true`.
     */
    suspend fun refresh(daysAhead: Int = 13, force: Boolean = false): AppResult<Unit>
    suspend fun currentLocationId(): Int
}

@Singleton
class MensaRepositoryImpl @Inject constructor(
    private val dao: MealDao,
    private val api: MensaApiService,
    private val settings: SettingsDataStore,
    private val extras: MensaExtrasStore
) : MensaRepository {

    private companion object {
        const val THROTTLE_MS = 6L * 60 * 60 * 1000 // 6 Stunden
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeForDate(date: LocalDate): Flow<List<MealEntity>> =
        settings.mensaLocationId.flatMapLatest { id -> dao.observeForDate(date, id) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAvailableDates(from: LocalDate): Flow<List<LocalDate>> =
        settings.mensaLocationId.flatMapLatest { id -> dao.observeAvailableDates(id, from) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSearchWindow(daysAhead: Int): Flow<List<MealEntity>> =
        settings.mensaLocationId.flatMapLatest { id ->
            val from = LocalDate.now()
            val to = from.plusDays(daysAhead.toLong())
            dao.observeRange(from, to, id)
        }

    // Hinweise kommen aus dem persistenten Cache, damit sie nicht erst nach dem
    // Netz-Refresh aufpoppen. Veraltete Einträge filtert `covers(date)` weg.
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAnnouncements(date: LocalDate): Flow<List<Announcement>> =
        settings.mensaLocationId.flatMapLatest { id ->
            extras.observeAnnouncements(id).map { list ->
                list.filter { it.covers(date) }.distinctBy { it.text }
            }
        }

    // Persistierte Öffnungszeiten wärmen nebenbei den In-Memory-Lookup in MensaHours,
    // damit auch Aufrufer ohne eigene Blöcke (Home-Kachel) die echten Zeiten sehen.
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOpeningHours(): Flow<List<OpeningHourBlock>> =
        settings.mensaLocationId.flatMapLatest { id ->
            extras.observeOpeningHours(id).onEach { MensaHours.updateFromApi(id, it) }
        }

    override suspend fun refresh(daysAhead: Int, force: Boolean): AppResult<Unit> = runCatchingApp {
        if (!force) {
            val lastRefresh = settings.lastMensaRefreshEpoch.first()
            val age = System.currentTimeMillis() - lastRefresh
            if (lastRefresh > 0 && age < THROTTLE_MS) {
                return@runCatchingApp
            }
        }
        val locationId = settings.mensaLocationId.first()
        val from = LocalDate.now()
        val to = from.plusDays(daysAhead.toLong())
        val result = api.fetchMenu(locationId, from, to)
        dao.replaceWindow(locationId, from, to, result.meals)
        dao.pruneOlderThan(locationId, from)
        extras.saveAnnouncements(locationId, result.announcements)
        extras.saveOpeningHours(locationId, result.openingHours)
        settings.setLastMensaRefreshEpoch(System.currentTimeMillis())
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
