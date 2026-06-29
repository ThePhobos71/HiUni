package de.transio.hiuni.core.common

import java.time.LocalDate

/**
 * Akademisches Semester an deutschen Hochschulen:
 *   - **Wintersemester (WS)**: 1. Oktober bis 31. März des Folgejahres
 *   - **Sommersemester (SS)**: 1. April bis 30. September
 *
 * `year` ist immer das *Start-Jahr* des Semesters. WS 2026/27 = `year = 2026, period = WS`,
 * SS 2026 = `year = 2026, period = SS`. Damit ist die Reihenfolge eindeutig
 * über `ordinal` vergleichbar.
 */
data class Semester(val period: Period, val year: Int) {

    enum class Period { SS, WS }

    /**
     * Monoton wachsender Index — SS 2026 = 4052, WS 2026/27 = 4053, SS 2027 = 4054.
     * Brauchen wir für `semestersBetween`, ohne sich mit Monaten/Tagen rumzuschlagen.
     */
    val ordinal: Int get() = year * 2 + if (period == Period.WS) 1 else 0

    /** „SS 2026" bzw. „WS 2026/27". */
    fun displayLabel(): String = when (period) {
        Period.SS -> "SS $year"
        Period.WS -> {
            val nextShort = (year + 1) % 100
            "WS $year/${nextShort.toString().padStart(2, '0')}"
        }
    }

    /**
     * Persistenz-Form: „2026W" / „2026S". Stabiler als das menschen-lesbare Label,
     * weil das Format sich nie ändert.
     */
    fun storageKey(): String = "${year}${if (period == Period.WS) "W" else "S"}"

    companion object {
        fun fromDate(date: LocalDate): Semester {
            val m = date.monthValue
            return when {
                m >= 10 -> Semester(Period.WS, date.year)
                m <= 3 -> Semester(Period.WS, date.year - 1) // WS 2026/27 läuft bis März 2027
                else -> Semester(Period.SS, date.year)
            }
        }

        fun fromStorageKey(key: String): Semester? {
            if (key.length < 5) return null
            val period = when (key.last()) {
                'W' -> Period.WS
                'S' -> Period.SS
                else -> return null
            }
            val year = key.dropLast(1).toIntOrNull() ?: return null
            return Semester(period, year)
        }

        /**
         * Anzahl Semester-Wechsel zwischen `start` (inklusive) und `end`. Gleicher
         * Semester → 0, ein Übergang dazwischen → 1, etc. Wert kann negativ sein
         * wenn `end < start`.
         */
        fun semestersBetween(start: Semester, end: Semester): Int =
            end.ordinal - start.ordinal

        /** `start.plusSemesters(n)`. */
        fun advance(start: Semester, n: Int): Semester {
            val newOrdinal = start.ordinal + n
            return Semester(
                period = if (newOrdinal % 2 == 1) Period.WS else Period.SS,
                year = newOrdinal / 2
            )
        }
    }
}
