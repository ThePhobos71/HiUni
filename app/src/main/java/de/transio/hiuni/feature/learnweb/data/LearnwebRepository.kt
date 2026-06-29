package de.transio.hiuni.feature.learnweb.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.runCatchingApp
import de.transio.hiuni.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface LearnwebRepository {
    /** Persistierte Kurse als Stream — UI sortiert/filtert selbst falls nötig. */
    fun observeCourses(): Flow<List<LearnwebCourse>>

    /**
     * Holt das Dashboard, parst Kurse und upsertet sie. Vorhandene Kurse, die
     * nicht mehr im Sync auftauchen, werden gelöscht. Drosselt sich selbst auf
     * einmal pro [THROTTLE_MS]; `force = true` (Pull-to-Refresh) umgeht das.
     */
    suspend fun refresh(force: Boolean = false): AppResult<Unit>
}

@Singleton
class LearnwebRepositoryImpl @Inject constructor(
    private val client: LearnwebClient,
    private val scraper: LearnwebScraper,
    private val dao: LearnwebCourseDao,
    private val settings: SettingsDataStore,
    private val casSession: CasSession
) : LearnwebRepository {

    override fun observeCourses(): Flow<List<LearnwebCourse>> = dao.observeAll()

    override suspend fun refresh(force: Boolean): AppResult<Unit> = runCatchingApp {
        // Ohne CAS-Session geht der ganze Flow ohnehin auf die Nase. Lieber
        // früh aufgeben statt einen sinnlosen Login-Page-Roundtrip zu machen.
        if (casSession.state.value !is CasState.Authenticated) {
            Timber.d("LearnwebRepository.refresh: keine CAS-Session — abort")
            error("Keine CAS-Session — bitte zuerst über Einstellungen anmelden")
        }
        if (!force) {
            val lastRefresh = settings.lastLearnwebRefreshEpoch.first()
            val age = System.currentTimeMillis() - lastRefresh
            if (lastRefresh > 0 && age < THROTTLE_MS) {
                Timber.d("LearnwebRepository.refresh: throttled (age=${age / 1000}s < ${THROTTLE_MS / 1000}s)")
                return@runCatchingApp
            }
        }
        val html = client.fetchDashboardHtml()
        val parsed = scraper.parseCourses(html)
        Timber.i("LearnwebRepository: parsed ${parsed.size} courses from learnweb dashboard")

        val now = System.currentTimeMillis()
        val base = LearnwebClient.baseUrl()
        val entities = parsed.map { p ->
            val url = p.treeHref?.takeIf { it.startsWith("http") }
                ?: "$base/course/view.php?id=${p.courseId}"
            LearnwebCourse(
                courseId = p.courseId,
                name = p.name,
                url = url,
                syncedAt = now
            )
        }
        if (entities.isNotEmpty()) {
            dao.upsertAll(entities)
            dao.pruneNotIn(entities.map { it.courseId })
        } else {
            // Leer-Antwort ist ungewöhnlich — wir lassen den Bestand stehen,
            // statt versehentlich alles wegzulöschen (z.B. wenn das HTML wegen
            // Layout-Variation kein Course-Filter-Block hatte).
            Timber.w("LearnwebRepository: scraper lieferte 0 Kurse — DB bleibt unverändert")
        }
        settings.setLastLearnwebRefreshEpoch(now)
    }

    companion object {
        // 15 Minuten — Learnweb-Kursliste ändert sich pro Semester nur einmal,
        // aber pro Refresh fummeln wir an einem Uni-Server. 15 Min ist ein
        // konservativer Default; `force = true` umgeht die Drossel.
        private const val THROTTLE_MS = 15L * 60 * 1000
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LearnwebRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindLearnwebRepository(impl: LearnwebRepositoryImpl): LearnwebRepository
}
