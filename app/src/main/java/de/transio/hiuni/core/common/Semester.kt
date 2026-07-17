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

    /**
     * Jahr-Suffix für die Learnweb-URL — `learnwebYYYY` wechselt pro Studienjahr.
     * Die Uni Hildesheim bündelt SS + das folgende WS zur gleichen Instanz,
     * also SS 2026 + WS 2026/27 → `learnweb2026`. Beide nehmen das gleiche
     * `year` aus dem Semester (für WS ist das das Startjahr — WS 2026/27.year = 2026).
     */
    fun learnwebYear(): Int = year

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
         * Tolerantes Parsen eines menschen-lesbaren Semester-Labels in ein [Semester].
         * Versteht die im ganzen Projekt vorkommenden Schreibweisen:
         *  - Noten-/LSF-Kurzform: „WiSe 24/25", „WS 2024/25", „SoSe 25", „SS 2025"
         *  - Meine-Veranstaltungen-Langform: „Sommer 2026", „Winter 2025/26"
         *
         * Regeln:
         *  - Jahr = *Start-Jahr* des Semesters (WiSe 24/25 → 2024, konsistent zum
         *    [year]-Feld). Ein „24/25"-Suffix wird als Start-Jahr 24 gelesen.
         *  - Zweistellige Jahre werden zu 20xx expandiert.
         *  - Whitespace/Groß-Klein-Schreibung sind egal; Müll → `null`.
         *
         * Bewusst additiv eingeführt (statt [de.transio.hiuni.feature.grades.GradesUiState.semesterSortKey]
         * und `parseSemesterRange` zu ersetzen), damit deren getestete Sonder-Semantik
         * (Sort-Fallbacks bzw. Kalender-Ranges) unangetastet bleibt.
         */
        fun parseLabel(label: String): Semester? {
            val trimmed = label.trim()
            if (trimmed.isBlank()) return null
            val lower = trimmed.lowercase()

            val winter = lower.startsWith("wi") || lower.startsWith("ws")
            val summer = lower.startsWith("so") || lower.startsWith("ss")
            if (!winter && !summer) return null

            // Erstes Jahr im Label: entweder 4-stellig (2024) oder 2-stellig (24).
            // Ein evtl. folgendes „/25" ignorieren wir — maßgeblich ist das Start-Jahr.
            val match = YEAR_REGEX.find(trimmed) ?: return null
            val fourDigit = match.groupValues[1].takeIf { it.isNotBlank() }?.toIntOrNull()
            val twoDigit = match.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull()
            val year = fourDigit ?: twoDigit?.let { 2000 + it } ?: return null

            return Semester(if (winter) Period.WS else Period.SS, year)
        }

        /** 4-stelliges Jahr bevorzugt, sonst erstes 2-stelliges (→ 20xx). */
        private val YEAR_REGEX = Regex("""(\d{4})|(\d{2})""")

        /**
         * Anzahl Semester-Wechsel zwischen `start` (inklusive) und `end`. Gleicher
         * Semester → 0, ein Übergang dazwischen → 1, etc. Wert kann negativ sein
         * wenn `end < start`.
         */
        fun semestersBetween(start: Semester, end: Semester): Int =
            end.ordinal - start.ordinal

        /**
         * Frühestes Semester aus einer Menge von Labels (via [parseLabel]) — das
         * mit dem kleinsten [ordinal]. Unparsbare Labels werden ignoriert. `null`,
         * wenn keins parsebar ist. Basis für den Icon-Unlock-Anker aus dem echten
         * Studienverlauf (Noten + Kurse).
         */
        fun earliestOf(labels: Iterable<String>): Semester? =
            labels.mapNotNull { parseLabel(it) }.minByOrNull { it.ordinal }

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
