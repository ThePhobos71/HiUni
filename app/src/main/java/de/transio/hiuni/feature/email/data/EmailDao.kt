package de.transio.hiuni.feature.email.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface EmailDao {

    // `isHiddenLocally = 0` filtert „lokal gelöschte" Mails raus — die bleiben in
    // der DB damit der nächste IMAP-Sync sie nicht versehentlich neu pullt, sind
    // aber für UI und Suche unsichtbar.

    @Query("SELECT * FROM emails WHERE folder = :folder AND isHiddenLocally = 0 ORDER BY receivedAt DESC LIMIT 200")
    fun observeFolder(folder: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE isStarred = 1 AND isHiddenLocally = 0 ORDER BY receivedAt DESC LIMIT 200")
    fun observeStarred(): Flow<List<EmailEntity>>

    /**
     * Volltext-Suche über `subject`, `fromName`, `fromAddress`, `bodyPlain`. Tokens werden
     * AND-verknüpft, jeder Token muss als Substring in mindestens einer der vier Spalten
     * vorkommen. `LIKE` ist mit `COLLATE NOCASE` case-insensitiv.
     *
     * Wir nutzen `@RawQuery` weil die Anzahl der Tokens zur Compile-Zeit unbekannt ist —
     * das Query wird im Repository dynamisch zusammengebaut. Room observed auf `emails`,
     * damit re-emits bei Insert/Update wie gewohnt funktionieren.
     */
    @RawQuery(observedEntities = [EmailEntity::class])
    fun searchRaw(query: SupportSQLiteQuery): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE rowId = :rowId LIMIT 1")
    suspend fun findByRowId(rowId: Long): EmailEntity?

    @Query("SELECT * FROM emails WHERE folder = :folder AND uid = :uid LIMIT 1")
    suspend fun findByUid(folder: String, uid: Long): EmailEntity?

    @Query("SELECT * FROM emails WHERE folder = :folder AND bodyPlain IS NULL AND bodyHtml IS NULL ORDER BY receivedAt DESC LIMIT :limit")
    suspend fun pendingBodies(folder: String, limit: Int): List<EmailEntity>

    @Query("SELECT uid FROM emails WHERE folder = :folder")
    suspend fun knownUids(folder: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(emails: List<EmailEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(email: EmailEntity)

    @Query("UPDATE emails SET isRead = :isRead WHERE rowId = :rowId")
    suspend fun setRead(rowId: Long, isRead: Boolean)

    @Query("UPDATE emails SET isStarred = :isStarred WHERE rowId = :rowId")
    suspend fun setStarred(rowId: Long, isStarred: Boolean)

    @Query("UPDATE emails SET bodyPlain = :body, bodyHtml = :html, attachmentsJson = :attachments WHERE rowId = :rowId")
    suspend fun setBody(rowId: Long, body: String, html: String?, attachments: String?)

    @Query("DELETE FROM emails WHERE folder = :folder AND uid NOT IN (:keepUids)")
    suspend fun pruneNotIn(folder: String, keepUids: List<Long>)

    @Query("DELETE FROM emails WHERE rowId = :rowId")
    suspend fun deleteByRowId(rowId: Long)

    /**
     * Setzt den Folder-Wert einer einzelnen Mail um — wird fürs Archivieren genutzt:
     * lokal verschieben wir die Zeile in den logischen `FOLDER_ARCHIVE`, der eigentliche
     * Server-MOVE passiert via [ImapClient.moveByUid].
     */
    @Query("UPDATE emails SET folder = :folder WHERE rowId = :rowId")
    suspend fun markFolderByRowId(rowId: Long, folder: String)

    /** Setzt das lokale Soft-Delete-Flag — die Mail verschwindet aus allen Listen. */
    @Query("UPDATE emails SET isHiddenLocally = :hidden WHERE rowId = :rowId")
    suspend fun setHiddenLocally(rowId: Long, hidden: Boolean)

    /**
     * Adress-Quellen für die Compose-Autocomplete. Letzte 500 Mails — neuere
     * Kontakte zuerst, damit Autocomplete frische Kontakte priorisiert.
     */
    @Query(
        "SELECT fromAddress, fromName, toAddresses, ccAddresses " +
            "FROM emails ORDER BY receivedAt DESC LIMIT 500"
    )
    fun observeKnownAddressRows(): Flow<List<KnownAddressRow>>
}

data class KnownAddressRow(
    val fromAddress: String,
    val fromName: String?,
    val toAddresses: String?,
    val ccAddresses: String?
)
