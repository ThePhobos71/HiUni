package de.transio.hiuni.core.nfc

import android.nfc.Tag
import de.transio.hiuni.core.common.OneShotSignal
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vermittelt zwischen ViewModel und MainActivity: das ViewModel signalisiert
 * "ich erwarte einen NFC-Scan", die Activity aktiviert dann den foreground
 * dispatch und leitet eintreffende Tags hier rein. Singleton damit beide
 * Seiten denselben Strom sehen.
 */
@Singleton
class NfcScanController @Inject constructor() {

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    // Live-Scans: Replay 0, Tags ohne aktiven Scan werden ignoriert. Ein Puffer
    // würde weitere Deliveries desselben Kontakts aufheben und zu einem zweiten
    // reader.read() führen ("Only one TagTechnology can be connected at a time").
    private val _tags = MutableSharedFlow<Tag>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Kaltstart-Tag: MainActivity reicht ihn in onCreate weiter, das
    // MensaCardViewModel sammelt aber erst nach der Navigation. Ein SharedFlow
    // ohne Abonnent würde ihn verwerfen, der konflatierte Channel hält ihn bis
    // zum Abholen, genau einmal. Mit Zeitstempel, damit ein liegengebliebener
    // Tag (z.B. App hing noch im Onboarding) später keinen Lesefehler auslöst.
    private class PendingTag(val tag: Tag, val receivedAtMs: Long)
    private val pendingTag = Channel<PendingTag>(Channel.CONFLATED)

    /** Monotone Uhr in ms, für Tests überschreibbar. */
    internal var clockMs: () -> Long = { System.nanoTime() / 1_000_000 }

    val tags: Flow<Tag> = merge(
        pendingTag.receiveAsFlow().mapNotNull { pending ->
            pending.tag.takeIf { clockMs() - pending.receivedAtMs <= PENDING_TAG_MAX_AGE_MS }
        },
        _tags
    )

    // Trigger "öffne MensaCardScreen" — wird gesetzt wenn die Activity über
    // ACTION_TECH_DISCOVERED gestartet/weitergeleitet wurde. NavGraph
    // observiert das und navigiert dahin.
    // OneShotSignal: beim Kaltstart per Karten-Auflegen hört der NavGraph noch
    // nicht zu, ein SharedFlow ohne Replay würde das Signal verwerfen.
    private val mensaCardSignal = OneShotSignal()
    val openMensaCard: Flow<Unit> = mensaCardSignal.events

    fun startScan() { _scanning.value = true }
    fun stopScan() { _scanning.value = false }

    fun onTagReceived(tag: Tag) {
        if (_scanning.value) _tags.tryEmit(tag)
    }

    /**
     * Beim Cold-Start oder externen NFC-Intent: scanning sofort aktivieren,
     * den Tag für das MensaCardViewModel bereitlegen (wartet bis zu
     * [PENDING_TAG_MAX_AGE_MS], falls es noch nicht sammelt) und UI auf
     * MensaCardScreen umschalten.
     * Umgeht das normale Scanning-Gating, weil der User explizit eine Karte
     * an's Phone gehalten hat und das eindeutig die Intention ist.
     */
    fun onUnsolicitedTag(tag: Tag) {
        _scanning.value = true
        pendingTag.trySend(PendingTag(tag, clockMs()))
        mensaCardSignal.fire()
    }

    companion object {
        /**
         * Wie lange ein Kaltstart-Tag auf das MensaCardViewModel wartet. Danach ist
         * die Karte praktisch sicher nicht mehr am Gerät, ein Lesen würde scheitern.
         */
        const val PENDING_TAG_MAX_AGE_MS = 30_000L
    }
}
