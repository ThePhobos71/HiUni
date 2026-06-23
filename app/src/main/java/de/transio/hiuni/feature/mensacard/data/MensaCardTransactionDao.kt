package de.transio.hiuni.feature.mensacard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MensaCardTransactionDao {
    @Query("SELECT * FROM mensa_card_transactions WHERE uid = :uid ORDER BY scannedAt DESC LIMIT :limit")
    fun observeLatest(uid: String, limit: Int = 20): Flow<List<MensaCardTransactionEntity>>

    @Query("SELECT * FROM mensa_card_transactions WHERE uid = :uid ORDER BY scannedAt DESC LIMIT 1")
    suspend fun latestFor(uid: String): MensaCardTransactionEntity?

    @Insert
    suspend fun insert(transaction: MensaCardTransactionEntity): Long

    /**
     * Aktualisiert nur den Zeitstempel — wird gerufen wenn die Karte mehrfach
     * ohne Saldo-Änderung gescannt wird; statt einen Duplikat-Eintrag
     * anzulegen wandert der bestehende auf die jetzige Uhrzeit.
     */
    @Query("UPDATE mensa_card_transactions SET scannedAt = :scannedAt WHERE id = :id")
    suspend fun updateScannedAt(id: Long, scannedAt: Long)

    @Query("DELETE FROM mensa_card_transactions WHERE uid = :uid")
    suspend fun deleteAllFor(uid: String)
}
