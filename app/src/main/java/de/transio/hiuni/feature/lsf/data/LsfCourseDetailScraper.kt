package de.transio.hiuni.feature.lsf.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser für die Veranstaltungs-Detailseite (`state=verpublish&publishid=NNN&moduleCall=webInfo`).
 *
 * Anders als das Listing trägt diese Seite Credits/SWS/Veranstaltungsart sowie
 * die durchführende Lehrperson und Lerninhalte. Wir fetchen sie nur einmal je
 * Veranstaltung (siehe Repository) um LSF nicht unnötig zu belasten.
 */
@Singleton
class LsfCourseDetailScraper @Inject constructor() {

    fun parse(html: String): LsfCourseDetail {
        return runCatching {
            val doc = Jsoup.parse(html)
            val grunddaten = doc.selectFirst("table[summary*=Grunddaten]")
            val credits = grunddaten?.let { findThNumber(it, "Credits") }
            val sws = grunddaten?.let { findThNumber(it, "SWS") }
            val art = grunddaten?.let { findThText(it, "Veranstaltungsart") }
            val kurztext = grunddaten?.let { findThText(it, "Kurztext") }
            val responsible = findResponsiblePerson(doc)
            val description = findInhaltField(doc, "Lerninhalte")
            val remark = findInhaltField(doc, "Bemerkung")
            val targetAudience = findInhaltField(doc, "Zielgruppe")
            val moduleAbbreviation = findModuleAbbreviation(doc)
            LsfCourseDetail(
                credits = credits,
                sws = sws,
                veranstaltungsart = art,
                kurztext = kurztext,
                responsiblePerson = responsible,
                description = description,
                remark = remark,
                targetAudience = targetAudience,
                moduleAbbreviation = moduleAbbreviation
            )
        }.onFailure { Timber.w(it, "LsfCourseDetailScraper.parse failed") }
            .getOrDefault(LsfCourseDetail.EMPTY)
    }

    /** Findet `<th>Label</th><td>Wert</td>` und extrahiert den Wert als String. */
    private fun findThText(scope: Element, label: String): String? {
        val th = scope.select("th").firstOrNull { it.text().trim().equals(label, ignoreCase = true) }
            ?: return null
        return th.nextElementSibling()?.text()?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Wie [findThText], aber parsed das Ergebnis als Integer (erste Ziffernfolge).
     *
     * Spezialfälle aus dem LSF:
     *   • "siehe 3530" → kein eigener Wert, sondern Verweis auf andere Veranstaltung
     *     (typisch bei Tutorien). Returnt null statt der referenzierten Modulnummer.
     *   • Plausibilitäts-Check: ECTS sind real 1..30, SWS auch. Größere Zahlen sind
     *     fast immer Modulnummern oder andere Referenzen — nicht als Wert übernehmen.
     */
    private fun findThNumber(scope: Element, label: String): Int? {
        val raw = findThText(scope, label) ?: return null
        if (raw.contains("siehe", ignoreCase = true)) return null
        val candidate = INT_REGEX.find(raw)?.value?.toIntOrNull() ?: return null
        if (candidate !in 0..30) return null
        return candidate
    }

    /**
     * Sucht in der "Durchführende Dozenten"-Tabelle die erste Person mit Zuständigkeit
     * "verantwortlich und durchführend" und gibt deren Namen sauber formatiert zurück.
     * Format aus LSF: "Nachname, Vorname, Titel" → wir flippen zu "Titel Vorname Nachname"
     * wenn das Schema klar ist.
     */
    private fun findResponsiblePerson(doc: Document): String? {
        val table = doc.selectFirst("table[summary*=Durchführende]") ?: return null
        val rows = table.select("tr")
        var best: String? = null
        var bestRank = Int.MAX_VALUE
        for (row in rows) {
            val cells = row.select("td")
            if (cells.size < 2) continue
            val name = cells[0].selectFirst("a")?.text()?.trim()
                ?: cells[0].text().trim()
            val role = cells[1].text().trim().lowercase()
            if (name.isBlank()) continue
            // Negationen explizit raus: "nicht durchführend" enthält "durchführend" als
            // Substring — naive Containment-Tests würden so Personen mit "weder noch"
            // als verantwortlich/durchführend einstufen.
            val isResponsible = "verantwortlich" in role && "nicht verantwortlich" !in role
            val isLeading = "durchführend" in role && "nicht durchführend" !in role
            val rank = when {
                isResponsible && isLeading -> 0
                isResponsible -> 1
                isLeading -> 2
                else -> 3
            }
            if (rank < bestRank) {
                best = name
                bestRank = rank
            }
        }
        return best?.let(::reformatPersonName)
    }

    /**
     * "Fuchs-Kreiß, Alexander, Professor Dr." → "Prof. Dr. Alexander Fuchs-Kreiß"
     * Lässt unbekannte Formate unverändert.
     */
    private fun reformatPersonName(raw: String): String {
        val parts = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 2) return raw
        val nachname = parts[0]
        val vorname = parts[1]
        val titel = parts.drop(2).joinToString(" ")
            .replace("Professor", "Prof.")
            .replace("Doktor", "Dr.")
            .replace(Regex("\\s+"), " ")
            .trim()
        return listOf(titel, vorname, nachname).filter { it.isNotBlank() }.joinToString(" ")
    }

