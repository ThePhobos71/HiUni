package de.transio.hiuni.core.sync

import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.di.ApplicationScope
import de.transio.hiuni.feature.email.data.EmailRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
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
                    Timber.i("LoginSyncOrchestrator: auth-transition → triggering LSF + email sync")
                    lsfSyncScheduler.triggerNow()
                    launch {
                        runCatching { emailRepository.refresh(force = true) }
                            .onFailure { Timber.w(it, "Post-Login Email-Refresh fehlgeschlagen") }
                    }
                }
                lastWasAuthenticated = isAuthenticated
            }
        }
    }
}
