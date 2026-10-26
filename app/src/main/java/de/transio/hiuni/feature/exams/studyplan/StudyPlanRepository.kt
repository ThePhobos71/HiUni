package de.transio.hiuni.feature.exams.studyplan

import de.transio.hiuni.core.common.DateTimeFormats
import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventDao
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brücke zwischen [StudyPlanner] und dem Kalender: liest die belegte Zeit und
 * schreibt geplante Blöcke als [CustomEventEntity.SOURCE_STUDY_PLAN]-Termine.
 *
 * ### Semantik
 *
 *  - **Ersetzen** ([replaceFutureBlocks]): löscht alle Lernblöcke, die nach `now`
 *    beginnen (samt Erinnerung), und schreibt die neuen. Vergangene und gerade
 *    laufende Blöcke bleiben als Verlauf stehen.
 *  - **Löschen** ([deleteFutureBlocks]): wie Ersetzen, nur ohne neue Blöcke.
 *  - Im Kalender bearbeitete Blöcke sind dort zu [CustomEventEntity.SOURCE_USER]
 *    geworden und werden hier nie angefasst; beim nächsten Plan zählen sie als belegt.
 */
@Singleton
class StudyPlanRepository @Inject constructor(
    private val dao: CustomEventDao,
    private val calendarRepository: CalendarRepository,
    private val scheduler: NotificationScheduler
) {

    /**
     * Belegte Zeit im Fenster `[from, to]`, wiederkehrende Termine (Vorlesungen)
     * bereits in einzelne Vorkommen expandiert. Künftige Lernblöcke zählen nicht,
     * weil sie beim Übernehmen ohnehin ersetzt werden.
     */
    suspend fun busyIntervals(from: Instant, to: Instant, now: Instant): List<BusyInterval> =
        calendarRepository.observeRange(from, to).first()
            .filterNot { it.sourceKind == CustomEventEntity.SOURCE_STUDY_PLAN && it.startTime.isAfter(now) }
            .map { BusyInterval(it.startTime, it.endTime) }

    /** Eingetragene Lernblöcke, die nach [now] beginnen. */
    fun observeUpcomingBlocks(now: Instant): Flow<List<CustomEventEntity>> =
        dao.observeBySourceKindStartingAfter(CustomEventEntity.SOURCE_STUDY_PLAN, now.toEpochMilli())

    /**
     * Ersetzt alle noch nicht begonnenen Lernblöcke durch [blocks]. Blöcke, die nicht
     * nach [now] beginnen, werden übersprungen. Rückgabe: Anzahl geschriebener Blöcke.
     */
    suspend fun replaceFutureBlocks(
        blocks: List<PlannedStudyBlock>,
        reminderMinutesBefore: Int?,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        deleteFutureBlocks(now)
        val entities = blocks
            .filter { it.start.isAfter(now) }
            .sortedBy { it.start }
            .map { toEntity(it, reminderMinutesBefore, zone) }
        if (entities.isEmpty()) return 0
        val ids = dao.insertAll(entities)
        if (reminderMinutesBefore != null) {
            entities.zip(ids).forEach { (entity, id) ->
                val triggerAt = entity.startTime.minus(Duration.ofMinutes(reminderMinutesBefore.toLong()))
                if (triggerAt.isAfter(now)) scheduler.schedule(id, entity.title, triggerAt)
            }
        }
        return entities.size
    }

    /** Löscht alle Lernblöcke, die nach [now] beginnen, samt Erinnerung. Rückgabe: Anzahl. */
    suspend fun deleteFutureBlocks(now: Instant): Int {
        val future = dao.findBySourceKindStartingAfter(CustomEventEntity.SOURCE_STUDY_PLAN, now.toEpochMilli())
        future.forEach { scheduler.cancel(it.id) }
        if (future.isNotEmpty()) {
            dao.deleteBySourceKindStartingAfter(CustomEventEntity.SOURCE_STUDY_PLAN, now.toEpochMilli())
        }
        return future.size
    }

    private fun toEntity(block: PlannedStudyBlock, reminderMinutesBefore: Int?, zone: ZoneId): CustomEventEntity {
        val localStart = block.start.atZone(zone).toLocalDateTime()
        return CustomEventEntity(
            title = "$TITLE_PREFIX${block.title}",
            description = "Lerneinheit für die Klausur am ${block.examDate.format(DateTimeFormats.dateWithYear)}. " +
                "Vom Lernplan eingetragen; bearbeitest du den Termin, bleibt er als eigener Termin erhalten.",
            startTime = block.start,
            endTime = block.end,
            sourceKind = CustomEventEntity.SOURCE_STUDY_PLAN,
            sourceReference = sourceReference(block.examId, localStart.format(REF_FORMAT)),
            reminderMinutesBefore = reminderMinutesBefore
        )
    }

    companion object {
        const val TITLE_PREFIX = "Lernen: "
        private val REF_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

        /** Stabile Referenz pro Block: Klausur plus lokaler Startzeitpunkt. */
        fun sourceReference(examId: Long, localStart: String): String = "studyplan:$examId:$localStart"
    }
}
