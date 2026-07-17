package de.transio.hiuni.feature.grades.data

import de.transio.hiuni.feature.lsf.data.ScrapeException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser für den LSF/QIS-Notenspiegel (Seitenansicht "lang").
 *
 * Der Notenspiegel wird zweistufig geladen (siehe [GradesRepository]):
 *  1. Menü-Seite "Veranstaltungsmanagement" → [findNotenspiegelUrl] extrahiert den
 *     session-gebundenen Notenspiegel-Link inkl. `asi`-Token.
 *  2. Notenspiegel-URL mit `P_vx=lang` → [parse] liest die Leistungstabelle.
 *
 * Zeilen werden ROBUST über ihre td-Klassen klassifiziert, NIE über die
 * Reihenfolge im Dokument:
 *  - `td.qis_kontoOnTop` / `td.qis_konto` → Konto-/Gruppen-Zeile. Wird NICHT als
 *    Leistung persistiert. Die Spezial-Konten 8997 (GPA + gewichtete LP) und 8999
 *    (Summe LP) werden zu einer [ParsedSummary] extrahiert.
 *  - `td.tabelle1_alignleft` (Prüfungsnr in Spalte 1) → Leistungszeile.
 *  - alles andere (Header, Legende, unerwartete Zeilen) → übersprungen.
 *
 * Bei strukturell kaputtem HTML (keine Leistungstabelle auffindbar) wirft [parse]
 * eine [ScrapeException] — der [de.transio.hiuni.core.sync.LsfSyncWorker]
 * klassifiziert das als Fatal.
 */
@Singleton
class NotenspiegelScraper @Inject constructor() {

