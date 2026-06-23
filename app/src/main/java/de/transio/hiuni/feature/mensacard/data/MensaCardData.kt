package de.transio.hiuni.feature.mensacard.data

import java.time.Instant

/**
 * Vollständiger Lesevorgang einer Mensa-Karte (Intercard DESfire). Wert wird
 * intern in 1/1000 € gespeichert (so liefert es Intercard auch direkt).
 *
 * [rawLastTransactionRecord]: 32 Bytes des neuesten Eintrags in File 2
 * (Cyclic Record File mit Transaktionshistorie). Bei DESfire Cyclic Files
 * ist Offset 0 immer der jüngste Eintrag. `null` heißt: Read war geblockt
 * (0xAE Auth Required) oder Karte hat keinen Verlauf.
 *
 * [lastDebitAmountMilliEuro]: Die DESfire "LimitedCreditValue" aus den File
 * Settings (`GET_FILE_SETTINGS 0xF5` auf File 1, Bytes 12-15 als Int32 LE).
 * Dieser Wert wird nach jeder Abbuchung auf den Betrag der Abbuchung gesetzt
 * — damit ein Terminal den gleichen Betrag ohne Auth als Refund zurück-
 * crediten kann. Praktisch heißt das: **Letzte Abbuchung als absoluter
 * Betrag**, on-chip verfügbar auch beim allerersten Scan. Verifiziert gegen
 * echte Karte: Saldo 4,39 € + LimitedCreditValue 6,35 € → letzte Buchung
 * war eine Abbuchung von 6,35 €.
 */
data class MensaCardScan(
    val uid: String,
    val valueMilliEuro: Int,
    val scannedAt: Instant,
    val source: CardSource = CardSource.INTERCARD,
    val production: ProductionData? = null,
    val rawLastTransactionRecord: ByteArray? = null,
    val lastDebitAmountMilliEuro: Int? = null
) {
    val valueEuro: Double get() = valueMilliEuro / 1000.0
}

enum class CardSource(val label: String) {
    INTERCARD("Intercard")
}

/** Produktionswoche/-jahr aus den DESfire Manufacturing Data (BCD-kodiert). */
data class ProductionData(
    val week: Int,
    val year: Int
)

sealed interface CardReadResult {
    data class Success(val scan: MensaCardScan) : CardReadResult
    data class Failure(val reason: Reason, val detail: String? = null) : CardReadResult

    enum class Reason {
        NOT_DESFIRE,
        APP_NOT_FOUND,
        AUTH_REQUIRED,
        TRANSCEIVE_ERROR,
        UNKNOWN
    }
}
