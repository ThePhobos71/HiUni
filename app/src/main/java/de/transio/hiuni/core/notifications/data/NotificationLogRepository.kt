package de.transio.hiuni.core.notifications.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface NotificationLogRepository {
    fun observeRecent(limit: Int = 200): Flow<List<NotificationLogEntity>>
    fun observeUnreadCount(): Flow<Int>
    suspend fun log(
        kind: NotificationKind,
        title: String,
        body: String? = null,
        refKey: String? = null,
        firedAt: Instant = Instant.now()
    ): Long
    suspend fun markRead(id: Long)
    suspend fun markAllRead()
    suspend fun delete(id: Long)
    /** Räumt Einträge älter als [olderThan] auf — typischerweise 30 Tage. */
    suspend fun prune(olderThan: Instant)
}

@Singleton
class NotificationLogRepositoryImpl @Inject constructor(
    private val dao: NotificationLogDao
) : NotificationLogRepository {

    override fun observeRecent(limit: Int): Flow<List<NotificationLogEntity>> =
        dao.observeRecent(limit)

    override fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    override suspend fun log(
        kind: NotificationKind,
        title: String,
        body: String?,
        refKey: String?,
        firedAt: Instant
    ): Long = dao.insert(
        NotificationLogEntity(
            kind = kind,
            title = title,
            body = body,
            firedAt = firedAt,
            isRead = false,
            refKey = refKey
        )
    )

    override suspend fun markRead(id: Long) = dao.markRead(id)
    override suspend fun markAllRead() = dao.markAllRead()
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun prune(olderThan: Instant) =
        dao.pruneOlderThan(olderThan.toEpochMilli())
}
