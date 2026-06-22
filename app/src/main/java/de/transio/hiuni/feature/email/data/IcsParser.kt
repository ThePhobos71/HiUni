package de.transio.hiuni.feature.email.data

import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class IcsInvite(
    val uid: String?,
    val summary: String?,
    val description: String?,
    val location: String?,
    val start: Instant?,
    val end: Instant?,
    val organizer: String?
)

object IcsParser {

    private val basicFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateOnly = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** Single-VEVENT-Parser (für Mail-Einladungen). Erstes VEVENT wird zurückgegeben. */
    fun parse(content: String): IcsInvite? = parseAll(content).firstOrNull()

    /** Multi-VEVENT-Parser (für LSF-Stundenplan-Export). */
    fun parseAll(content: String): List<IcsInvite> = try {
        val unfolded = unfold(content)
        val lines = unfolded.lines()
        val results = mutableListOf<IcsInvite>()
        var inEvent = false
        var uid: String? = null
        var summary: String? = null
        var description: String? = null
        var location: String? = null
        var start: Instant? = null
        var end: Instant? = null
        var organizer: String? = null
        fun resetEventState() {
            uid = null; summary = null; description = null
            location = null; start = null; end = null; organizer = null
        }
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    inEvent = true
                    resetEventState()
                }
                trimmed.equals("END:VEVENT", ignoreCase = true) -> {
                    if (summary != null || start != null) {
                        results += IcsInvite(uid, summary, description, location, start, end, organizer)
                    }
                    inEvent = false
                }
                inEvent -> {
                    val colon = trimmed.indexOf(':')
                    if (colon < 0) continue
                    val keyPart = trimmed.substring(0, colon)
                    val value = unescape(trimmed.substring(colon + 1))
                    val key = keyPart.substringBefore(';').uppercase()
                    when (key) {
                        "UID" -> uid = value
                        "SUMMARY" -> summary = value
                        "DESCRIPTION" -> description = value
                        "LOCATION" -> location = value
                        "DTSTART" -> start = parseDateTime(keyPart, value)
                        "DTEND" -> end = parseDateTime(keyPart, value)
                        "ORGANIZER" -> organizer = value.substringAfter("mailto:", value)
                    }
                }
            }
        }
        results
    } catch (t: Throwable) {
        Timber.w(t, "ICS parse failed")
        emptyList()
    }

    private fun unfold(raw: String): String =
        raw.replace("\r\n ", "").replace("\n ", "").replace("\r\n\t", "").replace("\n\t", "")

    private fun unescape(value: String): String =
        value.replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun parseDateTime(keyPart: String, value: String): Instant? = try {
        val tzid = keyPart.substringAfter("TZID=", "").substringBefore(';').trim()
        when {
            value.endsWith("Z") -> {
                LocalDateTime.parse(value.removeSuffix("Z"), basicFormatter)
                    .toInstant(ZoneOffset.UTC)
            }
            value.contains("T") -> {
                val zone = if (tzid.isNotBlank()) {
                    runCatching { ZoneId.of(tzid) }.getOrDefault(ZoneId.systemDefault())
                } else ZoneId.systemDefault()
                LocalDateTime.parse(value, basicFormatter).atZone(zone).toInstant()
            }
            value.length == 8 -> {
                LocalDate.parse(value, dateOnly).atStartOfDay(ZoneId.systemDefault()).toInstant()
            }
            else -> null
        }
    } catch (t: Throwable) {
        Timber.w(t, "ICS date parse failed for $value")
        null
    }
}
