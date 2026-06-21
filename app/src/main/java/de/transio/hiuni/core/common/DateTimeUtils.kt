package de.transio.hiuni.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object DateTimeUtils {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.GERMAN)

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

    fun formatRelativeDay(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val today = LocalDate.now(zone)
        val date = instant.atZone(zone).toLocalDate()
        return when (ChronoUnit.DAYS.between(today, date)) {
            0L -> "Heute"
            1L -> "Morgen"
            -1L -> "Gestern"
            else -> date.format(dateFormatter)
        }
    }

    fun formatTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        instant.atZone(zone).format(timeFormatter)

    fun formatRelativeDateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        "${formatRelativeDay(instant, zone)} · ${formatTime(instant, zone)} Uhr"
}
