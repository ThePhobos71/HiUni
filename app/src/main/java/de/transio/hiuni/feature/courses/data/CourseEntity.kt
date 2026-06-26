package de.transio.hiuni.feature.courses.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val professor: String,
    val credits: Int,
    val semester: String,
    val nextExamDate: LocalDate? = null,
    val attendedSessions: Int = 0,
    val totalSessions: Int = 0,
    val grade: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** "USER" (manuell) oder "LSF" (automatisch importiert, schreibgeschützt außer Note/Notes). */
    val source: String = SOURCE_USER,
    /** LSF publishid (Veranstaltung-ID). Nur gesetzt wenn source=LSF. */
    val lsfId: String? = null,
    /** Raumangabe aus LSF (z.B. "SC.B.0.37"). Nur gesetzt wenn source=LSF. */
    val room: String? = null,
    /** Status aus LSF: "angemeldet" oder "zugelassen". */
    val lsfStatus: String? = null,
    /** Semesterwochenstunden (aus LSF-Detail). */
    val sws: Int? = null,
    /** Lerninhalte / Veranstaltungsbeschreibung aus dem LSF-Detail. */
    val description: String? = null,
    /** Freie Bemerkung der Lehrenden zur Veranstaltung. */
    val remark: String? = null,
    /** Zielgruppe (z.B. "B.Sc. IMIT, B.Sc. Wirtschaftsinformatik"). */
    val targetAudience: String? = null,
    /** Modulkürzel (z.B. "IT-EINF1") aus der "LSF - Module"-Tabelle. */
    val moduleAbbreviation: String? = null,
    /**
     * LSF-Veranstaltungs-Nummer (4–5-stellig, z.B. "5395"). Steht aus User-Sicht
     * im Kursnamen-Suffix `… (5395)`, hier explizit als eigenes Feld gehalten,
     * damit das Klausur→Kurs-Matching deterministisch wird (ParsedExam liefert
     * dieselbe Nummer). Nur für LSF-Kurse gesetzt.
     */
    val lsfCode: String? = null,
    /** Veranstaltungsart aus LSF ("Vorlesung", "Tutorium", "Vorlesung mit Übung", …). */
    val courseType: String? = null,
    /**
     * lsfId der zugehörigen Mutter-Veranstaltung. Wird für Tutorien gesetzt, die
     * zu einer Vorlesung gehören (gleiches Semester + Modulkürzel). Null, wenn die
     * Veranstaltung selbst die Vorlesung/das Hauptmodul ist.
     */
    val parentLsfId: String? = null
) {
    val progress: Float
        get() = if (totalSessions > 0) {
            (attendedSessions.toFloat() / totalSessions).coerceIn(0f, 1f)
        } else 0f

    val isLsfManaged: Boolean
        get() = source == SOURCE_LSF

    /** Heuristik: Tutorium, Übung oder Praktikum — i.d.R. einem Hauptmodul untergeordnet. */
    val isTutoriumLike: Boolean
        get() = courseType?.lowercase()?.let { type ->
            "tutorium" in type || "übung" in type || "praktikum" in type
        } == true

    companion object {
        const val SOURCE_USER = "USER"
        const val SOURCE_LSF = "LSF"

        fun lsfRowId(lsfId: String): String = "lsf-$lsfId"
    }
}
