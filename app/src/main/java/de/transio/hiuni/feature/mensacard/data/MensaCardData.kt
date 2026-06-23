package de.transio.hiuni.feature.mensacard.data

import java.time.Instant

/**
 * Vollständiger Lesevorgang einer Mensa-Karte (Intercard DESfire). Wert wird
 * intern in 1/1000 € gespeichert (so liefert es Intercard auch direkt).
 */
data class MensaCardScan(
    val uid: String,
    val valueMilliEuro: Int,
    val scannedAt: Instant,
    val source: CardSource = CardSource.INTERCARD,
    val production: ProductionData? = null
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
