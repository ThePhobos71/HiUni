package de.transio.hiuni.core.notifications.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationLogDao {

    /**
     * Neueste zuerst. Limitiert auf einen großzügigen Cap — das Push-Center
     * scrollt, aber wir wollen nicht beliebig wachsen.
     */
    @Query(
        "SELECT * FROM notifications " +
            "ORDER BY firedAt DESC " +
            "LIMIT :limit"
    )
    fun observeRecent(limit: Int = 200): Flow<List<NotificationLogEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: NotificationLogEntity): Long

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM notifications WHERE firedAt < :cutoffMillis")
    suspend fun pruneOlderThan(cutoffMillis: Long)
}
