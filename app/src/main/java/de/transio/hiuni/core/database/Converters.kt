package de.transio.hiuni.core.database

import androidx.room.TypeConverter
import de.transio.hiuni.core.notifications.data.NotificationKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class Converters {

    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun localTimeToNanos(value: LocalTime?): Long? = value?.toNanoOfDay()

    @TypeConverter
    fun nanosToLocalTime(value: Long?): LocalTime? = value?.let(LocalTime::ofNanoOfDay)

    @TypeConverter
    fun notificationKindToString(value: NotificationKind?): String? = value?.name

    /** Unbekannte Werte aus alten Builds fallen still auf SYSTEM zurück. */
    @TypeConverter
    fun stringToNotificationKind(value: String?): NotificationKind? = value?.let {
        runCatching { NotificationKind.valueOf(it) }.getOrDefault(NotificationKind.SYSTEM)
    }
}
