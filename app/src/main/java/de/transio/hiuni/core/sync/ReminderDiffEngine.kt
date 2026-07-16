package de.transio.hiuni.core.sync

import de.transio.hiuni.core.notifications.NotificationScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Gemeinsame Diff-/Cancel-/Persist-Mechanik für die reminderbasierten Scheduler
 * ([ExamReminderScheduler], [LearnwebAssignmentReminderScheduler]).
 *
 * Beide teilen exakt dasselbe Muster:
 *
 *  1. Soll-Set an Reminder-IDs berechnen (nur Zukunfts-Slots).
 *  2. Gegen das persistierte Set aus dem letzten Lauf diffen → verwaiste IDs
 *     (alt − neu) über den [NotificationScheduler] canceln.
 *  3. Das neue Soll-Set persistieren, damit der nächste Lauf dagegen diffen kann.
 *
 * Ebenso zentralisiert die Engine das **ID-Schema** und dessen Overflow-Guard:
 *
 * ```
 * reminderId = idOffset + rowId * 10 + slot
 * ```
 *
 * Der `PendingIntent` im [NotificationScheduler] identifiziert sich über
 * `eventId.toInt()`. Bei einem Offset von 1e9 (bzw. 2e9) bleibt nach
 * `* 10 + slot` nur ein sehr schmales rowId-Fenster im positiven Int-Bereich:
 * `Int.MAX_VALUE` ist ~2.147e9, d.h. schon der 1e9-Offset selbst überschreitet
 * `Int.MAX_VALUE` nach `* 10` massiv. Der frühere Kommentar sprach von rowIds
 * bis ~2.1e8 — das ist zu optimistisch: mit Offset 1e9 muss `rowId * 10`
 * unter `Int.MAX_VALUE - 1e9` bleiben, also rowId ≲ 1.1e8; mit Offset 2e9
 * sogar nur ≲ 1.4e7. [reminderId] prüft deshalb VOR dem impliziten `.toInt()`
 * im Scheduler, ob die ID noch in den positiven Int-Bereich passt, und gibt
 * andernfalls `null` zurück (Timber.w + Skip statt stiller Overflow-Kollision).
 */
class ReminderDiffEngine(
    private val scheduler: NotificationScheduler,
    private val idOffset: Long,
    private val logTag: String,
) {

    /**
     * Baut die deterministische Reminder-ID aus `rowId` + `slot`. Gibt `null`
     * zurück, wenn die resultierende ID nicht mehr in den positiven Int-Bereich
     * passt (der [NotificationScheduler] castet auf Int für den PendingIntent) —
     * in dem Fall würde der Reminder mit einer fremden ID kollidieren, deshalb
     * skippen wir ihn lieber ganz.
     */
    fun reminderId(rowId: Long, slot: Int): Long? {
        val id = idOffset + rowId * 10 + slot
        if (id < 0 || id > Int.MAX_VALUE) {
            Timber.w(
                "%s: rowId=%d ergibt Reminder-ID %d außerhalb des positiven Int-Bereichs — skip",
                logTag, rowId, id
            )
            return null
        }
        return id
    }

    /**
     * Diff gegen den persistierten letzten Stand, cancelt verwaiste IDs und
     * persistiert das neue Soll-Set. `newScheduledIds` ist die frisch geplante
     * Sollmenge; `previousIdsFlow`/`persist` kapseln den DataStore-Zugriff des
     * jeweiligen Schedulers.
     */
    suspend fun commit(
        newScheduledIds: Set<Long>,
        previousIdsFlow: Flow<Set<Long>>,
        persist: suspend (Set<Long>) -> Unit,
    ): Int {
        val previousIds = runCatching { previousIdsFlow.first() }.getOrElse { emptySet() }
        val toCancel = previousIds - newScheduledIds
        for (id in toCancel) {
            scheduler.cancel(id)
        }
        if (toCancel.isNotEmpty()) {
            Timber.d("%s: canceled %d stale reminder(s)", logTag, toCancel.size)
        }

        runCatching { persist(newScheduledIds) }
            .onFailure { Timber.w(it, "%s: konnte Reminder-IDs nicht persistieren", logTag) }

        return toCancel.size
    }
}
