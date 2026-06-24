package de.transio.hiuni.feature.sport.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.runCatchingApp
import de.transio.hiuni.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface SportRepository {
    /** Alle anstehenden Termine ("anstehend" = endTime > jetzt), früheste zuerst. */
    fun observeUpcoming(): Flow<List<SportEventEntity>>

    /** Distinkte Titel der zukünftigen Termine — Quelle für die Filter-Chips. */
    fun observeDistinctTitles(): Flow<List<String>>

    /** Live-Zähler für die Home-Quick-Access-Kachel. */
    fun countUpcoming(): Flow<Int>

    /** Detail-Screen-Quelle: einzelner Termin via stabiler supersaas-Slot-ID. */
    fun observeBySlotId(slotId: Long): Flow<SportEventEntity?>

    /**
     * Holt den Plan vom supersaas-Server. Drosselt sich selbst auf einmal pro
     * [THROTTLE_MS]; `force = true` (Pull-to-Refresh, Worker-OneTime) umgeht das.
     */
    suspend fun refresh(force: Boolean = false): AppResult<Unit>
}

@Singleton
class SportRepositoryImpl @Inject constructor(
    private val dao: SportDao,
    private val scraper: SportScraper,
    private val settings: SettingsDataStore
) : SportRepository {

    override fun observeUpcoming(): Flow<List<SportEventEntity>> =
        dao.observeUpcoming(Instant.now().toEpochMilli())

    override fun observeDistinctTitles(): Flow<List<String>> =
        dao.observeDistinctTitles(Instant.now().toEpochMilli())

    override fun countUpcoming(): Flow<Int> =
        dao.countUpcoming(Instant.now().toEpochMilli())

    override fun observeBySlotId(slotId: Long): Flow<SportEventEntity?> =
        dao.observeBySlotId(slotId)

    override suspend fun refresh(force: Boolean): AppResult<Unit> = runCatchingApp {
        if (!force) {
            val lastRefresh = settings.lastSportRefreshEpoch.first()
            val age = System.currentTimeMillis() - lastRefresh
            if (lastRefresh > 0 && age < THROTTLE_MS) {
                Timber.d("SportRepository: refresh übersprungen (Alter ${age / 1000}s < ${THROTTLE_MS / 1000}s)")
                return@runCatchingApp
            }
        }
        val events = scraper.fetch()
        if (events.isEmpty()) {
            // Plan-Fenster echt leer? Eher Parsing-Edge-Case — wir lassen den
            // bisherigen Bestand stehen statt alles zu löschen.
            Timber.w("SportRepository: 0 Events geparst — DB wird nicht ersetzt")
            settings.setLastSportRefreshEpoch(System.currentTimeMillis())
            return@runCatchingApp
        }
        dao.upsertAll(events)
        // Vergangenheit > 7 Tage rausschmeißen, damit die DB nicht beliebig wächst.
        val pruneCutoff = Instant.now().minusSeconds(7L * 24 * 3600).toEpochMilli()
        dao.pruneBefore(pruneCutoff)
        settings.setLastSportRefreshEpoch(System.currentTimeMillis())
        Timber.i("SportRepository: ${events.size} Events synchronisiert")
    }

    companion object {
        private const val THROTTLE_MS = 6L * 60 * 60 * 1000 // 6 Stunden
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SportRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindSportRepository(impl: SportRepositoryImpl): SportRepository
}
