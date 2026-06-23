package de.transio.hiuni.feature.lsf.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser für die Seite "Meine Veranstaltungen" (state=wscheck&wscheck=leistungen).
 *
 * LSF rendert je Semester einen [div.Container_VeranstAus]; aktuelle Veranstaltungen
 * stehen in einem Container mit id="VeranstAus1sem*" (filtert auf "angemeldet/zugelassen"),
 * vergangene Semester in "VeranstAus2sem*". Wir parsen ALLE Container, damit der
 * User im Frontend zwischen Semestern wechseln kann.
 *
 * Pro Veranstaltung:
 *   <h2>Veranstaltung: <a href="…publishid=NNNNN…">NNNN Modulname</a></h2>
 *   <div class="Leistungen_Inhalt">
 *     …Gruppe: 1-Gruppe…
 *     <span class="grueneWarnung">angemeldet</span>   (oder "zugelassen")
 *     <table summary="Termine der Veranstaltung …">…</table>
 *   </div>
 */
@Singleton
class LsfMyCoursesScraper @Inject constructor() {

    fun parse(html: String): LsfMyCoursesPage {
        return runCatching {
            val doc = Jsoup.parse(html)
            val containers = doc.select("div.Container_VeranstAus")
            if (containers.isEmpty()) return@runCatching LsfMyCoursesPage("", emptyList())

            var currentSemester: String? = null
            val allEntries = mutableListOf<LsfCourseEntry>()
            for (container in containers) {
                val semester = container.parent()
                    ?.selectFirst("h2")?.text()
                    ?.substringBefore("(")?.trim()?.removeSuffix(":")?.trim().orEmpty()
                if (semester.isBlank()) continue

                // Erste 1sem*-Container = aktuell laufendes Semester.
                val isCurrent = container.id().contains("VeranstAus1sem")
                if (isCurrent && currentSemester == null) currentSemester = semester

                val anchors = container.select("h2 > a[href*=publishid]")
                val entries = anchors.mapNotNull { anchor -> parseEntry(anchor, semester) }
                allEntries += entries
            }
            // Dedupe pro publishid (ein Modul kann theoretisch in mehreren Containern
            // auftauchen — der erste Treffer behält Vorrang, also typischerweise das
            // aktuelle Semester wenn man die Seite frisch lädt).
            val deduped = allEntries.distinctBy { it.lsfId }
            LsfMyCoursesPage(
                currentSemester = currentSemester ?: deduped.firstOrNull()?.semester.orEmpty(),
                entries = deduped
            )
        }.onFailure { Timber.w(it, "LsfMyCoursesScraper.parse failed") }
            .getOrElse { LsfMyCoursesPage("", emptyList()) }
    }

    private fun parseEntry(anchor: Element, semester: String): LsfCourseEntry? {
        val href = anchor.attr("href")
        val lsfId = PUBLISHID_REGEX.find(href)?.groupValues?.get(1) ?: return null
        val raw = anchor.text().trim()
        val (code, title) = splitCodeAndTitle(raw)

        val h2 = anchor.parent() ?: return null
        val content = findLeistungenInhalt(h2)

        val status = content?.selectFirst("span.grueneWarnung")?.text()?.trim()
        val terminCells = content
            ?.selectFirst("table[summary*=Termine] tr:has(td)")
            ?.select("td")
        val termin = parseTerminRow(terminCells)

        return LsfCourseEntry(
            lsfId = lsfId,
            code = code,
            title = title,
            semester = semester,
            status = status,
            day = termin.day,
            timeStart = termin.timeStart,
            timeEnd = termin.timeEnd,
            rhythm = termin.rhythm,
            room = termin.room,
            lecturer = termin.lecturer
        )
    }

    /**
     * Findet den ersten `<div class="Leistungen_Inhalt">` der diesem `<h2>` folgt,
     * stoppt aber wenn vorher die nächste Veranstaltung-Überschrift kommt.
     */
    private fun findLeistungenInhalt(h2: Element): Element? {
        var node: Element? = h2.nextElementSibling()
        while (node != null) {
            if (node.tagName() == "h2" && node.selectFirst("a[href*=publishid]") != null) return null
            if ("Leistungen_Inhalt" in node.classNames()) return node
            val nested = node.selectFirst("div.Leistungen_Inhalt")
            if (nested != null) return nested
            node = node.nextElementSibling()
        }
        return null
    }

    private fun splitCodeAndTitle(raw: String): Pair<String?, String> {
        val match = LEADING_CODE_REGEX.find(raw)
        return if (match != null) {
            match.groupValues[1] to match.groupValues[2].trim()
        } else null to raw
    }

    private data class TerminInfo(
        val day: String?,
        val timeStart: String?,
        val timeEnd: String?,
        val rhythm: String?,
        val room: String?,
        val lecturer: String?
    )

    private fun parseTerminRow(cells: org.jsoup.select.Elements?): TerminInfo {
        if (cells == null || cells.size < 6) {
            return TerminInfo(null, null, null, null, null, null)
        }
        val day = cells[0].text().trim().takeIf { it.isNotBlank() }
        val (timeStart, timeEnd) = parseTimes(cells[1].text())
        val rhythm = cells[2].text().trim().takeIf { it.isNotBlank() }
        val room = cells[4].selectFirst("a")?.text()?.trim()
            ?: cells[4].text().trim().takeIf { it.isNotBlank() }
        val lecturer = cells[5].selectFirst("a")?.text()?.trim()
            ?: cells[5].text().trim().takeIf { it.isNotBlank() }
        return TerminInfo(day, timeStart, timeEnd, rhythm, room, lecturer)
    }

    private fun parseTimes(raw: String): Pair<String?, String?> {
        val m = TIME_RANGE_REGEX.find(raw) ?: return null to null
        return m.groupValues[1] to m.groupValues[2]
    }

    companion object {
        private val PUBLISHID_REGEX = Regex("publishid=(\\d+)")
        private val LEADING_CODE_REGEX = Regex("^(\\d+)\\s+(.+)$")
        private val TIME_RANGE_REGEX = Regex("(\\d{2}:\\d{2})\\s*bis\\s*(\\d{2}:\\d{2})")
    }
}

/**
 * Ein Eintrag aus der "Meine Veranstaltungen"-Seite. `lsfId` ist die `publishid` und
 * ist über Semester hinweg eindeutig pro konkretem LSF-Termin.
 */
data class LsfCourseEntry(
    val lsfId: String,
    val code: String?,
    val title: String,
    val semester: String,
    val status: String?,
    val day: String?,
    val timeStart: String?,
    val timeEnd: String?,
    val rhythm: String?,
    val room: String?,
    val lecturer: String?
)

data class LsfMyCoursesPage(
    /** Heuristisch ermitteltes "aktuelles" Semester (1sem-Container). Kann leer sein. */
    val currentSemester: String,
    /** Alle Einträge über alle erkannten Semester, dedupiert nach `lsfId`. */
    val entries: List<LsfCourseEntry>
)
