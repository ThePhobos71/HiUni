package de.transio.hiuni.feature.mensa.data

import de.transio.hiuni.core.common.isWeekend
import de.transio.hiuni.feature.mensa.Mealtime
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

object MensaHours {

    fun isOpenNow(today: LocalDate = LocalDate.now(), now: LocalTime = LocalTime.now()): Boolean {
        if (today.isWeekend()) return false
        return Mealtime.entries.any { now.isAfter(it.from) && now.isBefore(it.to) }
    }

    fun statusFor(
        date: LocalDate,
        mealtime: Mealtime,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now()
    ): OpenStatus {
        if (date != today) return OpenStatus.Preview
        if (date.isWeekend()) return OpenStatus.ClosedToday
        val from = mealtime.from
        val to = mealtime.to
        return when {
            now.isBefore(from) -> OpenStatus.OpensLater(from)
            now.isBefore(to) -> {
                val minutesLeft = Duration.between(now, to).toMinutes()
                if (minutesLeft <= 30) OpenStatus.ClosingSoon(minutesLeft) else OpenStatus.Open
            }
            else -> OpenStatus.ClosedToday
        }
    }
}
