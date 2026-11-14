package de.transio.hiuni.feature.rooms.data

/** Ein zur gefragten Uhrzeit freier Raum. `freeUntilMinute == null`: heute nichts mehr. */
data class FreeRoom(val room: LsfRoom, val freeUntilMinute: Int?)

/**
 * Ein zur gefragten Uhrzeit belegter Raum.
 *
 * @property busyUntilMinute     Ende der laufenden (zusammengefassten) Belegung, 1440 = bis Tagesende.
 * @property freeAfterUntilMinute Ende der freien Zeit danach, `null` = danach heute frei.
 */
data class BusyRoom(val room: LsfRoom, val busyUntilMinute: Int, val freeAfterUntilMinute: Int?)

data class FreeRoomsResult(
    val free: List<FreeRoom> = emptyList(),
    val busy: List<BusyRoom> = emptyList()
)

/**
 * Reine, Android-freie Frei/Belegt-Logik des Raumfinders (Muster wie
 * [de.transio.hiuni.feature.bib.data.BibFreeSlotNotifier]).
 *
 * Eingang sind die belegten Spannen je Raum für den Tag ([RoomDay.busy], halboffen,
 * Minuten ab Mitternacht) und eine Uhrzeit. Ein Raum ist um `t` belegt, wenn eine
 * Spanne `start <= t < end` enthält; eine Belegung, die genau um `t` endet, gilt also
 * schon als vorbei, eine, die genau um `t` beginnt, als laufend.
 *
 * Reihenfolge: freie Räume zuerst, am längsten frei oben ("heute nichts mehr" ganz
 * oben); dann die belegten, am frühesten wieder frei oben. Gleichstand nach Raumname.
 */
object FreeRoomCalculator {

    const val END_OF_DAY = 24 * 60

    fun evaluate(days: List<RoomDay>, atMinute: Int): FreeRoomsResult {
        val free = mutableListOf<FreeRoom>()
        val busy = mutableListOf<BusyRoom>()
        for (day in days) {
            val spans = RoomIcsParser.merge(day.busy)
            val current = spans.firstOrNull { atMinute >= it.startMinute && atMinute < it.endMinute }
            if (current == null) {
                val next = spans.firstOrNull { it.startMinute > atMinute }
                free += FreeRoom(day.room, next?.startMinute)
            } else {
                val next = spans.firstOrNull { it.startMinute > current.endMinute }
                val freeAfter = if (current.endMinute >= END_OF_DAY) null else next?.startMinute
                busy += BusyRoom(day.room, current.endMinute, freeAfter)
            }
        }
        return FreeRoomsResult(
            free = free.sortedWith(
                compareByDescending<FreeRoom> { it.freeUntilMinute ?: Int.MAX_VALUE }
                    .thenBy { it.room.code }
            ),
            busy = busy.sortedWith(
                compareBy<BusyRoom> { it.busyUntilMinute }.thenBy { it.room.code }
            )
        )
    }

    /** "frei bis 16:00" oder "frei, heute nichts mehr". */
    fun statusText(room: FreeRoom): String =
        room.freeUntilMinute?.let { "frei bis ${clock(it)}" } ?: "frei, heute nichts mehr"

    /**
     * "belegt bis 14:00, danach frei", "belegt bis 14:00, danach frei bis 16:00"
     * oder "heute durchgehend belegt".
     */
    fun statusText(room: BusyRoom): String {
        if (room.busyUntilMinute >= END_OF_DAY) return "heute durchgehend belegt"
        val base = "belegt bis ${clock(room.busyUntilMinute)}, danach frei"
        return room.freeAfterUntilMinute?.let { "$base bis ${clock(it)}" } ?: base
    }

    fun clock(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
}