    /**
     * Extrahiert die Notenspiegel-URL (mit frischem, session-gebundenem `asi`-Token)
     * aus der "Veranstaltungsmanagement"-Menüseite.
     *
     * Robust: sucht in `#makronavigation` einen `<a>` mit `state=notenspiegelStudent`
     * in der href; Fallback über den Link-Text "Notenspiegel". Der `asi`-Token MUSS
     * bei jedem Sync frisch hier gezogen werden (er ist an die JSESSIONID gebunden).
     *
     * @return absolute URL oder `null`, wenn der Link fehlt (dann behandelt das
     *   Repository das als Auth-/Freischaltungs-Problem, analog LsfExamsRepository).
     */
    fun findNotenspiegelUrl(menuHtml: String): String? {
        val doc = Jsoup.parse(menuHtml)
        val nav = doc.selectFirst("#makronavigation") ?: doc
        val byState = nav.select("a[href*=state=notenspiegelStudent]").firstOrNull()
            ?.absUrl("href")
            ?.takeIf { it.isNotBlank() }
        if (byState != null) return byState
        // Fallback: Link-Text.
        return nav.select("a")
            .firstOrNull { it.text().contains("Notenspiegel", ignoreCase = true) }
            ?.absUrl("href")
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Parst die Notenspiegel-Tabelle.
     *
     * @return [NotenspiegelResult] mit allen Leistungszeilen und (falls vorhanden)
     *   den Kopf-Summen.
     * @throws ScrapeException wenn keine Leistungstabelle auffindbar ist.
     */
    fun parse(html: String): NotenspiegelResult {
        val doc = Jsoup.parse(html)
        val table = locateTable(doc)
            ?: throw ScrapeException(
                "Notenspiegel-Leistungstabelle nicht gefunden (keine <table> mit <th class=tabelleheader> " +
                    "und qis_konto-/tabelle1_-Zeilen)"
            )

        val rows = table.select("> tbody > tr, > tr")
        if (rows.isEmpty()) {
            throw ScrapeException("Notenspiegel-Tabelle ist leer (keine <tr>-Zeilen)")
        }

        val grades = mutableListOf<ParsedGrade>()
        var summaryGpa: Double? = null
        var summaryWeightedLp: Int? = null
        var summaryTotalLp: Int? = null

        // Konto-Kontext: die zuletzt gesehene Gruppen-Zeile (qis_konto/qis_kontoOnTop),
        // unter der die nachfolgenden Leistungszeilen hängen. LSF verschachtelt Konten,
        // wir merken uns das jeweils zuletzt genannte NICHT-Summen-Konto als Parent.
        var currentKontoNr: String? = null
        var currentKontoName: String? = null

        for (row in rows) {
            val firstTd = row.selectFirst("td") ?: continue
            val cls = firstTd.className()

            when {
                cls.contains("qis_kontoOnTop") || cls.contains("qis_konto") -> {
                    val konto = parseKonto(row)
                    if (konto != null) {
                        when (konto.nr) {
                            KONTO_GPA -> {
                                // 8997: GPA in Note-Spalte, gewichtete LP in Bonus-Spalte.
                                konto.noteValue?.let { summaryGpa = it }
                                konto.bonusValue?.let { summaryWeightedLp = it }
                            }
                            KONTO_TOTAL_LP -> {
                                // 8999: Gesamt-LP in Bonus-Spalte.
                                konto.bonusValue?.let { summaryTotalLp = it }
                            }
                            else -> {
                                // Reguläres Gruppen-Konto → als Parent-Kontext merken.
                                // (Zeilen ohne Nummer — die Trailing-Duplikate von 8997/8999
                                //  mit colspan=2 — liefern konto.nr==null und werden ignoriert.)
                                if (konto.nr != null) {
                                    currentKontoNr = konto.nr
                                    currentKontoName = konto.name
                                }
                            }
                        }
                    }
                }

                cls.contains("tabelle1_") -> {
                    runCatching {
                        parseGradeRow(row, currentKontoNr, currentKontoName)
                    }.onFailure {
                        Timber.w(it, "NotenspiegelScraper: Leistungszeile übersprungen — ${it.message}")
                    }.getOrNull()?.let { grades += it }
                }

                else -> {
                    // Header-/Legende-/unbekannte Zeile: still überspringen.
                }
            }
        }

        val summary = if (summaryGpa != null || summaryWeightedLp != null || summaryTotalLp != null) {
            ParsedSummary(gpa = summaryGpa, weightedLp = summaryWeightedLp, totalLp = summaryTotalLp)
        } else {
            null
        }
        return NotenspiegelResult(grades = grades, summary = summary)
    }

    /**
     * Findet die Leistungstabelle. Die Stammdaten-Tabelle (Name/Matrikel) trägt
     * `summary="Liste der Stammdaten…"` und wird bewusst ausgeschlossen — wir
     * suchen die Tabelle mit `th.tabelleheader` (Spalten-Header der Leistungen).
     */
    private fun locateTable(doc: Document): Element? {
        val scope = doc.selectFirst("div.content") ?: doc
        return scope.select("table")
            .firstOrNull { table ->
                table.selectFirst("th.tabelleheader") != null &&
                    table.selectFirst("td.qis_kontoOnTop, td.qis_konto, td.tabelle1_alignleft") != null
            }
    }

    /**
     * Parst eine Konto-/Gruppen-Zeile (qis_kontoOnTop/qis_konto).
     *
     * Spalten-Layout ist bei Konto-Zeilen uneinheitlich (colspan variiert), daher
     * greifen wir gezielt:
     *  - Nummer: `<b>`-Element in der ersten Zelle (z.B. "8997").
     *  - Name: die Bezeichnungs-Zelle (colspan=2 bei kontoOnTop, sonst zweite Zelle).
     *  - noteValue/bonusValue: nur relevant für die Summen-Konten; wir lesen die
     *    Note- und Bonus-Spalte per Klassen-unabhängiger Positionslogik NICHT —
     *    stattdessen scannen wir alle Zellen nach den charakteristischen Werten.
     *
     * Für 8997/8999 sind die relevanten Werte eindeutig: die Note-Spalte (align=right,
     * Komma-Dezimal) und die Bonus-Spalte (align=right, Integer). Wir identifizieren
     * sie über den Zellinhalt statt über die Position.
     */
    private fun parseKonto(row: Element): ParsedKonto? {
        val cells = row.select("> td")
        if (cells.isEmpty()) return null
        val nr = cells[0].selectFirst("b")?.text()?.let(::cleanWhitespace)?.takeIf { it.isNotBlank() }
        val name = cells.getOrNull(1)?.text()?.let(::cleanWhitespace).orEmpty()

        // Für die Summen-Konten: GPA (Komma-Dezimal) und LP (Int) aus den align=right-
        // Zellen. Bei 8997 steht die 2,6 in der Note-Spalte und 109 in der Bonus-Spalte;
        // bei 8999 nur 121 in der Bonus-Spalte. Wir sammeln beide getrennt ein.
        var noteValue: Double? = null
        var bonusValue: Int? = null
        if (nr == KONTO_GPA || nr == KONTO_TOTAL_LP) {
            for (cell in cells.drop(1)) {
                val text = cleanWhitespace(cell.text())
                if (text.isBlank()) continue
                val d = parseNoteOrNull(text)
                if (d != null && noteValue == null) {
                    noteValue = d
                    continue
                }
                val i = text.toIntOrNull()
                if (i != null && bonusValue == null) {
                    bonusValue = i
                }
            }
        }
        return ParsedKonto(nr = nr, name = name, noteValue = noteValue, bonusValue = bonusValue)
    }

    /**
     * Parst eine Leistungszeile (`td.tabelle1_*`). Spalten (Ansicht "lang"):
     *   0 Prüfungsnr | 1 Bezeichnung (+ Klassenspiegel-Info-Link + Veranstaltungslink)
     *   | 2 Semester | 3 Note | 4 Status | 5 Bonus | 6 Vermerk | 7 Versuch
     *   | 8 Prüfungsdatum | (9 optional "(zuletzt geändert…)")
     *
     * Robustheit: Die td-Klassen (`tabelle1_alignleft/_alignright/_aligncenter`) sind
     * konsistent, aber die trailing "(zuletzt geändert…)"-Zelle hat GAR KEINE Klasse.
     * Wir arbeiten daher über die Positionen der ersten 9 Zellen; die Prüfungsnr in
     * Zelle 0 dient als Plausibilitäts-Anker.
     */
    private fun parseGradeRow(row: Element, kontoNr: String?, kontoName: String?): ParsedGrade {
        val cells = row.select("> td")
        if (cells.size < 9) throw ScrapeException("Leistungszeile hat nur ${cells.size} Zellen (erwartet ≥9)")

        val pruefungsNr = cleanWhitespace(cells[0].text())
        if (pruefungsNr.isBlank()) throw ScrapeException("leere Prüfungsnr")

        val bezeichnungCell = cells[1]
        val labnr = extractLabnr(bezeichnungCell)
        val titel = extractTitel(bezeichnungCell)
        val veranstaltungsNr = extractVeranstaltungsNr(bezeichnungCell)

        val semester = cleanWhitespace(cells[2].text())
        val note = parseNoteOrNull(cleanWhitespace(cells[3].text()))
        val status = parseStatus(cleanWhitespace(cells[4].text()))
        val bonusLp = cleanWhitespace(cells[5].text()).toIntOrNull() ?: 0
        val vermerk = cleanWhitespace(cells[6].text())
        val versuch = cleanWhitespace(cells[7].text()).toIntOrNull() ?: 1
        val pruefungsDatum = parseDateOrNull(cleanWhitespace(cells[8].text()))

        return ParsedGrade(
            labnr = labnr,
            pruefungsNr = pruefungsNr,
            titel = titel,
            veranstaltungsNr = veranstaltungsNr,
            kontoNr = kontoNr,
            kontoName = kontoName,
            semester = semester,
            note = note,
            status = status,
            bonusLp = bonusLp,
            vermerk = vermerk,
            versuch = versuch,
            pruefungsDatum = pruefungsDatum
        )
    }

    /**
     * Extrahiert die stabile Prüfungs-ID aus dem Klassenspiegel-Info-Link. Dessen
     * href enthält (URL-encoded) `pruefung%3Alabnr%3D<ID>`. Wir dekodieren nicht,
     * sondern matchen direkt gegen die encoded-Form ODER die dekodierte Form, damit
     * beide Varianten (mit/ohne Jsoup-Decoding) greifen. Null wenn kein Info-Link.
     */
    private fun extractLabnr(cell: Element): Long? {
        val href = cell.select("a[href*=labnr]").firstOrNull()?.attr("href") ?: return null
        return LABNR_REGEX.find(href)?.groupValues?.get(1)?.toLongOrNull()
    }

    /**
     * Bezeichnung ohne die angehängten Link-Texte. Die Zelle enthält den Titel als
     * ersten Text-Knoten, danach den Info-Link (Bild) und via `<br>` den
     * Veranstaltungslink ("35061 Web und Datenbankenpraktikum …"). Wir nehmen den
     * Text VOR dem ersten `<a>`/`<br>` — das ist der reine Titel.
     */
    private fun extractTitel(cell: Element): String {
        // Erster direkter Text-Knoten der Zelle ist der Titel (vor Info-Link/<br>).
        val ownText = cell.ownText()
        if (ownText.isNotBlank()) return cleanWhitespace(ownText)
        // Fallback: alles bis zum ersten "/" (LSF trennt Titel und Veranstaltung mit " / ").
        val full = cleanWhitespace(cell.text())
        return full.substringBefore(" / ").trim().ifBlank { full }
    }

    /**
     * Führende Veranstaltungs-Nr aus dem Veranstaltungslink der Bezeichnungsspalte.
     * Der `<a href*=publishSubDir=veranstaltung>` trägt Text wie
     * „3202 Betriebliche Informationssysteme (Vorlesung)" (Zahl per `&nbsp;` vom
     * Titel getrennt) — wir nehmen die führende Ziffernfolge. Fallback: erster
     * Veranstaltungslink überhaupt. Null, wenn kein passender Link / keine Zahl.
     */
    private fun extractVeranstaltungsNr(cell: Element): String? {
        val link = cell.select("a[href*=publishSubDir=veranstaltung]").firstOrNull()
            ?: cell.select("a[href*=publishid]").firstOrNull()
            ?: return null
        val text = cleanWhitespace(link.text())
        return VERANSTALTUNGS_NR_REGEX.find(text)?.groupValues?.get(1)
    }

    private fun parseStatus(raw: String): GradeStatus = when {
        raw.equals("bestanden", ignoreCase = true) -> GradeStatus.PASSED
        raw.equals("nicht bestanden", ignoreCase = true) -> GradeStatus.FAILED
        raw.equals("angemeldet", ignoreCase = true) -> GradeStatus.REGISTERED
        // Konto-Status wie "Prüfung vorhanden" tauchen in Leistungszeilen nicht auf;
        // unbekannt → REGISTERED (harmlosester Default).
        else -> GradeStatus.REGISTERED
    }

    /** "2,7" → 2.7; "" / " " → null. Auch Punkt-Dezimal tolerant. */
    private fun parseNoteOrNull(raw: String): Double? {
        val t = raw.trim()
        if (t.isBlank()) return null
        // Nur echte Noten-Muster (Ziffer, Komma/Punkt, Ziffer) — kein Integer-Missbrauch.
        if (!NOTE_REGEX.matches(t)) return null
        return t.replace(',', '.').toDoubleOrNull()
    }

    private fun parseDateOrNull(raw: String): Long? {
        val t = raw.trim()
        if (t.isBlank()) return null
        return runCatching { LocalDate.parse(t, DATE_FORMATTER).toEpochDay() }.getOrNull()
    }

    private fun cleanWhitespace(s: String): String =
        s.replace(" ", " ").replace(Regex("\\s+"), " ").trim()

    companion object {
        private const val KONTO_GPA = "8997"
        private const val KONTO_TOTAL_LP = "8999"

        /**
         * Stabile Prüfungs-ID aus dem Info-Link. Matcht beide Formen:
         *  - encoded:  `pruefung%3Alabnr%3D2438258`
         *  - decoded:  `pruefung:labnr=2438258`
         */
        private val LABNR_REGEX = Regex("pruefung(?:%3A|:)labnr(?:%3D|=)(\\d+)")
        /** Führende Veranstaltungs-Nr (4–5-stellig) am Anfang des Veranstaltungslink-Texts. */
        private val VERANSTALTUNGS_NR_REGEX = Regex("^(\\d{3,6})\\b")
        private val NOTE_REGEX = Regex("\\d+[.,]\\d+")
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }
}

/** Rohe Konto-Zeile (nur intern im Scraper genutzt). */
private data class ParsedKonto(
    val nr: String?,
    val name: String,
    val noteValue: Double?,
    val bonusValue: Int?
)

/** Ergebnis eines Notenspiegel-Parses: Leistungen + optionale Kopf-Summen. */
data class NotenspiegelResult(
    val grades: List<ParsedGrade>,
    val summary: ParsedSummary?
)

/** Kopf-Summen aus den Spezial-Konten 8997 (GPA/gewichtete LP) und 8999 (Gesamt-LP). */
data class ParsedSummary(
    val gpa: Double?,
    val weightedLp: Int?,
    val totalLp: Int?
)

/**
 * Reine Parser-Ausgabe einer Leistungszeile; das [GradesRepository] macht daraus
 * eine [GradeEntity] inkl. berechnetem Merge-Key.
 */
data class ParsedGrade(
    val labnr: Long?,
    val pruefungsNr: String,
    val titel: String,
    /** Führende Veranstaltungs-Nr aus dem Veranstaltungslink (z.B. "3202"). Null wenn keine. */
    val veranstaltungsNr: String?,
    val kontoNr: String?,
    val kontoName: String?,
    val semester: String,
    val note: Double?,
    val status: GradeStatus,
    val bonusLp: Int,
    val vermerk: String,
    val versuch: Int,
    val pruefungsDatum: Long?
) {
    /** Merge-Key: labnr bevorzugt, sonst pruefungsNr+versuch. */
    val mergeKey: String get() = GradeEntity.mergeKeyFor(labnr, pruefungsNr, versuch)
}
