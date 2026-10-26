package de.transio.hiuni.feature.exams.studyplan

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Einstellungen des Lernplan-Generators. Werden im Preferences-DataStore gehalten
 * ([StudyPlanSettingsStore]), damit der nächste Plan mit denselben Vorlieben startet.
 *
 * Das Startdatum selbst wird nicht als Datum gespeichert (wäre morgen schon veraltet),
 * sondern als Abstand zu heute: 0 = heute, 1 = morgen.
 */
data class StudyPlanSettings(
    /** Frühester Beginn eines Lernblocks am Tag. */
    val windowStart: LocalTime = LocalTime.of(9, 0),
    /** Spätestes Ende eines Lernblocks am Tag. */
    val windowEnd: LocalTime = LocalTime.of(20, 0),
    /** Obergrenze der Lernzeit pro Tag in Minuten. */
    val maxMinutesPerDay: Int = 240,
    /** Länge eines Lernblocks in Minuten. */
    val blockMinutes: Int = 90,
    /** Abstand zu bestehenden Terminen und zwischen zwei Lernblöcken in Minuten. */
    val bufferMinutes: Int = 15,
    /** Tage ohne Lernblöcke. */
    val freeDays: Set<DayOfWeek> = setOf(DayOfWeek.SUNDAY),
    /** Lernumfang: Stunden Vorbereitung pro Leistungspunkt. */
    val hoursPerCredit: Int = 3,
    /** Erinnerung vor jedem Block in Minuten, `null` = keine Erinnerung. */
    val reminderMinutesBefore: Int? = null,
    /** Start relativ zu heute: 0 = heute, 1 = morgen. */
    val startOffsetDays: Int = 1
) {
    companion object {
        /** Vorauswahl: Klausuren in den nächsten sechs Wochen. */
        const val DEFAULT_HORIZON_DAYS = 42L
    }
}

/** Eine Klausur als Planer-Eingabe. [credits] = LP des zugehörigen Kurses, falls bekannt. */
data class StudyPlanExam(
    val examId: Long,
    val title: String,
    val examStart: LocalDateTime,
    val credits: Int?
) {
    val examDate: LocalDate get() = examStart.toLocalDate()
}

/** Belegte Zeit im Kalender (Vorlesung, eigener Termin, Klausur, ...). */
data class BusyInterval(val start: Instant, val end: Instant)

/** Ein geplanter Lernblock. */
data class PlannedStudyBlock(
    val examId: Long,
    val title: String,
    val examDate: LocalDate,
    val start: Instant,
    val end: Instant
) {
    val minutes: Long get() = java.time.Duration.between(start, end).toMinutes()
}

/** Warum eine einzelne Klausur nicht (vollständig) eingeplant werden konnte. */
enum class ExamPlanIssue {
    /** Der letzte Lerntag (Vortag der Klausur) liegt vor dem Starttag. */
    TOO_SOON,

    /** Zwischen Start und Klausur gibt es keine freie Lücke. */
    NO_FREE_TIME,

    /** Es passt nur ein Teil des Lernumfangs in die freien Lücken. */
    PARTIAL
}

/** Kompakte Auswertung pro Klausur für die Vorschau. */
data class ExamPlanSummary(
    val examId: Long,
    val title: String,
    val examDate: LocalDate,
    val targetMinutes: Int,
    val plannedMinutes: Int,
    val blockCount: Int,
    val issue: ExamPlanIssue?
)

/** Warum der ganze Plan leer ist. */
enum class StudyPlanEmptyReason {
    /** Keine (ausgewählte) Klausur mit Datum in der Zukunft. */
    NO_EXAMS,

    /** Alle Klausuren liegen so nah, dass vor dem Starttag kein Lerntag bleibt. */
    TOO_SOON,

    /** Im Lernfenster ist keine freie Zeit übrig. */
    NO_FREE_TIME
}

/** Ergebnis des Planers: Blöcke chronologisch, Auswertung pro Klausur, ggf. Grund für einen leeren Plan. */
data class StudyPlan(
    val blocks: List<PlannedStudyBlock>,
    val exams: List<ExamPlanSummary>,
    val emptyReason: StudyPlanEmptyReason?
) {
    val totalMinutes: Long get() = blocks.sumOf { it.minutes }
    val isEmpty: Boolean get() = blocks.isEmpty()
}
