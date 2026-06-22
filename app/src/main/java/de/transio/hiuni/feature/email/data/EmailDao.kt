package de.transio.hiuni.feature.email.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EmailDao {

    @Query("SELECT * FROM emails WHERE folder = :folder ORDER BY receivedAt DESC LIMIT 200")
    fun observeFolder(folder: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE isStarred = 1 ORDER BY receivedAt DESC LIMIT 200")
    fun observeStarred(): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE rowId = :rowId LIMIT 1")
    suspend fun findByRowId(rowId: Long): EmailEntity?

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
}
