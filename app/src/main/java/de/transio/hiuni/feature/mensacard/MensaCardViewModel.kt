package de.transio.hiuni.feature.mensacard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.nfc.NfcScanController
import de.transio.hiuni.feature.mensacard.data.CardReadResult
import de.transio.hiuni.feature.mensacard.data.CardSource
import de.transio.hiuni.feature.mensacard.data.MensaCardReader
import de.transio.hiuni.feature.mensacard.data.MensaCardScan
import de.transio.hiuni.feature.mensacard.data.MensaCardTransactionDao
import de.transio.hiuni.feature.mensacard.data.MensaCardTransactionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * UI-Sicht der eigenen Mensa-Karte. [primaryUid] ist die UID die der User
 * explizit als "meine Karte" festgelegt hat; ohne diese Markierung läuft jede
 * frische Karte als [transientScan] in der Friend-View durch, ohne Verlauf
 * zu pollen.
 */
data class MensaCardUiState(
    val primaryUid: String = "",
    val primaryBalanceMilliEuro: Int = -1,
    val primarySource: String = "",
    val primaryScannedAt: Instant? = null,
    val lastTransaction: MensaCardTransactionEntity? = null,
    val history: List<MensaCardTransactionEntity> = emptyList(),
    val stats: MensaCardStats = MensaCardStats(),
    val onCardLastDebitMilliEuro: Int = 0,
    val transientScan: TransientScan? = null,
    val scanning: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null
) {
    val hasPrimary: Boolean get() = primaryUid.isNotBlank()
    val hasBalance: Boolean get() = primaryBalanceMilliEuro >= 0
    val primaryBalanceEuro: Double get() = primaryBalanceMilliEuro / 1000.0
    val hasOnCardLastDebit: Boolean get() = onCardLastDebitMilliEuro > 0
}

/**
 * Periodische Spending-Stats für die Primärkarte. Zwischen zwei Scans
 * können mehrere Buchungen passieren — wir können sie nicht einzeln
 * rekonstruieren, summieren also netto-Abbuchungen pro Zeitraum.
 *
 * - [thisWeekMilliEuro]: Summe von |delta| für Einträge mit delta < 0 in
 *   den letzten 7 Tagen.
 * - [thisMonthMilliEuro]: dito für die letzten 30 Tage.
 * - [totalMilliEuro]: dito über alle bisherigen Scans.
 * - [scanCount]: Anzahl beobachteter Scan-Ereignisse mit Saldo-Änderung
 *   (NICHT Anzahl Mensa-Buchungen — zwischen zwei Scans können mehrere
 *   Buchungen liegen).
 * - [periodFrom]: Zeitstempel des ersten beobachteten Scans.
 */
data class MensaCardStats(
    val thisWeekMilliEuro: Int = 0,
    val thisMonthMilliEuro: Int = 0,
    val totalMilliEuro: Int = 0,
    val scanCount: Int = 0,
    val periodFrom: Instant? = null
) {
    val hasData: Boolean get() = totalMilliEuro > 0
}

/**
 * Frischer Scan, der entweder gerade eben passiert ist (zur kurzen Anzeige)
 * oder eine fremde Karte ist die nie persistiert wird. [isOwn]=false heißt
 * "Fremde Karte" und blockiert den Verlauf der eigenen Karte.
 */
data class TransientScan(
    val scan: MensaCardScan,
    val isOwn: Boolean
) {
    val valueEuro: Double get() = scan.valueMilliEuro / 1000.0
}

