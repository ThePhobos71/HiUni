package de.transio.hiuni.feature.calendar.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * RFC 5545-light Recurrence-Modell. Bewusst klein gehalten — wer komplexere Regeln
 * (BYMONTHDAY, COUNT, YEARLY, …) braucht, legt mehrere Events an.
 *
 * Persistiert wird die Regel als JSON-String in [CustomEventEntity.recurrenceRule].
 * Beispiel:
 * ```
 * {"freq":"WEEKLY","interval":1,"byDays":["MO","WE"],"until":"2026-07-31"}
 * ```
 *
 * Wir nutzen kotlinx.serialization (ohnehin im Projekt für andere Entities), damit
 * Unit-Tests ohne Robolectric/Android-Stubs laufen können.
 */
data class RecurrenceRule(
    val freq: Freq,
    val interval: Int = 1,
    /** Nur für [Freq.WEEKLY] relevant. Null/leer = Wochentag vom Master-Start. */
    val byDays: List<DayOfWeek>? = null,
    /** ISO-LocalDate, exklusiv. Null = unendlich (in der Praxis: Expander cappt auf 2 Jahre). */
    val until: LocalDate? = null
) {
    enum class Freq { DAILY, WEEKLY, MONTHLY }

    fun toJsonString(): String {
        val dto = Dto(
            freq = freq.name,
            interval = interval,
            byDays = byDays?.takeIf { it.isNotEmpty() }?.map { it.toIsoAbbrev() },
            until = until?.toString()
        )
        return JsonFormat.encodeToString(Dto.serializer(), dto)
    }

    /**
     * Interne Wire-Form. `freq` / `byDays` als String, damit alte/zukünftige Werte
     * ohne Crash geparst werden können (Versions-Robustheit).
     */
    @Serializable
    private data class Dto(
        @SerialName("freq") val freq: String,
        @SerialName("interval") val interval: Int = 1,
        @SerialName("byDays") val byDays: List<String>? = null,
        @SerialName("until") val until: String? = null
    )

    companion object {
        /**
         * Lenient-Json: ignoriere unbekannte Keys + lass Default-Values zu. So überlebt
         * der Parser zukünftige Felder, ohne Bestands-Events zu zerschießen.
         */
        private val JsonFormat = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /**
         * Parsed eine JSON-Regel aus [CustomEventEntity.recurrenceRule]. Bei Fehlern
         * (kaputtes JSON, unbekanntes `freq`) → null + Warn-Log. Wir wollen nicht den
         * gesamten Kalender abreißen, nur weil ein einzelner Event eine korrupte Rule hat.
         */
        fun fromJsonString(raw: String?): RecurrenceRule? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val dto = JsonFormat.decodeFromString(Dto.serializer(), raw)
                val freq = Freq.entries.firstOrNull { it.name == dto.freq }
                    ?: error("Unbekannte freq: ${dto.freq}")
                val byDays = dto.byDays?.mapNotNull { it.fromIsoAbbrev() }
                    ?.takeIf { it.isNotEmpty() }
                val until = dto.until?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                RecurrenceRule(
                    freq = freq,
                    interval = dto.interval.coerceAtLeast(1),
                    byDays = byDays,
                    until = until
                )
            }.onFailure { Timber.w(it, "RecurrenceRule.fromJsonString failed: $raw") }.getOrNull()
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
 * DayOfWeek ↔ ISO-Abkürzung
 * ────────────────────────────────────────────────────────────────── */

internal fun DayOfWeek.toIsoAbbrev(): String = when (this) {
    DayOfWeek.MONDAY -> "MO"
    DayOfWeek.TUESDAY -> "TU"
    DayOfWeek.WEDNESDAY -> "WE"
    DayOfWeek.THURSDAY -> "TH"
    DayOfWeek.FRIDAY -> "FR"
    DayOfWeek.SATURDAY -> "SA"
    DayOfWeek.SUNDAY -> "SU"
}

internal fun String.fromIsoAbbrev(): DayOfWeek? = when (this.uppercase()) {
    "MO" -> DayOfWeek.MONDAY
    "TU" -> DayOfWeek.TUESDAY
    "WE" -> DayOfWeek.WEDNESDAY
    "TH" -> DayOfWeek.THURSDAY
    "FR" -> DayOfWeek.FRIDAY
    "SA" -> DayOfWeek.SATURDAY
    "SU" -> DayOfWeek.SUNDAY
    else -> null
}
