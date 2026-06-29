package de.transio.hiuni.core.startup

import de.transio.hiuni.di.ApplicationScope
import de.transio.hiuni.feature.mensa.data.MensaRepository
import de.transio.hiuni.feature.movies.data.MoviesRepository
import de.transio.hiuni.feature.sport.data.SportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wärmt alle Login-freien Datenquellen direkt beim App-Start auf — auch
 * während des Onboardings, damit Mensa/Filme/Sport schon Daten haben wenn
 * der User durch die Setup-Slides klickt und das erste Mal auf einen dieser
 * Tabs tippt. LSF/Email werden bewusst NICHT hier getriggert: die brauchen
 * Auth und laufen via [de.transio.hiuni.core.sync.LoginSyncOrchestrator]
 * sobald CAS authenticated meldet.
 */
@Singleton
class StartupRefresher @Inject constructor(
    private val moviesRepository: MoviesRepository,
    private val mensaRepository: MensaRepository,
    private val sportRepository: SportRepository,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val triggered = AtomicBoolean(false)

    fun trigger() {
        if (!triggered.compareAndSet(false, true)) return
        // Jeweils eigene Coroutine — parallel, ein einzelner Fehler killt die
        // anderen nicht. force = false respektiert die Repository-Throttles
        // (5 Min Mensa, 6h Sport etc.), beim Cold-Start nach längerer
        // Zwangspause läuft der Refresh trotzdem durch.
        scope.launch {
            runCatching { moviesRepository.refresh(force = false) }
                .onFailure { Timber.w(it, "Startup-Refresh Movies fehlgeschlagen") }
        }
        scope.launch {
            runCatching { mensaRepository.refresh(force = false) }
                .onFailure { Timber.w(it, "Startup-Refresh Mensa fehlgeschlagen") }
        }
        scope.launch {
            runCatching { sportRepository.refresh(force = false) }
                .onFailure { Timber.w(it, "Startup-Refresh Sport fehlgeschlagen") }
        }
    }
}