    /**
     * Findet eine Zeile in der "Weitere Angaben"-Tabelle anhand der Th-Label
     * (Literatur, Bemerkung, Lerninhalte, Zielgruppe) und liefert den darin
     * enthaltenen Plain-Text zurück. Mehrfache Whitespace-Sequenzen werden auf
     * Single-Newline reduziert, weil LSF mit Indentation um sich wirft.
     */
    private fun findInhaltField(doc: Document, label: String): String? {
        val rows = doc.select("table[summary*=Weitere Angaben] tr")
        for (row in rows) {
            val th = row.selectFirst("th") ?: continue
            if (!th.text().trim().equals(label, ignoreCase = true)) continue
            val td = row.selectFirst("td.mod_n") ?: row.selectFirst("td") ?: continue
            val text = td.wholeText()
                .replace(' ', ' ')
                .replace(Regex("[ \\t]+"), " ")
                .replace(Regex("\\s*\n\\s*"), "\n")
                .trim()
            return text.takeIf { it.isNotBlank() }
        }
        return null
    }

    /**
     * Liest aus der Tabelle mit Caption "LSF - Module" das Modulkürzel der ersten
     * Zeile. Bei mehreren Modul-Zuordnungen nehmen wir die erste — typischerweise
     * gibt es ohnehin nur eines, und wenn nicht, ist die erste Zeile die relevanteste.
     */
    private fun findModuleAbbreviation(doc: Document): String? {
        val table = doc.select("table").firstOrNull { table ->
            table.selectFirst("caption")?.text()?.contains("LSF - Module", ignoreCase = true) == true
        } ?: return null
        val firstDataRow = table.select("tr").firstOrNull { it.select("td").isNotEmpty() }
            ?: return null
        return firstDataRow.select("td").firstOrNull()?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        private val INT_REGEX = Regex("\\d+")
    }
}

/**
 * Ergebnis des Detail-Scrapers für eine Veranstaltung. Felder sind nullable weil
 * LSF sie nicht garantiert ausfüllt.
 */
data class LsfCourseDetail(
    val credits: Int?,
    val sws: Int?,
    val veranstaltungsart: String?,
    val kurztext: String?,
    val responsiblePerson: String?,
    val description: String?,
    val remark: String?,
    val targetAudience: String?,
    val moduleAbbreviation: String?
) {
    val isEmpty: Boolean
        get() = credits == null && sws == null && veranstaltungsart == null &&
            kurztext == null && responsiblePerson == null && description == null &&
            remark == null && targetAudience == null && moduleAbbreviation == null

    companion object {
        val EMPTY = LsfCourseDetail(null, null, null, null, null, null, null, null, null)
    }
}
