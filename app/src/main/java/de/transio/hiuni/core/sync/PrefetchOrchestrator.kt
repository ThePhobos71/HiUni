package de.transio.hiuni.core.sync

import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.network.ConnectivityObserver
import de.transio.hiuni.di.ApplicationScope
import de.transio.hiuni.feature.bib.data.BibRepository
import de.transio.hiuni.feature.grades.data.GradesRepository
import de.transio.hiuni.feature.learnweb.data.LearnwebRepository
import de.transio.hiuni.feature.mensa.data.MensaRepository
import de.transio.hiuni.feature.movies.data.MoviesRepository
import de.transio.hiuni.feature.sport.data.SportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestaffelter Hintergrund-Warmup der Feature-Caches.
 *
 * PROBLEM: Jeder Screen (Mensa, Noten, Learnweb, Sport, Movies, Klausuren …) lud
 * beim Öffnen einzeln nach → der Nutzer sah bei jedem Screen-Wechsel erst ein
 * Skeleton, bis der jeweilige Repository-Refresh den Room-Cache gefüllt hatte.
 *
 * LÖSUNG: Beim App-Start und nach einem frischen Login läuft dieser Orchestrator
 * im [ApplicationScope] und refresht die Feature-Repositories NACHEINANDER mit
 * einer Staffelung ([STAGGER_DELAY_MS]), sodass der Room-Cache bereits „warm" ist,
 * wenn der Nutzer den Screen tatsächlich öffnet. Die Screens selbst laden dann
 * nur noch TTL-gated nach (siehe die ViewModel- bzw. Repository-Throttle).
 *
 * SCHONUNG DER UNI-SERVER: Die Staffelung verteilt die Requests zeitlich, und jede
 * Feature ist [ttl]-gated — es wird nur refresht, wenn `lastRefreshEpoch` älter als
 * die jeweilige Feature-TTL ist. Damit hämmern wir weder STW-ON noch das LSF.
 *
 * ABGRENZUNG:
 *  - Kein WorkManager-Umbau. Die bestehenden periodischen Worker
 *    ([LsfSyncScheduler] / [SportSyncScheduler]) bleiben unangetastet; das hier ist
 *    nur der zusätzliche Foreground-Warmup.
 *  - Exams/LSF wird NICHT über das Repository refresht, sondern über den bereits
 *    existierenden [LsfSyncScheduler] getriggert (dessen Periodic-Worker die
 *    Klausur-Phase mitnimmt) — kein zweiter LSF-Login-Roundtrip.
 *  - Email ist bewusst NICHT Teil des Prefetch: der Mail-Sync hängt am FCM-Push-
 *    Tickle-Flow und wird separat (im [LoginSyncOrchestrator]) behandelt.
 *  - Calendar bleibt außen vor (lokale Daten, kein Netz-Refresh).
 */
