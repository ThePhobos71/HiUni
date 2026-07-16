package de.transio.hiuni.feature.grades.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Die Kopf-Summen des Notenspiegels: der offizielle Zwischen-GPA (Konto 8997
 * "BA-Notenzwischenkonto"), die dafür gewichteten Leistungspunkte und die
 * Gesamt-Leistungspunkte (Konto 8999 "Summe der LP").
 *
 * **Warum eigene Ein-Zeilen-Tabelle statt DataStore?**
 * Die drei Werte fallen im selben Parse-Durchlauf wie die Leistungszeilen an und
 * werden im UI zusammen mit den Noten angezeigt. Room gibt uns dafür einen
 * observe-Flow, der exakt so aussieht wie alle anderen Repository-Flows —
 * kein Sonderweg. DataStore müsste die drei korrelierten Felder von Hand
 * serialisieren, könnte NICHT transaktional konsistent mit der `grades`-Tabelle
 * geschrieben werden (Upsert + Summary im selben Room-Write), und der
 * observe-Flows-aus-Room-Pattern der übrigen Feature-Repos ginge verloren.
 * Eine feste Ein-Zeilen-Tabelle ([SINGLETON_ID]) ist hier das Einfachere.
 *
 * Alle Felder nullable: bevor der erste Sync lief bzw. wenn das QIS die
 * Summen-Konten (noch) nicht ausweist, bleibt die Zeile ganz weg oder trägt
 * `null` in den Einzelfeldern.
 */
@Entity(tableName = "grades_summary")
data class GradesSummaryEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    /** Offizieller Durchschnitt aus Konto 8997 (Komma-Dezimal "2,6" → 2.6). Null wenn nicht ausgewiesen. */
    val gpa: Double?,
    /** Gewichtete LP aus der Bonus-Spalte von Konto 8997 (z.B. 109). Null wenn nicht ausgewiesen. */
    val weightedLp: Int?,
    /** Gesamt-LP aus Konto 8999 "Summe der LP" (z.B. 121). Null wenn nicht ausgewiesen. */
    val totalLp: Int?,
    /** Zeitpunkt des Scrapes (Instant-Millis). */
    val fetchedAt: Long
) {
    companion object {
        /** Feste Primary-Key-ID — es gibt immer nur genau eine Summen-Zeile. */
        const val SINGLETON_ID = 0
    }
}
