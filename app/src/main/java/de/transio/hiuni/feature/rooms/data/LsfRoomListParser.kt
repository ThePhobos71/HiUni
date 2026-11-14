package de.transio.hiuni.feature.rooms.data

import org.jsoup.Jsoup

/**
 * Liest die öffentliche LSF-Raumauswahl ("Bitte wählen Sie einen Raum ...").
 *
 * Beobachtetes Markup (2026-11-14): je Raum ein
 * `<a class="regular" href="...&raum.rgid=2&...">SC.A.0.09 (Großer Seminarraum) -</a>`.
 * Der Campus steckt im Präfix vor dem ersten Punkt. Ausreißer wie
 * `gesperrt/HC.A.-1.09 (...)`, `Online-Lehre (...)`, `-- (keine Raumbuchung ...)`
 * oder `C 003 (...)` haben kein bekanntes Präfix und fallen heraus.
 *
 * Geliefert werden nur Lernräume (siehe [isStudyRoom]).
 */
object LsfRoomListParser {

    private val RGID = Regex("""raum\.rgid=(\d+)""")

    /** Wörter, von denen eines in Raumname oder Raumart vorkommen muss. */
    private val INCLUDE = listOf("seminarraum", "hörsaal", "arbeitsraum")

    /**
     * Wörter, die einen Raum trotzdem ausschließen: gesperrte Räume, Fachräume mit
     * Labortischen, Sprach- und Medienlabore, der Freiluftseminarraum.
     */
    private val EXCLUDE = listOf("gesperrt", "labor", "freiluft")

    fun parse(html: String): List<LsfRoom> {
        val doc = Jsoup.parse(html)
        val seen = mutableSetOf<Int>()
        return doc.select("a[href*=raum.rgid=]").mapNotNull { a ->
            val rgid = RGID.find(a.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val label = cleanLabel(a.text())
            if (label.isEmpty() || !isStudyRoom(label)) return@mapNotNull null
            val room = toRoom(rgid, label) ?: return@mapNotNull null
            if (seen.add(rgid)) room else null
        }
    }

    /** Lernraum im Sinne des Raumfinders: Seminarraum, Hörsaal oder Arbeitsraum. */
    fun isStudyRoom(label: String): Boolean {
        val lower = label.lowercase()
        return INCLUDE.any { it in lower } && EXCLUDE.none { it in lower }
    }

    /** `SC.A.0.09 (Großer Seminarraum) -` wird zu `SC.A.0.09 (Großer Seminarraum)`. */
    private fun cleanLabel(raw: String): String =
        raw.replace(' ', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .removeSuffix("-")
            .trim()

    private fun toRoom(rgid: Int, label: String): LsfRoom? {
        val open = label.indexOf(" (")
        val close = label.lastIndexOf(')')
        val (code, type) = if (open > 0 && close > open) {
            label.substring(0, open).trim() to label.substring(open + 2, close).trim()
        } else {
            // Ohne Klammer, zum Beispiel "HC.E.0.Hörsaal 3": Raumart aus dem Namen.
            label to if ("hörsaal" in label.lowercase()) "Hörsaal" else ""
        }
        val campus = Campus.fromPrefix(code.substringBefore('.')) ?: return null
        return LsfRoom(rgid = rgid, code = code, type = type, campus = campus)
    }
}
