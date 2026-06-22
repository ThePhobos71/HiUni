package de.transio.hiuni.core.startup

import de.transio.hiuni.di.ApplicationScope
import de.transio.hiuni.feature.mensa.data.MensaRepository
import de.transio.hiuni.feature.movies.data.MoviesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartupRefresher @Inject constructor(
    private val moviesRepository: MoviesRepository,
    private val mensaRepository: MensaRepository,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val triggered = AtomicBoolean(false)

    fun trigger() {
        if (!triggered.compareAndSet(false, true)) return
        scope.launch { moviesRepository.refresh(force = false) }
        scope.launch { mensaRepository.refresh(force = false) }
    }
}
