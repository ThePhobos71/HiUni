package de.transio.hiuni.feature.grades.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Status einer Studien-/Prüfungsleistung aus dem LSF-Notenspiegel.
 *
 * Storage als TEXT via [de.transio.hiuni.core.database.Converters.gradeStatusToString];
 * unbekannte Werte aus künftigen LSF-Änderungen fallen still auf [REGISTERED] zurück
 * (der harmloseste Zustand — keine Note, kein Bestehen impliziert).
 */
enum class GradeStatus {
    /** LSF-Status "bestanden". */
    PASSED,

    /** LSF-Status "nicht bestanden". */
    FAILED,

    /** LSF-Status "angemeldet" (Prüfung steht noch aus, keine Note). */
    REGISTERED
}

/**
 * Eine einzelne Leistungszeile aus dem LSF/QIS-Notenspiegel (Seitenansicht "lang").
 *
 * `rowId` ist Room-Autogenerate; der LOGISCHE Merge-Key ist [mergeKey]:
 *  - `labnr`, wenn die Zeile einen Klassenspiegel-Info-Link mit stabiler Prüfungs-ID
 *    (`pruefung:labnr=<ID>`) trägt — das ist der verlässlichste Schlüssel und überlebt
 *    Titel-/Semester-Umbenennungen.
 *  - sonst `pruefungsNr#versuch` — für Zeilen ohne Info-Link (z.B. frisch angemeldete
 *    Prüfungen ohne Klassenspiegel). Der Versuch trennt Wiederholungen derselben
 *    Prüfungsnummer.
 *
 * Der Unique-Index über [mergeKey] macht das Diff-Upsert im [GradesRepository]
 * idempotent, sodass ein erneuter Sync bestehende Zeilen aktualisiert statt zu
 * duplizieren.
 *
 * KONTO-/Summen-Zeilen des Notenspiegels (qis_konto/qis_kontoOnTop, 8997/8999)
 * werden NICHT als [GradeEntity] persistiert — die Kopf-Summen liegen separat in
 * [GradesSummaryEntity], die reinen Gruppen-Zeilen werden verworfen.
 */
@Entity(
    tableName = "grades",
    indices = [
        Index(value = ["mergeKey"], unique = true),
        Index("kontoNr")
    ]
)
data class GradeEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    /**
     * Logischer Merge-Key (siehe KDoc der Klasse): `l:<labnr>` wenn labnr vorhanden,
     * sonst `p:<pruefungsNr>#<versuch>`. Wird vom Scraper/Repository berechnet und
     * ist der Anker für Diff-Upsert + Prune.
     */
    val mergeKey: String,
    /** Stabile LSF-Prüfungs-ID aus dem Klassenspiegel-Link (`pruefung:labnr=<ID>`). Null wenn kein Info-Link. */
    val labnr: Long?,
    /** Prüfungsnummer aus Spalte 1 (z.B. "23011", "214103", "1801"). */
    val pruefungsNr: String,
    /** Bezeichnung der Leistung / Lehrveranstaltung (Spalte 2, ohne die Link-Anhänge). */
    val titel: String,
    /** Nummer des übergeordneten Kontos (qis_konto), unter dem die Zeile im Baum hängt (z.B. "1100"). Null wenn keins vorausging. */
    val kontoNr: String?,
    /** Anzeigename des übergeordneten Kontos (z.B. "Pflichtmodule Wirtschaftsinformatik"). */
    val kontoName: String?,
    /** Anzeige-Semester aus Spalte 3 (z.B. "WiSe 24/25", "SoSe 26"). */
    val semester: String,
    /** Note als Double (Komma-Dezimal "2,7" → 2.7). Null bei angemeldet / bestanden-ohne-Note. */
    val note: Double?,
    /** Status der Leistung (Spalte 5). */
    val status: GradeStatus,
    /** Bonus/Leistungspunkte aus Spalte 6 (Int). 0 wenn leer. */
    val bonusLp: Int,
    /** Vermerk aus Spalte 7 (z.B. "(Klausur)"). Leer wenn keiner. */
    val vermerk: String,
    /** Versuch aus Spalte 8 (1..4). */
    val versuch: Int,
    /** Prüfungsdatum aus Spalte 9 als EpochDay. Null wenn leer/nicht terminiert. */
    val pruefungsDatum: Long?,
    /** Zeitpunkt des Scrapes (Instant-Millis). */
    val fetchedAt: Long
) {
    companion object {
        /** Merge-Key aus labnr (bevorzugt) bzw. Prüfungsnr+Versuch. */
        fun mergeKeyFor(labnr: Long?, pruefungsNr: String, versuch: Int): String =
            if (labnr != null) "l:$labnr" else "p:$pruefungsNr#$versuch"
    }
}
