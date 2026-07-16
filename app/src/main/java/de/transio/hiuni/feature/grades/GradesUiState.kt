package de.transio.hiuni.feature.grades

import de.transio.hiuni.feature.grades.data.GradeEntity
import de.transio.hiuni.feature.grades.data.GradeStatus

/**
 * Eine nach Semester gruppierte Sektion des Notenspiegels.
 *
 * Die Reihenfolge der Sektionen ist „neuestes Semester zuerst" (siehe
 * [GradesUiState.semesters]); die Zeilen innerhalb einer Sektion behalten die
 * DAO-Reihenfolge (Prüfungsdatum aufsteigend, undatiert ans Ende, dann Versuch).
 */
data class SemesterSection(
    /** Anzeige-Semester (z.B. "WiSe 24/25", "SoSe 26"). */
    val semester: String,
    /** Sortier-Schlüssel für „neuestes zuerst" (siehe [GradesUiState.semesterSortKey]). Absteigend. */
    val sortKey: Int,
    val grades: List<GradeEntity>
) {
    /** Summe der bestandenen LP dieser Sektion — für die optionale Sektions-Zusammenfassung. */
    val passedLp: Int
        get() = grades.filter { it.status == GradeStatus.PASSED }.sumOf { it.bonusLp }
}

/**
 * View-State des Noten-Screens.
 *
 * Zustands-Konventionen (analog Learnweb/Exams):
 *  - [isLoading]: erster Cold-Start-Roundtrip läuft und es liegt noch KEIN Cache
 *    vor → Skeleton. Wird `false`, sobald der erste Refresh (Erfolg oder Fehler)
 *    durch ist.
 *  - [isRefreshing]: Pull-to-Refresh über vorhandenem/leerem Cache.
 *  - [errorMessage]: letzter Refresh-Fehler; bei vorhandenem Cache als Snackbar,
 *    ohne Cache als [de.transio.hiuni.core.design.components.ErrorState].
 *  - [isAuthRequired]: CAS-Session fehlt/abgelaufen → Hinweis-Karte mit Absprung
 *    zu den CAS-Login-Settings.
 */
data class GradesUiState(
    /** GPA aus Konto 8997. Null wenn (noch) nicht ausgewiesen. */
    val gpa: Double? = null,
    /** Gesamt-LP aus Konto 8999 „Summe der LP". Null wenn (noch) nicht ausgewiesen. */
    val totalLp: Int? = null,
    /** Nach Semester gruppierte Leistungen, neuestes Semester zuerst. */
    val semesters: List<SemesterSection> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    /** CAS-Session fehlt/abgelaufen → Login-Hinweis statt Daten. */
    val isAuthRequired: Boolean = false,
    /**
     * Session abgelaufen, aber Cache vorhanden → dezente Reauth-Hinweis-Karte
     * ÜBER den (stale) Daten, statt die Daten zu verstecken. Komplementär zu
     * [isAuthRequired] (das nur ohne Cache greift).
     */
    val showReauthBanner: Boolean = false,
    /** Prozessweiter Netz-Status. Für die Stale-/Offline-Kennzeichnung unter der TopBar. */
    val isOnline: Boolean = true,
    /** Epoch-ms des letzten erfolgreichen Noten-Refresh (0 = nie). Speist das StalenessLabel. */
    val lastRefreshEpoch: Long = 0L,
    val errorMessage: String? = null
) {
    /** Fortschritts-Anteil 0f..1f für den ECTS-Balken (X / [TARGET_LP]). */
    val ectsProgress: Float
        get() = ((totalLp ?: 0).toFloat() / TARGET_LP).coerceIn(0f, 1f)

    /** True wenn wir überhaupt etwas anzuzeigen haben (Leistungen ODER Summen). */
    val hasContent: Boolean
        get() = semesters.isNotEmpty() || gpa != null || totalLp != null

    companion object {
        /** Ziel-LP für den Bachelor-Fortschritt (180 CP Regelstudienzeit). */
        const val TARGET_LP = 180

        /**
         * Gruppiert die flache Leistungsliste nach [GradeEntity.semester] und
         * sortiert die Sektionen „neuestes zuerst". Zeilen-Reihenfolge innerhalb
         * einer Sektion bleibt so, wie das DAO sie liefert.
         */
        fun groupBySemester(grades: List<GradeEntity>): List<SemesterSection> =
            grades.groupBy { it.semester }
                .map { (semester, rows) ->
                    SemesterSection(
                        semester = semester,
                        sortKey = semesterSortKey(semester),
                        grades = rows
                    )
                }
                // Neuestes Semester zuerst: primär nach berechnetem Sort-Key
                // (Jahr*2 + So/Wi), sekundär alphabetisch absteigend als Fallback
                // für unparsbare Labels.
                .sortedWith(
                    compareByDescending<SemesterSection> { it.sortKey }
                        .thenByDescending { it.semester }
                )

        /**
         * Übersetzt ein Semester-Label in einen monoton steigenden Sortier-Key,
         * sodass „neuer" = „größer". Muster:
         *  - „SoSe 26" / „SS 2026" → Jahr 2026, Sommer (Offset 1)
         *  - „WiSe 24/25" / „WS 2024/25" → Jahr 2024, Winter (Offset 0, aber nach
         *    dem SoSe desselben Anfangsjahres → wir zählen Winter als späteren Teil
         *    des Anfangsjahres, also year*2 + 0? Nein: WiSe folgt auf SoSe, muss also
         *    größer sein → Winter bekommt Offset 1, Sommer Offset 0? )
         *
         * Konvention hier: Studienjahr läuft SoSe (früher) → WiSe (später). Also
         * `year*2 + (winter ? 1 : 0)`. Nicht erkannte Labels → [Int.MIN_VALUE], damit
         * sie ans Ende (unten) sortieren, aber der alphabetische thenBy sie stabil hält.
         */
        fun semesterSortKey(label: String): Int {
            val lower = label.lowercase()
            val winter = lower.startsWith("wi") || lower.startsWith("ws")
            // Erste 4-stellige Jahreszahl, sonst erste 2-stellige (→ 20xx).
            val fourDigit = Regex("""(\d{4})""").find(label)?.groupValues?.get(1)?.toIntOrNull()
            val twoDigit = Regex("""(\d{2})""").find(label)?.groupValues?.get(1)?.toIntOrNull()
            val year = fourDigit ?: twoDigit?.let { 2000 + it } ?: return Int.MIN_VALUE
            return year * 2 + if (winter) 1 else 0
        }
    }
}
