package de.transio.hiuni.feature.exams.studyplan

import de.transio.hiuni.core.common.DateTimeFormats
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Texte für die Lernplan-Vorschau (rein, damit testbar). */
object StudyPlanFormat {

    /** "18 Std", "1,5 Std", "45 Min". */
    fun hours(minutes: Long): String = when {
        minutes < 60 -> "$minutes Min"
        minutes % 60 == 0L -> "${minutes / 60} Std"
        else -> String.format(Locale.GERMAN, "%.1f Std", minutes / 60.0)
    }

    /** Überschrift der Vorschau, z. B. "12 Lerneinheiten". */
    fun headline(plan: StudyPlan): String = StudyPlanViewModel.unitsLabel(plan.blocks.size)

    /** Unterzeile der Vorschau, z. B. "18 Std · bis Fr 13. Nov". */
    fun subline(plan: StudyPlan, zone: ZoneId = ZoneId.systemDefault()): String {
        val last = plan.blocks.maxOfOrNull { it.start } ?: return ""
        val lastDate: LocalDate = last.atZone(zone).toLocalDate()
        return "${hours(plan.totalMinutes)} · bis ${lastDate.format(DateTimeFormats.dayShortNoComma)}"
    }

    /** Hinweis für einen leeren Plan. */
    fun emptyMessage(reason: StudyPlanEmptyReason?, hasCandidates: Boolean): String = when (reason) {
        StudyPlanEmptyReason.NO_EXAMS ->
            if (hasCandidates) {
                "Wähle mindestens eine Klausur aus."
            } else {
                "Keine anstehende Klausur mit Datum. Trag eine Klausur ein oder synchronisiere das LSF."
            }
        StudyPlanEmptyReason.TOO_SOON ->
            "Vor den ausgewählten Klausuren bleibt ab dem Starttag kein Lerntag. Starte heute, um die verbleibende Zeit zu nutzen."
        StudyPlanEmptyReason.NO_FREE_TIME, null ->
            "In deinem Lernfenster ist keine freie Zeit übrig. Erweitere das Zeitfenster, gib weniger freie Tage an oder verkürze Blocklänge und Puffer."
    }

    /** Kurzer Status einer Klausur in der Vorschau, `null` wenn alles passt. */
    fun issueLabel(summary: ExamPlanSummary): String? = when (summary.issue) {
        ExamPlanIssue.TOO_SOON -> "zu kurzfristig"
        ExamPlanIssue.NO_FREE_TIME -> "keine freie Zeit"
        ExamPlanIssue.PARTIAL ->
            "nur ${hours(summary.plannedMinutes.toLong())} von ${hours(summary.targetMinutes.toLong())}"
        null -> null
    }

    /** Zeile pro Klausur, z. B. "6 Einheiten · 9 Std". */
    fun examLine(summary: ExamPlanSummary): String {
        val units = if (summary.blockCount == 1) "1 Einheit" else "${summary.blockCount} Einheiten"
        return "$units · ${hours(summary.plannedMinutes.toLong())}"
    }
}
