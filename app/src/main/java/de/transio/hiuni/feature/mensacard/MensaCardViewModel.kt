package de.transio.hiuni.feature.mensacard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.nfc.NfcScanController
import de.transio.hiuni.feature.mensacard.data.CardReadResult
import de.transio.hiuni.feature.mensacard.data.CardSource
import de.transio.hiuni.feature.mensacard.data.MensaCardReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class MensaCardUiState(
    val valueMilliEuro: Int = -1,
    val uid: String = "",
    val source: String = "",
    val scannedAt: Instant? = null,
    val scanning: Boolean = false,
    val error: String? = null
) {
    val hasBalance: Boolean get() = valueMilliEuro >= 0
    val valueEuro: Double get() = valueMilliEuro / 1000.0
}

@HiltViewModel
class MensaCardViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val nfcController: NfcScanController,
    private val reader: MensaCardReader
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)

    val state: StateFlow<MensaCardUiState> = combine(
        settings.mensaCardBalanceMilliEuro,
        settings.mensaCardUid,
        settings.mensaCardSource,
        settings.mensaCardScannedEpoch,
        combine(nfcController.scanning, _error) { s, e -> s to e }
    ) { value, uid, source, epoch, scanningError ->
        val (scanning, error) = scanningError
        MensaCardUiState(
            valueMilliEuro = value,
            uid = uid,
            source = source,
            scannedAt = if (epoch > 0) Instant.ofEpochMilli(epoch) else null,
            scanning = scanning,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MensaCardUiState())

    init {
        // Tag-Stream beobachten: sobald ein Tag eintrifft (während scanning=true),
        // lesen wir ihn aus und persistieren das Ergebnis. Der Controller stellt
        // sicher, dass Tags nur weitergereicht werden, wenn ein Scan aktiv ist.
        viewModelScope.launch {
            nfcController.tags.collect { tag ->
                when (val result = reader.read(tag)) {
                    is CardReadResult.Success -> {
                        val scan = result.scan
                        settings.setMensaCardScan(
                            uid = scan.uid,
                            valueMilliEuro = scan.valueMilliEuro,
                            source = scan.source.name,
                            epoch = scan.scannedAt.toEpochMilli()
                        )
                        _error.value = null
                    }
                    is CardReadResult.Failure -> {
                        _error.value = humanize(result)
                    }
                }
                nfcController.stopScan()
            }
        }
    }

    fun startScan() {
        _error.value = null
        nfcController.startScan()
    }

    fun cancelScan() {
        nfcController.stopScan()
    }

    fun consumeError() { _error.value = null }

    fun sourceLabel(): String =
        if (state.value.source == CardSource.INTERCARD.name) CardSource.INTERCARD.label else ""

    private fun humanize(failure: CardReadResult.Failure): String = when (failure.reason) {
        CardReadResult.Reason.NOT_DESFIRE ->
            "Karte ist keine MIFARE DESfire. Bitte Hochschulkarte verwenden."
        CardReadResult.Reason.APP_NOT_FOUND ->
            "Keine Mensa-App auf der Karte gefunden."
        CardReadResult.Reason.AUTH_REQUIRED ->
            "Karte verlangt Authentifizierung — Guthaben nicht frei lesbar."
        CardReadResult.Reason.TRANSCEIVE_ERROR ->
            failure.detail ?: "Karte zu früh entfernt, bitte erneut auflegen."
        CardReadResult.Reason.UNKNOWN ->
            "Unbekannter Fehler beim Lesen der Karte."
    }
}
