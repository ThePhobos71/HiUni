package de.transio.hiuni.feature.calendar.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    /** Direktzugriff für den Reminder-Scheduler: Master-Event per primärem id. */
    suspend fun findById(id: Long): CustomEventEntity?
}

@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val dao: CustomEventDao
) : CalendarRepository {

    /**
     * Vereint zwei Quellen in einer Liste:
     * 1. Single-shot-Events, die per Range-Query schon ins Fenster passen.
     * 2. Recurring-Master-Events mit `startTime <= window.to`, deren Occurrences in
     *    das Fenster fallen. Der Master selber landet als eines der Occurrences im
     *    Output (gleiche id, evtl. neue start/end-Times).
     *
     * Doppelte Einträge können entstehen, wenn ein Recurring-Master selber ins Fenster
     * fällt (dann ist er sowohl in (1) als auch in (2) Treffer). Wir deduplizieren am
     * Ende per (id, startTime).
     *
     * TODO Recurrence-Reminder-Scheduling: Hier (oder im ViewModel) müsste der nächste
     *  geplante Reminder eines Recurring-Masters bei Trigger neu berechnet werden. Aktuell
     *  bekommt nur das erste Auftreten eine Notification — siehe Bericht.
     */
    override fun observeRange(from: Instant, to: Instant): Flow<List<CustomEventEntity>> =
        combine(
            dao.observeRange(from.toEpochMilli(), to.toEpochMilli()),
            dao.observeRecurringMastersUntil(to.toEpochMilli())
        ) { singleShotAndMasters, recurringMasters ->
            val expanded = RecurrenceExpander.expandAll(recurringMasters, from, to)
            // Single-Shot-Window enthält auch Recurring-Masters (falls ihr Master-Start
            // ins Fenster fällt). Wir filtern die raus, weil sie bereits durch [expanded]
            // abgedeckt sind — sonst hätten wir Doubletten.
            val singleShot = singleShotAndMasters.filter { it.recurrenceRule.isNullOrBlank() }
            (singleShot + expanded).sortedBy { it.startTime }
        }

    override fun observeAll(): Flow<List<CustomEventEntity>> = dao.observeAll()

    override suspend fun upsert(event: CustomEventEntity): Long =
        if (event.id == 0L) dao.insert(event) else { dao.update(event); event.id }

    override suspend fun delete(event: CustomEventEntity) = dao.delete(event)

    override suspend fun findNextEvent(): CustomEventEntity? = dao.findNextEvent()

    override suspend fun findBySourceReference(kind: String, ref: String): CustomEventEntity? =
        dao.findBySourceReference(kind, ref)

    override suspend fun findById(id: Long): CustomEventEntity? = dao.findById(id)
}
