package de.transio.hiuni.feature.lsf.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Ein aus der LSF "Meine POS-Anmeldungen"-Tabelle gescrapter Klausur-Eintrag.
 *
 * `rowId` ist Room-Autogenerate; logischer Primärschlüssel ist
 * (`veranstaltungsNumber`, `semesterCode`) via Unique-Index — so kann derselbe
 * Eintrag pro Sync upserted werden ohne Duplikate, und derselbe Kurs in zwei
 * Semestern bleibt trotzdem unterscheidbar.
 *
 * [source] unterscheidet automatisch gescrapte LSF-Einträge ([SOURCE_LSF]) von
 * manuell erfassten Klausuren ([SOURCE_MANUAL]). KRITISCH: Der LSF-Sync
 * upserted/pruned NUR `source='LSF'`-Zeilen — manuelle Einträge müssen Syncs
 * überleben. Manuelle Einträge bekommen eine synthetische `veranstaltungsNumber`
 * (`man-<UUID>`), damit sie den Unique-Index nicht mit LSF-Nummern kollidieren
 * lassen und der LSF-Prune (`NOT IN keep`) sie nicht versehentlich trifft.
 *
 * Felder die LSF leer lassen darf (z.B. Klausurdatum noch nicht terminiert)
 * werden nullable abgebildet — der Home-Countdown rendert für `examDate==null`
 * dann "Termin noch offen".
 */
@Entity(
    tableName = "exams",
    indices = [
        Index("examDate"),
        Index(value = ["veranstaltungsNumber", "semesterCode"], unique = true)
    ]
)
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    /** Vierstellige Veranstaltungs-Nr aus dem Prüfungstext (z.B. "5395"). */
    val veranstaltungsNumber: String,
    /** Voller Prüfungstext der LSF-Zeile, für Display und Debug. */
    val pruefungstext: String,
    /** Name des Moduls, aus dem Prüfungstext extrahiert (alles nach "/ NUMMER "). */
    val moduleName: String,
    /** Optional: das Eltern-Modul links vom "--" Trenner (z.B. "Pflichtmodule Methoden"). */
    val parentModule: String?,
    /** Klausur-Datum (Cell 4). Null wenn noch nicht terminiert. */
    val examDate: LocalDate?,
    /** Erste Klausur-Uhrzeit aus Cell 5 (08:00, 10:00, …). Null wenn unbekannt. */
    val examTime: LocalTime?,
    /** Liste aller Räume aus Cell 5 (z.B. ["SC.A.0.09", "SC.B.0.37"]). Leer wenn unbekannt. */
    val rooms: List<String>,
    /** Semester-Anzeige aus Cell 2 (z.B. "SoSe 26"). */
    val semester: String,
    /** Maschinenlesbarer Semester-Code aus URL (z.B. "20261"). Für Unique-Index. */
    val semesterCode: String,
    /** Cell 3, Anmelde-Datum. */
    val registrationDate: LocalDate?,
    /** Cell 3, Abmeldung-Bis-Datum. */
    val cancellationDeadline: LocalDate?,
    /** Cell 1, Prüfer (oft leer). */
    val pruefer: String?,
    /** Match-Versuch (publishid, sonst Number-Prefix) → existierende CourseEntity. Null wenn unmatched. */
    val courseId: String?,
    /**
     * LSF `publishid` der zugehörigen Veranstaltung, falls die POS-Anmeldungs-
     * Tabelle einen direkten Link auf den Veranstaltungs-Eintrag enthält. Wird
     * bevorzugt fürs Course-Matching genutzt — Number-Prefix-Heuristik bleibt
     * Fallback. Null wenn die Tabellen-Zelle keinen `publishid`-Link liefert.
     */
    val lsfPublishId: String? = null,
    val fetchedAt: Instant = Instant.now(),
    /**
     * Herkunft des Eintrags: [SOURCE_LSF] (automatisch gescrapt) oder
     * [SOURCE_MANUAL] (vom User erfasst). Steuert Editierbarkeit im UI und
     * schützt manuelle Einträge vor dem LSF-Sync-Prune.
     */
    val source: String = SOURCE_LSF
) {
    /** True für manuell erfasste Klausuren — nur diese sind editier-/löschbar. */
    val isManual: Boolean get() = source == SOURCE_MANUAL

    companion object {
        const val SOURCE_LSF = "LSF"
        const val SOURCE_MANUAL = "MANUAL"

        /** Synthetische Veranstaltungs-Nr für manuelle Einträge (kollisionsfrei zu LSF). */
        fun manualNumber(): String = "man-${java.util.UUID.randomUUID()}"
    }
}
