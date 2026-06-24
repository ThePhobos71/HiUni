package de.transio.hiuni.feature.calendar.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface CalendarRepository {
    fun observeRange(from: Instant, to: Instant): Flow<List<CustomEventEntity>>
    fun observeAll(): Flow<List<CustomEventEntity>>
    suspend fun upsert(event: CustomEventEntity): Long
    suspend fun delete(event: CustomEventEntity)
    suspend fun findNextEvent(): CustomEventEntity?
    /** Lookup für Pin-Toggles: liefert den Kalendereintrag, falls eine Quelle (z. B. "sport:42") schon gepinnt ist. */
    suspend fun findBySourceReference(kind: String, ref: String): CustomEventEntity?
}

@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val dao: CustomEventDao
) : CalendarRepository {

    override fun observeRange(from: Instant, to: Instant): Flow<List<CustomEventEntity>> =
        dao.observeRange(from.toEpochMilli(), to.toEpochMilli())

    override fun observeAll(): Flow<List<CustomEventEntity>> = dao.observeAll()

    override suspend fun upsert(event: CustomEventEntity): Long =
        if (event.id == 0L) dao.insert(event) else { dao.update(event); event.id }

    override suspend fun delete(event: CustomEventEntity) = dao.delete(event)

    override suspend fun findNextEvent(): CustomEventEntity? = dao.findNextEvent()

    override suspend fun findBySourceReference(kind: String, ref: String): CustomEventEntity? =
        dao.findBySourceReference(kind, ref)
}
