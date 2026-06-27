package de.transio.hiuni.feature.mensa.data

import de.transio.hiuni.core.common.isWeekend
import de.transio.hiuni.feature.mensa.Mealtime
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

sealed interface OpenStatus {
    data object Open : OpenStatus
    data class ClosingSoon(val minutes: Long) : OpenStatus
    data class OpensLater(val time: LocalTime) : OpenStatus
    data object ClosedToday : OpenStatus
    data object Preview : OpenStatus
}

/**
 * Ein Öffnungszeit-Block aus der STW-ON-API. Pro `time` ("noon" / "evening")
 * kann es mehrere Blöcke geben — z.B. Mo–Do bis 14:15 und Fr nur bis 14:00.
 *
 * `startDay`/`endDay` sind ISO-DayOfWeek-Werte (1 = Montag … 7 = Sonntag).
 */
data class OpeningHourBlock(
    val time: String,
    val startDay: Int,
    val endDay: Int,
    val startTime: LocalTime,
    val endTime: LocalTime
) {
    fun coversDay(day: DayOfWeek): Boolean = day.value in startDay..endDay

    val mealtime: Mealtime? get() = when (time.lowercase()) {
        "noon", "lunch" -> Mealtime.MITTAG
        "evening", "dinner" -> Mealtime.ABEND
        else -> null
    }
}

/**
 * Öffnungszeiten-Lookup. Bevorzugt API-Daten (per `updateFromApi` befüllt nach
 * jedem `MensaApiService.fetchMenu`), fällt auf die hardcoded `Mealtime`-Slots
 * zurück, solange die API noch nichts geliefert hat (Cold-Start vor erstem
 * Refresh, oder unbekannte Location).
 *
 * In-Memory-Cache reicht: Öffnungszeiten ändern sich selten, und der nächste
 * Refresh überschreibt sie eh. Über App-Restarts hinweg ist der Fallback
 * tolerabel — die hardcoded Werte sind das aktuelle Hildesheim-Soll.
 */
object MensaHours {

    @Volatile
    private var dynamicHoursByLocation: Map<Int, List<OpeningHourBlock>> = emptyMap()

    fun updateFromApi(locationId: Int, blocks: List<OpeningHourBlock>) {
        if (blocks.isEmpty()) return
        dynamicHoursByLocation = dynamicHoursByLocation + (locationId to blocks)
    }

    fun isOpenNow(
        locationId: Int? = null,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now()
    ): Boolean {
        val blocks = locationId?.let { dynamicHoursByLocation[it] }
        if (blocks.isNullOrEmpty()) {
            // Fallback: hardcoded Mealtime-Slots, no-weekends.
            if (today.isWeekend()) return false
            return Mealtime.entries.any { now.isAfter(it.from) && now.isBefore(it.to) }
        }
        return blocks.any { block ->
            block.coversDay(today.dayOfWeek) &&
                now.isAfter(block.startTime) &&
                now.isBefore(block.endTime)
        }
    }

    fun statusFor(
        date: LocalDate,
        mealtime: Mealtime,
        locationId: Int? = null,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now()
    ): OpenStatus {
        if (date != today) return OpenStatus.Preview
        val (from, to) = windowFor(locationId, date, mealtime)
            ?: return OpenStatus.ClosedToday
        return when {
            now.isBefore(from) -> OpenStatus.OpensLater(from)
            now.isBefore(to) -> {
                val minutesLeft = Duration.between(now, to).toMinutes()
                if (minutesLeft <= 30) OpenStatus.ClosingSoon(minutesLeft) else OpenStatus.Open
            }
            else -> OpenStatus.ClosedToday
        }
    }

    /**
     * Bestimmt das (start, end)-Fenster für `mealtime` am gegebenen Tag.
     * Bei mehreren passenden API-Blöcken (Mo–Do bis 14:15 UND Fr bis 14:00)
     * wird der spezifischste pro Tag gewählt — kleinster `endDay-startDay`-
     * Range gewinnt; bei Gleichstand der späteste `endTime`. So matchen wir
     * den engsten Treffer für den jeweiligen Wochentag.
     *
     * Rückgabe `null` = der Tag ist für die Location komplett geschlossen.
     */
    private fun windowFor(
        locationId: Int?,
        date: LocalDate,
        mealtime: Mealtime
    ): Pair<LocalTime, LocalTime>? {
        val blocks = locationId?.let { dynamicHoursByLocation[it] }
        if (blocks.isNullOrEmpty()) {
            if (date.isWeekend()) return null
            return mealtime.from to mealtime.to
        }
        val day = date.dayOfWeek
        val candidates = blocks.filter { it.mealtime == mealtime && it.coversDay(day) }
        if (candidates.isEmpty()) return null
        val best = candidates.minWithOrNull(
            compareBy<OpeningHourBlock> { it.endDay - it.startDay }
                .thenByDescending { it.endTime }
        ) ?: return null
        return best.startTime to best.endTime
    }
}
