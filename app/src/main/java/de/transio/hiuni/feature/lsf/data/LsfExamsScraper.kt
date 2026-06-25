package de.transio.hiuni.feature.lsf.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wird vom [LsfExamsRepository] geworfen, wenn die "Meine POS-Anmeldungen"-Seite
 * eine unerwartete Struktur hat (keine Tabelle gefunden, Header-Zeilen fehlen).
 * Der [de.transio.hiuni.core.sync.LsfSyncWorker] klassifiziert das als Fatal,
 * sodass nicht endlos retry-gehämmert wird.
 */
class ScrapeException(message: String) : RuntimeException(message)

/**
 * Parser für die LSF "Meine POS-Anmeldungen"-Tabelle.
 *
 * Erwartetes Markup (Spike 2026-06-27):
 *   <div class="content">
 *     <table border="0" width="100%">
 *       <tr><td colspan="7">Studiengang: B.Sc. …</td></tr>          ← Header-Row
 *       <tr><th class="tabelleheader">Prüfungstext</th>…</tr>      ← Spalten-Header
 *       <tr>                                                        ← Datenzeile
 *         <td class="mod_n"><Modul-Parent> -- <Modul-Name> / &nbsp;NUMMER&nbsp;<Veranstaltung></td>
 *         <td class="mod_n">Prüfer (oft leer)</td>
 *         <td class="mod_n"><span>SoSe 26</span></td>
 *         <td class="mod_n">15.06.2026 (verbindliche Anmeldung) -- Abmeldung bis zum 20.07.2026</td>
 *         <td class="mod_n">21.07.2026</td>
 *         <td class="mod_n">10:00 Uhr; Raum: SC.A.0.09;<br/>10:00 Uhr; Raum: SC.B.0.37;</td>
 *       </tr>
 *       …
 *     </table>
 *   </div>
 */
@Singleton
class LsfExamsScraper @Inject constructor() {

    /**
     * @param html roher HTML-Body der POS-Anmeldungs-Seite
     * @param semesterCode 5-stelliger Semester-Code (z.B. "20261") aus der URL,
     *                     wird als logischer Sekundär-Key in [ExamEntity] gespeichert
     */
    fun parse(html: String, semesterCode: String): List<ParsedExam> {
        val doc = Jsoup.parse(html)
        val table = locateTable(doc)
            ?: throw ScrapeException("POS-Anmeldungs-Tabelle nicht gefunden (kein <table border=0 width=100%> mit <th class=tabelleheader>)")

        val rows = table.select("> tbody > tr, > tr")
        if (rows.isEmpty()) {
            throw ScrapeException("POS-Anmeldungs-Tabelle ist leer (keine <tr>-Zeilen)")
        }

        val parsed = mutableListOf<ParsedExam>()
        for (row in rows) {
            // Header-Zeilen überspringen: Studiengang-Row hat ein <td colspan>, Spalten-Header
            // benutzen <th class="tabelleheader">.
            if (row.selectFirst("th.tabelleheader") != null) continue
            val firstCell = row.firstElementChild() ?: continue
            if (firstCell.tagName() == "td" && firstCell.hasAttr("colspan")) continue

            val cells = row.select("> td")
            if (cells.size < 5) continue  // Datenzeilen haben mindestens 5 td-Zellen

            runCatching {
                parseRow(cells, semesterCode)
            }.onFailure {
                Timber.w(it, "LsfExamsScraper: Row übersprungen — ${it.message}")
            }.getOrNull()?.let { parsed += it }
        }
        return parsed
    }

    /**
     * Findet die POS-Anmeldungs-Tabelle. LSF verschachtelt mehrere Tabellen für
     * Layout — wir suchen die mit den charakteristischen `th.tabelleheader`-Zellen,
     * die Prüfungs-Spalten beschriften.
     */
    private fun locateTable(doc: org.jsoup.nodes.Document): Element? {
        // Bevorzugt im div.content suchen, fallback auf das ganze Dokument.
        val scope = doc.selectFirst("div.content") ?: doc
        return scope.select("table[border=0][width=100%]")
            .firstOrNull { it.selectFirst("th.tabelleheader") != null }
            ?: scope.select("table").firstOrNull { it.selectFirst("th.tabelleheader") != null }
    }

