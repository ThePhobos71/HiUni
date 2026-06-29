package de.transio.hiuni.core.startup

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.transio.hiuni.di.ApplicationScope
import de.transio.hiuni.feature.mensa.data.MensaRepository
import de.transio.hiuni.feature.movies.data.MoviesRepository
import de.transio.hiuni.feature.sport.data.SportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
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
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val triggered = AtomicBoolean(false)

    fun trigger() {
        if (!triggered.compareAndSet(false, true)) return
        // Jeweils eigene Coroutine — parallel, ein einzelner Fehler killt die
        // anderen nicht. force = false respektiert die Repository-Throttles.
        scope.launch {
            runCatching {
                moviesRepository.refresh(force = false)
                // Direkt nach erfolgreichem Refresh die Poster-URLs in Coils
                // Disk-Cache vorladen — sonst poppen die Bilder beim ersten
                // Tab-Open sichtbar nach. ImageLoader.enqueue ist fire-and-
                // forget, gewährt Coil intern Throttling.
                prefetchMoviePosters()
            }.onFailure { Timber.w(it, "Startup-Refresh Movies fehlgeschlagen") }
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

    private suspend fun prefetchMoviePosters() {
        runCatching {
            val movies = moviesRepository.observeUpcoming().first()
            val urls = movies.mapNotNull { it.posterUrl?.takeIf { url -> url.isNotBlank() } }
            urls.take(MAX_POSTER_PREFETCH).forEach { url ->
                imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(url)
                        .build()
                )
            }
            Timber.i("Startup-Prefetch: %d Movie-Poster in Coil-Cache geladen", urls.size)
        }.onFailure { Timber.w(it, "Movie-Poster-Prefetch fehlgeschlagen") }
    }

    private companion object {
        /** Mehr als 12 anstehende Filme gibt's selten — alles drüber wäre Cache-Verschwendung. */
        const val MAX_POSTER_PREFETCH = 12
    }
}