@HiltViewModel
class MensaCardViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val nfcController: NfcScanController,
    private val reader: MensaCardReader,
    private val transactionDao: MensaCardTransactionDao
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    private val _transientScan = MutableStateFlow<TransientScan?>(null)
    private val _isRefreshing = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val historyFlow = settings.mensaCardPrimaryUid.flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(emptyList()) else transactionDao.observeLatest(uid)
    }

    val state: StateFlow<MensaCardUiState> = combine(
        combine(
            settings.mensaCardPrimaryUid,
            settings.mensaCardBalanceMilliEuro,
            settings.mensaCardSource,
            settings.mensaCardScannedEpoch,
            settings.mensaCardOnCardLastDebitMilliEuro
        ) { uid, value, source, epoch, onCardLastDebit ->
            CoreSettings(uid, value, source, epoch, onCardLastDebit)
        },
        historyFlow,
        nfcController.scanning,
        _transientScan,
        combine(_error, _isRefreshing) { error, refreshing -> error to refreshing }
    ) { core, history, scanning, transient, errorAndRefreshing ->
        val (error, refreshing) = errorAndRefreshing
        MensaCardUiState(
            primaryUid = core.uid,
            primaryBalanceMilliEuro = core.value,
            primarySource = core.source,
            primaryScannedAt = if (core.epoch > 0) Instant.ofEpochMilli(core.epoch) else null,
            lastTransaction = history.firstOrNull { it.deltaMilliEuro != 0 },
            history = history,
            stats = computeStats(history),
            onCardLastDebitMilliEuro = core.onCardLastDebit,
            transientScan = transient,
            scanning = scanning,
            // Sobald der `combine`-Block überhaupt läuft, haben alle Quellen
            // (DataStore + Room) mindestens einmal emittiert — Erstladen ist
            // damit per Definition vorbei.
            isLoading = false,
            isRefreshing = refreshing,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), MensaCardUiState())

    init {
        viewModelScope.launch {
            nfcController.tags.collect { tag ->
                // Scanning SOFORT stoppen, damit weitere Tag-Deliveries beim
                // selben physischen Kontakt nicht in den SharedFlow gepuffert
                // werden und nach Abschluss zu einem zweiten reader.read()
                // führen → das knallt mit "Only one tag technology can be
                // connected at a time", weil der Chip noch belegt ist.
                nfcController.stopScan()
                when (val result = reader.read(tag)) {
                    is CardReadResult.Success -> {
                        _error.value = null
                        handleScan(result.scan)
                    }
                    is CardReadResult.Failure -> _error.value = humanize(result)
                }
                // Auto-rearm: wenn kein Friend-Banner Aufmerksamkeit braucht,
                // wieder lauschen — so kann der User direkt nochmal scannen
                // ohne erst auf den Reader zu tippen.
                if (_transientScan.value == null) {
                    nfcController.startScan()
                }
            }
        }
    }

    fun startScan() {
        _error.value = null
        _transientScan.value = null
        nfcController.startScan()
    }

    fun cancelScan() {
        nfcController.stopScan()
    }

    /**
     * Pull-to-Refresh: es gibt hier keinen Netzwerk-Sync — Guthaben/Verlauf
     * kommen ausschließlich vom NFC-Scan. "Refresh" heißt daher: Scan neu
     * scharfschalten + Fehler zurücksetzen, damit der User die Karte einfach
     * nochmal auflegen kann. Indicator bleibt kurz sichtbar als Feedback.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            startScan()
            delay(600L)
            _isRefreshing.value = false
        }
    }

    fun consumeError() { _error.value = null }

    fun dismissTransient() { _transientScan.value = null }

    /**
     * Legt die zuletzt gescannte fremde Karte als eigene Primärkarte fest.
     * Hilfreich für Erst-Scan ("auto-adopt") UND für späteren Wechsel auf
     * eine neue Karte (z. B. Ersatzkarte nach Verlust).
     */
    fun adoptTransientAsPrimary() = viewModelScope.launch {
        val transient = _transientScan.value ?: return@launch
        persistAsOwnCard(transient.scan)
        _transientScan.value = null
    }

    fun sourceLabel(): String =
        if (state.value.primarySource == CardSource.INTERCARD.name) CardSource.INTERCARD.label else ""

    private suspend fun handleScan(scan: MensaCardScan) {
        val primaryUid = settings.mensaCardPrimaryUid.first()
        when {
            primaryUid.isBlank() -> {
                // Noch keine Karte festgelegt → Karte als unentschieden in die
                // transient-Sicht legen. User entscheidet via "Als meine festlegen".
                _transientScan.value = TransientScan(scan, isOwn = false)
            }
            scan.uid == primaryUid -> {
                // Eigene Karte → Verlauf updaten. Kein transient nötig — der
                // neue Balance + lastTransaction sind das Feedback.
                persistAsOwnCard(scan)
                _transientScan.value = null
            }
            else -> {
                // Fremde Karte → nur transient, NICHT in Verlauf der eigenen Karte.
                _transientScan.value = TransientScan(scan, isOwn = false)
            }
        }
    }

    private suspend fun persistAsOwnCard(scan: MensaCardScan) {
        val previous = transactionDao.latestFor(scan.uid)
        val newEpoch = scan.scannedAt.toEpochMilli()
        val cardDebit = scan.lastDebitAmountMilliEuro
        if (previous != null && previous.balanceMilliEuro == scan.valueMilliEuro) {
            // Saldo unverändert → nur den Zeitstempel des letzten Eintrags
            // hochziehen. Kein neuer Verlaufseintrag, keine Duplikate.
            transactionDao.updateScannedAt(previous.id, newEpoch)
        } else {
            val delta = if (previous == null) 0 else scan.valueMilliEuro - previous.balanceMilliEuro
            transactionDao.insert(
                MensaCardTransactionEntity(
                    uid = scan.uid,
                    balanceMilliEuro = scan.valueMilliEuro,
                    deltaMilliEuro = delta,
                    scannedAt = newEpoch,
                    // Chip-bestätigter Abbuchungsbetrag — bei Abbuchungen sollte
                    // er |delta| matchen, sonst ist zwischen unseren Scans
                    // mehr passiert als nur ein Vorgang.
                    cardLastDebitMilliEuro = cardDebit?.takeIf { it > 0 }
                )
            )
        }
        settings.setMensaCardPrimaryUid(scan.uid)
        settings.setMensaCardScan(
            uid = scan.uid,
            valueMilliEuro = scan.valueMilliEuro,
            source = scan.source.name,
            epoch = newEpoch
        )
        // DESfire LimitedCreditValue aus File-Settings = Betrag der letzten
        // Abbuchung (on-chip persistiert vom Bezahl-Terminal). Auch wenn der
        // Verlaufseintrag dedupliziert wird, den letzten Debit-Betrag
        // separat hochziehen, damit das Header-Banner aktuell bleibt.
        cardDebit?.takeIf { it > 0 }?.let { settings.setMensaCardOnCardLastDebitMilliEuro(it) }
    }

    private fun humanize(failure: CardReadResult.Failure): String = when (failure.reason) {
        CardReadResult.Reason.NOT_DESFIRE ->
            "Karte ist keine MIFARE DESfire. Bitte Hochschulkarte verwenden."
        CardReadResult.Reason.APP_NOT_FOUND ->
            "Keine Intercard-App auf dieser Karte gefunden."
        CardReadResult.Reason.AUTH_REQUIRED ->
            "Karte verlangt Authentifizierung — Guthaben nicht frei lesbar."
        CardReadResult.Reason.TRANSCEIVE_ERROR ->
            failure.detail ?: "Karte zu früh entfernt, bitte erneut auflegen."
        CardReadResult.Reason.UNKNOWN ->
            "Unbekannter Fehler beim Lesen der Karte."
    }

    private fun computeStats(history: List<MensaCardTransactionEntity>): MensaCardStats {
        val withdrawals = history.filter { it.deltaMilliEuro < 0 }
        if (withdrawals.isEmpty()) return MensaCardStats()
        val now = System.currentTimeMillis()
        val weekAgo = now - 7L * 24 * 60 * 60 * 1000
        val monthAgo = now - 30L * 24 * 60 * 60 * 1000
        val thisWeek = withdrawals.filter { it.scannedAt >= weekAgo }.sumOf { -it.deltaMilliEuro }
        val thisMonth = withdrawals.filter { it.scannedAt >= monthAgo }.sumOf { -it.deltaMilliEuro }
        val total = withdrawals.sumOf { -it.deltaMilliEuro }
        return MensaCardStats(
            thisWeekMilliEuro = thisWeek,
            thisMonthMilliEuro = thisMonth,
            totalMilliEuro = total,
            scanCount = withdrawals.size,
            periodFrom = Instant.ofEpochMilli(withdrawals.minOf { it.scannedAt })
        )
    }

    private data class CoreSettings(
        val uid: String,
        val value: Int,
        val source: String,
        val epoch: Long,
        val onCardLastDebit: Int
    )
}
