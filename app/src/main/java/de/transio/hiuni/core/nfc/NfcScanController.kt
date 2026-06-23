package de.transio.hiuni.core.nfc

import android.nfc.Tag
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    // Replay 0: Activity ignoriert Tags, solange kein Scan aktiv ist.
    private val _tags = MutableSharedFlow<Tag>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val tags: SharedFlow<Tag> = _tags.asSharedFlow()

    fun startScan() { _scanning.value = true }
    fun stopScan() { _scanning.value = false }

    fun onTagReceived(tag: Tag) {
        if (_scanning.value) _tags.tryEmit(tag)
    }
}
