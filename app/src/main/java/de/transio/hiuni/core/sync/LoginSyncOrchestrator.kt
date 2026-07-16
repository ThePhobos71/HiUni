package de.transio.hiuni.core.sync

import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.di.ApplicationScope
import de.transio.hiuni.feature.email.data.EmailRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Beobachtet den CAS-Auth-State app-weit und triggert bei jeder
 * `not-authenticated → authenticated`-Transition einen one-time LSF-Sync und
 * einen Email-Refresh.
 *
 * Lebt im Application-Lifecycle (Singleton + [ApplicationScope]), damit der
 * First-Login-Trigger auch dann läuft, wenn der User im Onboarding-Flow seinen
 * CAS-Login durchführt — also bevor die `CasLoginCard` (und ihr ViewModel) je
 * im Composable-Tree war.
 *
 * Beim App-Start (Cold-Boot mit bereits-authentifiziertem User) wird der
 * initiale Wert als "lastWasAuthenticated = true" gemerkt → kein spurious Sync.
 */
@Singleton
class LoginSyncOrchestrator @Inject constructor(
    private val casSession: CasSession,
    private val lsfSyncScheduler: LsfSyncScheduler,
    private val emailRepository: EmailRepository,
    private val settings: SettingsDataStore,
    private val prefetchOrchestrator: PrefetchOrchestrator,
    @ApplicationScope private val appScope: CoroutineScope
) {

    private val started = AtomicBoolean(false)
    private var collectorJob: Job? = null

    fun start() {
        // Idempotent: bei Process-Death und erneutem onCreate() würde sonst eine
        // zweite Collector-Coroutine laufen und jeden Trigger doppelt feuern.
        if (!started.compareAndSet(false, true)) {
            Timber.d("LoginSyncOrchestrator.start() already running — ignored")
            return
        }
        collectorJob = appScope.launch {
            var lastWasAuthenticated = casSession.state.value is CasState.Authenticated
            casSession.state.collect { current ->
                val isAuthenticated = current is CasState.Authenticated
                if (isAuthenticated && !lastWasAuthenticated) {
                    onAuthTransition()
                }
                lastWasAuthenticated = isAuthenticated
            }
        }
    }

    /**
     * Auth-Transition (not-auth → auth): bei einem **frischen** Login direkt
     * triggern, bei einem Cold-Start mit bereits gültigem TGC nur wenn der
     * letzte Sync länger als [MIN_RESYNC_INTERVAL_MS] her ist. Damit hämmern
     * wir LSF nicht bei jedem App-Open neu — der periodische Worker holt das
     * eh ein wenn nötig.
     */
    private suspend fun onAuthTransition() {
        val lastSync = runCatching { settings.lastLsfSyncEpoch.first() }.getOrDefault(0L)
        val age = Instant.now().toEpochMilli() - lastSync
        val shouldSync = lastSync == 0L || age >= MIN_RESYNC_INTERVAL_MS
        if (!shouldSync) {
            Timber.i(
                "LoginSyncOrchestrator: auth-transition, aber LSF-Sync vor %d Min — skip",
                age / 60_000
            )
            return
        }
        Timber.i("LoginSyncOrchestrator: auth-transition → triggering LSF + email sync")
        lsfSyncScheduler.triggerNow()
        appScope.launch {
            runCatching { emailRepository.refresh(force = true) }
                .onFailure { Timber.w(it, "Post-Login Email-Refresh fehlgeschlagen") }
        }
        // Nach einem frischen Login die restlichen Feature-Caches gestaffelt
        // vorwärmen (TTL-gated, überspringt frische Features). Bewusst HIER
        // eingehängt statt als zweiter Auth-State-Collector in der Application:
        // die Transition-Dedup-Logik (lastWasAuthenticated + MIN_RESYNC_INTERVAL)
        // lebt schon hier — ein paralleler Collector würde doppelt feuern.
        prefetchOrchestrator.prefetch()
    }

    private companion object {
        /**
         * Minimum-Abstand zwischen post-Login-Syncs. Bei jedem App-Cold-Start läuft
         * der Auth-State von Loading → Authenticated; ohne diese Schwelle würde
         * jeder Start einen Sync triggern. 6h ist konservativ; der reguläre
         * periodische Worker (Default 12h) bleibt davon unberührt.
         */
        const val MIN_RESYNC_INTERVAL_MS = 6L * 60 * 60 * 1000
    }
}