    private fun parseRow(cells: org.jsoup.select.Elements, semesterCode: String): ParsedExam {
        val pruefungstextRaw = cleanWhitespace(cells[0].text())
        if (pruefungstextRaw.isBlank()) throw ScrapeException("leere Prüfungstext-Zelle")
        val (parentModule, moduleName, veranstaltungsNumber) = splitPruefungstext(pruefungstextRaw)
            ?: throw ScrapeException("Prüfungstext ohne erkennbare Veranstaltungs-Nr: $pruefungstextRaw")

        val pruefer = cells.getOrNull(1)?.text()?.let(::cleanWhitespace)?.takeIf { it.isNotBlank() }
        val semester = cells.getOrNull(2)?.text()?.let(::cleanWhitespace).orEmpty()

        val (registrationDate, cancellationDeadline) = parseAnmeldungZelle(cells.getOrNull(3))
        val examDate = cells.getOrNull(4)?.text()?.let(::cleanWhitespace)?.let(::parseDateOrNull)

        val klausurplanCell = cells.getOrNull(5)
        val (examTime, rooms) = if (examDate != null && klausurplanCell != null) {
            parseKlausurplan(klausurplanCell)
        } else {
            null to emptyList()
        }

        return ParsedExam(
            veranstaltungsNumber = veranstaltungsNumber,
            pruefungstext = pruefungstextRaw,
            moduleName = moduleName,
            parentModule = parentModule,
            examDate = examDate,
            examTime = examTime,
            rooms = rooms,
            semester = semester,
            semesterCode = semesterCode,
            registrationDate = registrationDate,
            cancellationDeadline = cancellationDeadline,
            pruefer = pruefer
        )
    }

    /**
     * Spaltet einen Prüfungstext der Form
     *   `<Eltern-Modul> -- <Modul-Name> / NUMMER <Veranstaltungsname>`
     * in seine drei Teile. Die NUMMER ist 4–5 Stellen. Beispiele:
     *   "Pflichtmodule Methoden -- Mathematische Methoden IV / 5395 Statistik"
     *   "Pflichtbereich Informatik -- Einführung Informatik / 3204 Einführung"
     */
    private fun splitPruefungstext(raw: String): Triple<String?, String, String>? {
        val numberMatch = NUMBER_REGEX.find(raw) ?: return null
        val veranstaltungsNumber = numberMatch.groupValues[1]

        // Alles VOR der Nummer-Region: enthält "<Eltern> -- <Modul-Name> /"
        val beforeNumber = raw.substring(0, numberMatch.range.first).trimEnd()
            .removeSuffix("/").trim()
        val (parent, moduleName) = if (DASH_SEPARATOR_REGEX.containsMatchIn(beforeNumber)) {
            val parts = DASH_SEPARATOR_REGEX.split(beforeNumber, limit = 2)
            parts[0].trim().takeIf { it.isNotBlank() } to parts.getOrNull(1)?.trim().orEmpty()
        } else {
            null to beforeNumber
        }
        val finalModuleName = moduleName.ifBlank { raw.substring(numberMatch.range.last + 1).trim() }
        return Triple(parent, finalModuleName, veranstaltungsNumber)
    }

    /**
     * Die Anmelde-Zelle enthält i.d.R. zwei Daten:
     *   `15.06.2026 (verbindliche Anmeldung) -- Abmeldung bis zum 20.07.2026`
     * Wir parsen das erste vorkommende Datum als `registrationDate`, das zweite
     * (falls vorhanden) als `cancellationDeadline`.
     */
    private fun parseAnmeldungZelle(cell: Element?): Pair<LocalDate?, LocalDate?> {
        if (cell == null) return null to null
        val text = cleanWhitespace(cell.text())
        val dates = DATE_REGEX.findAll(text)
            .mapNotNull { parseDateOrNull(it.value) }
            .toList()
        return dates.getOrNull(0) to dates.getOrNull(1)
    }

