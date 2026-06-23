package de.transio.hiuni.feature.mensacard.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ein einzelner Mensa-Karten-Scan der eigenen Karte. [balanceMilliEuro] ist
 * der Saldo nach dem Ereignis, [deltaMilliEuro] die Differenz zum letzten
 * gespeicherten Eintrag, [cardLastDebitMilliEuro] der vom Chip selbst
 * gemeldete Betrag der letzten Abbuchung (DESfire LimitedCreditValue, immer
 * positiv) zum Scan-Zeitpunkt.
 *
 * Mit (scannedAt, balanceMilliEuro, cardLastDebitMilliEuro) lässt sich ein
 * vollständiger Verlaufsgraph zeichnen — Saldo über Zeit + on-chip
 * bestätigte Buchungen.
 */
@Entity(
    tableName = "mensa_card_transactions",
    indices = [Index(value = ["uid", "scannedAt"])]
)
data class MensaCardTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uid: String,
    val balanceMilliEuro: Int,
    val deltaMilliEuro: Int,
    val scannedAt: Long,
    val cardLastDebitMilliEuro: Int? = null
) {
    val isWithdrawal: Boolean get() = deltaMilliEuro < 0
    val isTopUp: Boolean get() = deltaMilliEuro > 0
}
