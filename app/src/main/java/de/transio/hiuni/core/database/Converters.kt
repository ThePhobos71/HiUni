package de.transio.hiuni.core.database

import androidx.room.TypeConverter
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.feature.grades.data.GradeStatus
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

    /**
     * Liste von kurzen Strings (z.B. Raum-Namen "SC.A.0.09") <-> einzelne TEXT-Spalte.
     * Newline ist Trennzeichen — Räume können Punkte enthalten aber niemals "\n".
     * Leere Liste wird als leerer String gespeichert, damit wir beim Lese-Roundtrip
     * `null` nur kriegen, wenn die Spalte selbst NULL ist.
     */
    @TypeConverter
    fun stringListToString(value: List<String>?): String? =
        value?.joinToString("\n")

    @TypeConverter
    fun stringToStringList(value: String?): List<String>? =
        value?.split("\n")?.filter { it.isNotEmpty() }

    @TypeConverter
    fun gradeStatusToString(value: GradeStatus?): String? = value?.name

    /** Unbekannte Werte aus künftigen LSF-Änderungen fallen still auf REGISTERED zurück. */
    @TypeConverter
    fun stringToGradeStatus(value: String?): GradeStatus? = value?.let {
        runCatching { GradeStatus.valueOf(it) }.getOrDefault(GradeStatus.REGISTERED)
    }
}