    /**
     * Die Klausurplan-Zelle enthält Multi-Line Uhrzeit+Raum-Paare, getrennt durch
     * `<br>`-Tags. Beispiel:
     *   `10:00 Uhr; Raum: SC.A.0.09;<br/>10:00 Uhr; Raum: SC.B.0.37;<br/>Zum <a>Klausurplan</a>`
     *
     * Erste Uhrzeit wird zurückgegeben (alle Räume teilen sich i.d.R. dieselbe
     * Uhrzeit). Alle Räume werden eingesammelt.
     */
    private fun parseKlausurplan(cell: Element): Pair<LocalTime?, List<String>> {
        // <br> als Trenner — die Zelle als Plain-Text-Segmente per Newline zerteilen.
        val marker = "[[BR]]"
        val withBreaks = cell.html().replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), marker)
        val text = Jsoup.parse(withBreaks).text().replace(" ", " ")
        val segments = text.split(marker).map { it.trim() }.filter { it.isNotEmpty() }

        var firstTime: LocalTime? = null
        val rooms = mutableListOf<String>()
        for (segment in segments) {
            if (firstTime == null) {
                TIME_REGEX.find(segment)?.let { match ->
                    firstTime = parseTimeOrNull(match.value)
                }
            }
            ROOM_REGEX.find(segment)?.let { match ->
                val room = match.groupValues[1].trim().trimEnd(';', ',', '.')
                if (room.isNotBlank() && room !in rooms) rooms += room
            }
        }
        return firstTime to rooms
    }

    private fun parseDateOrNull(raw: String): LocalDate? = runCatching {
        LocalDate.parse(raw, DATE_FORMATTER)
    }.getOrNull()

    private fun parseTimeOrNull(raw: String): LocalTime? = runCatching {
        LocalTime.parse(raw, TIME_FORMATTER)
    }.getOrNull()

    /** Reduziert beliebigen Whitespace (inkl. NBSP) auf einzelne Leerzeichen. */
    private fun cleanWhitespace(s: String): String =
        s.replace(" ", " ").replace(Regex("\\s+"), " ").trim()

    companion object {
        // 4–5 stellige Veranstaltungs-Nummer, idealerweise umgeben von Whitespace/NBSP.
        // Wir verlangen Whitespace links und Whitespace rechts, damit wir Year-Zahlen in
        // freiem Text nicht versehentlich matchen.
        private val NUMBER_REGEX = Regex("(?:^|\\s)(\\d{4,5})\\s")
        // Trenner zwischen Eltern-Modul und Modul-Name. LSF nutzt "--" mit Spaces drumherum.
        private val DASH_SEPARATOR_REGEX = Regex("\\s--\\s")
        private val DATE_REGEX = Regex("\\b(\\d{2}\\.\\d{2}\\.\\d{4})\\b")
        private val TIME_REGEX = Regex("\\b(\\d{2}:\\d{2})\\b")
        private val ROOM_REGEX = Regex("Raum:\\s*([^;<\\n]+)", RegexOption.IGNORE_CASE)
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

/**
 * Reine Parser-Ausgabe; Repository macht daraus eine [ExamEntity] inkl. Course-Matching.
 */
data class ParsedExam(
    val veranstaltungsNumber: String,
    val pruefungstext: String,
    val moduleName: String,
    val parentModule: String?,
    val examDate: LocalDate?,
    val examTime: LocalTime?,
    val rooms: List<String>,
    val semester: String,
    val semesterCode: String,
    val registrationDate: LocalDate?,
    val cancellationDeadline: LocalDate?,
    val pruefer: String?
)

