package de.transio.hiuni.feature.movies.data

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wärmt den Bild-Cache für das Kinoprogramm vor, damit Kino-Tab, Home-Karussell und
 * Film-Detail ihre Poster bzw. das Hero-Backdrop sofort aus dem Cache zeigen statt
 * erst beim Öffnen zu laden.
 */
interface MovieImagePrefetcher {
    /** Fire-and-forget: kehrt sofort zurück, Fehler werden nur geloggt. */
    fun prefetch(movies: List<MovieEntity>)
}

/**
 * Welche Bilder vorgeladen werden: nur kommende Vorstellungen, keine Überraschungsfilme
 * (die zeigen ohnehin den Platzhalter-Hero), jede URL nur einmal. Erst alle Poster
 * (Kino-Liste, Home-Karussell), dann die Backdrops (Detail-Hero), jeweils nach Datum
 * (ohne Datum zuletzt), damit bei einem Abbruch das Wichtigste schon im Cache liegt.
 */
internal fun movieImagePrefetchUrls(movies: List<MovieEntity>, today: LocalDate): List<String> {
    val relevant = movies
        .filter { it.isUpcoming(today) && !it.isSurpriseScreening() }
        .sortedWith(compareBy({ it.date == null }, { it.date }))
    return (relevant.map { it.posterUrl } + relevant.map { it.backdropUrl })
        .mapNotNull { url -> url?.trim()?.takeIf { it.isNotEmpty() } }
        .distinct()
}

/**
 * Lädt die Bilder über den App-weiten Coil-[coil.ImageLoader] (derselbe, den `AsyncImage`
 * nutzt) in dessen Disk-Cache. Der Memory-Cache bleibt bewusst aus: dessen Keys hängen an
 * der Zielgröße des jeweiligen Composables, ein Vorab-Bitmap würde dort also nie getroffen
 * und nur RAM belegen. Dekodiert wird deshalb nur winzig ([DECODE_SIZE_PX]), entscheidend
 * sind die Original-Bytes im Disk-Cache.
 *
 * Höflich: höchstens [MAX_PARALLEL] Downloads gleichzeitig. Bilder, die schon im
 * Disk-Cache liegen, kosten keinen erneuten Download, ein Refresh lädt also nur die
 * Bilder neuer Filme. Ein neuer Aufruf bricht einen noch laufenden Durchgang ab.
 */
@Singleton
class CoilMovieImagePrefetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) : MovieImagePrefetcher {

    private var running: Job? = null

    override fun prefetch(movies: List<MovieEntity>) {
        val urls = movieImagePrefetchUrls(movies, LocalDate.now())
        if (urls.isEmpty()) return
        synchronized(this) {
            running?.cancel()
            running = scope.launch { warm(urls) }
        }
    }

    private suspend fun warm(urls: List<String>) {
        val gate = Semaphore(MAX_PARALLEL)
        val loaded = coroutineScope {
            urls.map { url -> async { gate.withPermit { warmOne(url) } } }.awaitAll()
        }.count { it }
        Timber.d("Kino-Bilder vorgeladen: $loaded von ${urls.size}")
    }

    private suspend fun warmOne(url: String): Boolean {
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .size(DECODE_SIZE_PX)
            .build()
        return when (val result = context.imageLoader.execute(request)) {
            is SuccessResult -> true
            is ErrorResult -> {
                Timber.d(result.throwable, "Kino-Bild konnte nicht vorgeladen werden: $url")
                false
            }
        }
    }

    private companion object {
        const val MAX_PARALLEL = 3
        const val DECODE_SIZE_PX = 64
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MovieImagePrefetcherModule {

    @Binds
    @Singleton
    abstract fun bindMovieImagePrefetcher(impl: CoilMovieImagePrefetcher): MovieImagePrefetcher
}
