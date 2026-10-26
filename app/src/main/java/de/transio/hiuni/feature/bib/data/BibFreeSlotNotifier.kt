package de.transio.hiuni.feature.bib.data

import de.transio.hiuni.core.notifications.data.NotificationKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Reine, Android-/Netz-freie Entscheidungslogik für „Lieblings-Raum ist wieder
 * frei"-Pushes (Muster wie [de.transio.hiuni.feature.grades.data.GradeDiffNotifier]).
 *
 * WAS „FREI WERDEN" HEISST: Ein Slot, der beim letzten Check von jemand anderem
 * belegt war ([SlotStatus.BOOKED]) und jetzt [SlotStatus.FREE] ist, also eine
 * Stornierung. Räume, die ohnehin den ganzen Tag frei sind, sieht man in der App;
 * dafür braucht es keinen Push. Daraus folgt:
 *  - Leere Baseline (erster Check, frisch markierter Favorit) → keine Meldung,
 *    denn es gibt keinen bekannten Vorher-Zustand „belegt".
 *  - Weiterhin freie Slots melden nichts (sie waren vorher nicht belegt).
 *  - Ein gemeldeter Slot steht danach als frei in der Welt und meldet nicht
 *    nochmal. Wird er wieder belegt und erneut storniert, ist das eine neue Meldung.
 *  - Eigene Buchungen ([SlotStatus.OWN_BOOKING]) zählen nicht als „belegt": wer
 *    selbst storniert, bekommt keinen Push.
 *
 * WANN GEMELDET WIRD: Nur für heute und nur für Slots, die nach „jetzt" beginnen
 * (gleiche Regel wie das Buchungs-Grid, das begonnene Slots sperrt). Aus den
 * freien Slots werden zusammenhängende Blöcke gebildet; ein Block ist meldenswert,
 * wenn er mindestens [MIN_FREE_SLOTS] Slots (1 h) lang ist UND mindestens einen
 * frei gewordenen Slot enthält. Pro Raum gibt es höchstens eine Meldung (für den
 * frühesten solchen Block), damit eine Mehrfach-Stornierung nicht mehrfach piept.
 *
 * BASELINE: Die Belegt-Slots der Lieblings-Räume für heute und morgen. Morgen ist
 * dabei, damit beim ersten Check nach Mitternacht schon ein Vorher-Zustand für
 * den neuen Tag existiert (der Snapshot zeigt 28 Tage im Voraus).
 */
object BibFreeSlotNotifier {

    /** Mindestlänge eines meldenswerten freien Blocks: 2 × 30 min = 1 h. */
    const val MIN_FREE_SLOTS = 2

    /** Wie viele Tage ab heute die Baseline abdeckt (heute + morgen). */
    const val BASELINE_DAYS = 2L

    /** [NotificationKind] aller Frei-Meldungen (Kategorie „Bibliothek"). */
    val KIND: NotificationKind = NotificationKind.BIB

    /** Eine fertig aufbereitete Frei-Meldung für einen Raum. */
    data class Push(
        val roomId: Int,
        val title: String,
        val body: String,
        val refKey: String,
        val kind: NotificationKind = KIND
    )

    /**
     * Ergebnis eines Checks: zu feuernde Meldungen und die neue Baseline, die der
     * Aufrufer persistiert (ersetzt die alte vollständig).
     */
    data class Decision(
        val pushes: List<Push>,
        val bookedBaseline: Set<String>
    )

    /**
     * @param days           Belegung aus dem Snapshot ([BibSnapshot.allDays]).
     * @param favorites      Raum-IDs der Lieblings-Räume.
     * @param previousBooked Baseline des letzten Checks (siehe [slotKey]).
     * @param now            aktueller Zeitpunkt (lokale Zeit).
     */
    fun decide(
        days: Map<Pair<LocalDate, Int>, RoomDayAvailability>,
        favorites: Set<Int>,
        previousBooked: Set<String>,
        now: LocalDateTime
    ): Decision {
        if (favorites.isEmpty()) return Decision(emptyList(), emptySet())
        val today = now.toLocalDate()
        val rooms = favorites.sorted()

        val baseline = buildSet {
            for (offset in 0 until BASELINE_DAYS) {
                val date = today.plusDays(offset)
                for (roomId in rooms) {
                    days[date to roomId]?.slots.orEmpty()
                        .filter { it.status == SlotStatus.BOOKED }
                        .forEach { add(slotKey(roomId, date, it.startTime)) }
                }
            }
        }

        val pushes = rooms.mapNotNull { roomId ->
            val day = days[today to roomId] ?: return@mapNotNull null
            val block = freeBlocksAfter(day.slots, now.toLocalTime())
                .filter { it.size >= MIN_FREE_SLOTS }
                .firstOrNull { block ->
                    block.any { slotKey(roomId, today, it.startTime) in previousBooked }
                }
                ?: return@mapNotNull null
            pushFor(roomId, today, block.first().startTime, block.last().endTime)
        }

        return Decision(pushes, baseline)
    }

    /** Stabiler Schlüssel eines Slots: `102|2026-10-26|1400`. */
    fun slotKey(roomId: Int, date: LocalDate, start: LocalTime): String =
        "$roomId|$date|${hhmm(start)}"

    /** Präfix aller Schlüssel eines Raums, z.B. zum Verwerfen beim Ent-Favorisieren. */
    fun roomKeyPrefix(roomId: Int): String = "$roomId|"

    /**
     * Zusammenhängende Läufe freier Slots, die nach [now] beginnen. Ein belegter,
     * geschlossener oder eigener Slot (oder eine Lücke im Grid) beendet den Lauf.
     */
    private fun freeBlocksAfter(slots: List<SlotEntry>, now: LocalTime): List<List<SlotEntry>> {
        val blocks = mutableListOf<List<SlotEntry>>()
        var current = mutableListOf<SlotEntry>()
        for (slot in slots.sortedBy { it.startTime }) {
            val usable = slot.status == SlotStatus.FREE && slot.startTime.isAfter(now)
            val contiguous = current.isEmpty() || current.last().endTime == slot.startTime
            if (usable && contiguous) {
                current += slot
                continue
            }
            if (current.isNotEmpty()) blocks += current
            current = if (usable) mutableListOf(slot) else mutableListOf()
        }
        if (current.isNotEmpty()) blocks += current
        return blocks
    }

    private fun pushFor(roomId: Int, date: LocalDate, from: LocalTime, until: LocalTime): Push {
        val label = BibConfig.ROOM_META[roomId]?.label ?: "F$roomId"
        return Push(
            roomId = roomId,
            title = "$label ist heute ab ${clock(from)} frei",
            body = "Frei bis ${clock(until)} · Tippen zum Buchen",
            refKey = "bib_free_${date}_${roomId}_${hhmm(from)}"
        )
    }

    private fun clock(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

    private fun hhmm(t: LocalTime): String = "%02d%02d".format(t.hour, t.minute)
}