@Singleton
class PrefetchOrchestrator @Inject constructor(
    private val connectivity: ConnectivityObserver,
    private val casSession: CasSession,
    private val settings: SettingsDataStore,
    private val mensaRepository: MensaRepository,
    private val learnwebRepository: LearnwebRepository,
    private val gradesRepository: GradesRepository,
    private val sportRepository: SportRepository,
    private val moviesRepository: MoviesRepository,
    private val bibRepository: BibRepository,
    private val lsfSyncScheduler: LsfSyncScheduler,
    @ApplicationScope private val appScope: CoroutineScope
) {

    /** Verhindert überlappende Warmup-Läufe (App-Start + gleichzeitiger Login). */
    private val running = AtomicBoolean(false)

    /**
     * Stößt einen einmaligen, gestaffelten Warmup-Lauf an (fire-and-forget im
     * [ApplicationScope]). Idempotent: läuft bereits ein Warmup, kehrt der Aufruf
     * sofort zurück — der laufende Lauf holt die frischen Daten ohnehin.
     */
    fun prefetch(): Job? {
        if (!running.compareAndSet(false, true)) {
            Timber.d("PrefetchOrchestrator: Warmup läuft bereits — Aufruf ignoriert")
            return null
        }
        return appScope.launch {
            try {
                runWarmup()
            } finally {
                running.set(false)
            }
        }
    }

    private suspend fun runWarmup() {
        if (!connectivity.isOnline.value) {
            Timber.i("Prefetch: offline — Warmup übersprungen")
            return
        }
        val authenticated = casSession.state.value is CasState.Authenticated
        Timber.i("Prefetch: Warmup gestartet (auth=$authenticated)")

        // Reihenfolge nach Nutzwert: Mensa (tägliche Info) zuerst, Movies zuletzt.
        // Auth-pflichtige Features werden bei fehlender Session einfach übersprungen.

        // 1) Mensa (kein Auth nötig).
        step(Feature.MENSA, TTL_MENSA_MS, requiresAuth = false, authenticated) {
            mensaRepository.refresh(force = false)
        }

        // 2) Klausuren/LSF — über den bestehenden Periodic-Worker-Scheduler,
        //    nicht über einen zweiten Repository-Login.
        step(Feature.EXAMS, TTL_EXAMS_MS, requiresAuth = true, authenticated) {
            lsfSyncScheduler.triggerNow()
        }

        // 3) Learnweb (Auth).
        step(Feature.LEARNWEB, TTL_LEARNWEB_MS, requiresAuth = true, authenticated) {
            learnwebRepository.refresh(force = false)
        }

        // 4) Noten (Auth).
        step(Feature.GRADES, TTL_GRADES_MS, requiresAuth = true, authenticated) {
            gradesRepository.refresh(force = false)
        }

        // 5) Sport (kein Auth).
        step(Feature.SPORT, TTL_SPORT_MS, requiresAuth = false, authenticated) {
            sportRepository.refresh(force = false)
        }

        // 6) Movies (kein Auth).
        step(Feature.MOVIES, TTL_MOVIES_MS, requiresAuth = false, authenticated) {
            moviesRepository.refresh(force = false)
        }

        // 7) Bib-Snapshot (Auth für eigene Buchungen; anonym ginge auch, aber der
        //    Warmup ist nur dann wertvoll, wenn der User eingeloggt ist).
        step(Feature.BIB, TTL_BIB_MS, requiresAuth = true, authenticated) {
            bibRepository.refreshIfStale(TTL_BIB_MS)
        }

        Timber.i("Prefetch: Warmup abgeschlossen")
    }

    /**
     * Führt einen TTL-gegateten Refresh-Schritt aus:
     *  - Auth-Gate: `requiresAuth`-Features werden ohne Session übersprungen.
     *  - TTL-Gate: nur refreshen, wenn der letzte Refresh älter als [ttl] ist.
     *  - Fehler werden in [runCatching] gekapselt — ein Feature-Fehler darf die
     *    Kette nicht abreißen.
     *  - Nach jedem tatsächlich ausgeführten Refresh wird gestaffelt gewartet,
     *    damit die Uni-Server nicht in einem Rutsch getroffen werden.
     */
    private suspend fun step(
        feature: Feature,
        ttl: Long,
        requiresAuth: Boolean,
        authenticated: Boolean,
        refresh: suspend () -> Unit
    ) {
        if (requiresAuth && !authenticated) {
            Timber.d("Prefetch[%s]: keine Session — übersprungen", feature.tag)
            return
        }
        val lastRefresh = runCatching { feature.lastRefreshEpoch() }.getOrDefault(0L)
        val age = System.currentTimeMillis() - lastRefresh
        if (lastRefresh > 0 && age < ttl) {
            Timber.d(
                "Prefetch[%s]: frisch (Alter %ds < TTL %ds) — übersprungen",
                feature.tag, age / 1000, ttl / 1000
            )
            return
        }
        val ok = runCatching { refresh() }
            .onFailure { Timber.w(it, "Prefetch[%s]: Refresh fehlgeschlagen", feature.tag) }
            .isSuccess
        if (ok) {
            Timber.i("Prefetch[%s]: refreshed", feature.tag)
            // Staffelung nur NACH einem echten Refresh — übersprungene Features
            // sollen die Kette nicht künstlich verlangsamen.
            delay(STAGGER_DELAY_MS)
        }
    }

    private suspend fun Feature.lastRefreshEpoch(): Long = when (this) {
        Feature.MENSA -> settings.lastMensaRefreshEpoch.first()
        Feature.EXAMS -> settings.lastLsfExamsRefreshEpoch.first()
        Feature.LEARNWEB -> settings.lastLearnwebRefreshEpoch.first()
        Feature.GRADES -> settings.lastGradesRefreshEpoch.first()
        Feature.SPORT -> settings.lastSportRefreshEpoch.first()
        Feature.MOVIES -> settings.lastMoviesRefreshEpoch.first()
        // Bib hat keinen DataStore-Epoch; die Staleness steckt im File-Cache und
        // wird in BibRepository.refreshIfStale() selbst geprüft. Hier immer "stale"
        // melden, damit der Schritt bis zur Repo-Prüfung durchläuft.
        Feature.BIB -> 0L
    }

    private enum class Feature(val tag: String) {
        MENSA("mensa"),
        EXAMS("exams"),
        LEARNWEB("learnweb"),
        GRADES("grades"),
        SPORT("sport"),
        MOVIES("movies"),
        BIB("bib")
    }

    companion object {
        /**
         * Verzögerung zwischen zwei tatsächlich ausgeführten Feature-Refreshes.
         * Verteilt die Netz-Requests zeitlich, damit STW-ON/LSF/unifilm nicht in
         * einem Rutsch getroffen werden.
         */
        const val STAGGER_DELAY_MS = 3_000L

        // ── Feature-TTLs (Millis) ────────────────────────────────────────────
        // Ein Feature wird nur refresht, wenn der letzte erfolgreiche Refresh
        // älter als seine TTL ist. Die Werte spiegeln den Aktualitätsbedarf:
        // Learnweb/Klausuren/Noten ändern sich häufiger als Mensa; Sport/Movies
        // sehr selten. Diese Konstanten sind die EINE Quelle der Wahrheit — die
        // TTL-Gates im Orchestrator UND die Cold-Start-Gates in den ViewModels
        // (bzw. Repositories) referenzieren sie.

        /** Mensa: Menüplan ändert sich täglich, aber selten mehrmals pro Tag. */
        const val TTL_MENSA_MS = 6L * 60 * 60 * 1000

        /** Klausuren: LSF-Termine ändern sich selten; 6h ist konservativ. */
        const val TTL_EXAMS_MS = 6L * 60 * 60 * 1000

        /** Learnweb: Kurse/Deadlines können sich mehrmals täglich ändern. */
        const val TTL_LEARNWEB_MS = 1L * 60 * 60 * 1000

        /** Noten: neue Einträge sind zeitkritisch, aber nicht minütlich. */
        const val TTL_GRADES_MS = 6L * 60 * 60 * 1000

        /** Sport: Kursplan ist über die Woche stabil. */
        const val TTL_SPORT_MS = 12L * 60 * 60 * 1000

        /** Movies: unifilm.de aktualisiert das Programm nur wöchentlich. */
        const val TTL_MOVIES_MS = 12L * 60 * 60 * 1000

        /** Bib: Belegungs-Snapshot; halbtägig reicht für den Warmup. */
        const val TTL_BIB_MS = 12L * 60 * 60 * 1000
    }
}
