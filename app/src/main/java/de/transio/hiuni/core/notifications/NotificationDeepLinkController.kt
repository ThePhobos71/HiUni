package de.transio.hiuni.core.notifications

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge zwischen MainActivity (parsed Notification-Tap-Intents) und
 * AppNavGraph (navigiert auf den passenden Screen). Singleton, weil beide
 * Seiten denselben Strom sehen müssen.
 *
 * `replay = 0` + `extraBufferCapacity = 1` matched die Konvention von
 * [de.transio.hiuni.core.nfc.NfcScanController]: bei Cold-Start wird die Emit
 * gepuffert bis der NavGraph den Collector startet, danach Standard-Hot-Flow.
 */
@Singleton
class NotificationDeepLinkController @Inject constructor() {

    private val _openCenter = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val openCenter: SharedFlow<Unit> = _openCenter.asSharedFlow()

    fun signalOpenCenter() {
        _openCenter.tryEmit(Unit)
    }
}
