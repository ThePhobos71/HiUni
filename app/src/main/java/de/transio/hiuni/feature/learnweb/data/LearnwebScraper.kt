package de.transio.hiuni.feature.learnweb.data

import org.jsoup.Jsoup
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTML-Scraper für die Learnweb-Dashboard-Seite. Extrahiert die eingeschriebenen
 * Kurse aus zwei Quellen, die Moodle parallel rendert:
 *
 * - **Calendar-Course-Filter Select** (`<select id="calendar-course-filter-…">`):
 *   sehr kompakte Quelle, jede `<option>` = ein Kurs. Inner-Text kann gekürzt
 *   sein („…"-Suffix). Special-Eintrag `value="1"` = „Alle Kurse" wird
 *   verworfen.
 * - **Navigation-Tree** (`<li class="type_course" data-node-key="…">`):
 *   redundant zum Select, hat aber `<a title>` mit dem vollständigen Namen und
 *   einen direkten Course-Link. Wir nutzen das für Title-Augmentation, falls
 *   der Select-Text gekürzt war.
 *
 * Falls die Tree-Quelle fehlt (Layout-Variation), reicht das Select alleine.
 */
@Singleton
class LearnwebScraper @Inject constructor() {

    fun parseCourses(html: String): List<ParsedCourse> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)

        // Quelle A: Course-Filter-Select. Mehrere Kalender-Blocks auf einer Seite
        // möglich (verschiedene Block-Instanzen), aber alle teilen dieselbe
        // Course-Liste — wir nehmen das erste, das wir finden.
        val selectOptions = doc.select("select[id^=calendar-course-filter] option")
        val fromSelect = selectOptions.mapNotNull { opt ->
            val id = opt.attr("value").trim().toLongOrNull() ?: return@mapNotNull null
            if (id == 1L) return@mapNotNull null // "Alle Kurse"
            val text = opt.text().trim()
            if (text.isBlank()) return@mapNotNull null
            ParsedCourse(courseId = id, name = text)
        }.distinctBy { it.courseId }

        // Quelle B: Navigation-Tree. Wir indexieren title + href pro courseId,
        // damit wir Select-Einträge augmentieren oder fehlende Kurse ergänzen
        // können.
        val treeNodes = doc.select("li.type_course[data-node-key]")
        val treeInfos = treeNodes.mapNotNull { node ->
            val id = node.attr("data-node-key").trim().toLongOrNull() ?: return@mapNotNull null
            val anchor = node.selectFirst("a[title]") ?: node.selectFirst("a")
            val title = anchor?.attr("title")?.trim().orEmpty()
            val href = anchor?.attr("href")?.trim().orEmpty()
            id to TreeInfo(title = title.takeIf { it.isNotBlank() }, href = href.takeIf { it.isNotBlank() })
        }.toMap()

        val merged = mutableMapOf<Long, ParsedCourse>()
        for (entry in fromSelect) {
            val info = treeInfos[entry.courseId]
            val bestName = pickBestName(entry.name, info?.title)
            merged[entry.courseId] = entry.copy(name = bestName, treeHref = info?.href)
        }
        // Falls der Select-Block fehlt, aber der Tree da ist (passiert auf
        // Studierenden-Dashboards mit minimalem Layout) — Tree-Only-Kurse
        // zusätzlich übernehmen.
        for ((id, info) in treeInfos) {
            if (merged.containsKey(id)) continue
            val name = info.title?.takeIf { it.isNotBlank() } ?: continue
            merged[id] = ParsedCourse(courseId = id, name = name, treeHref = info.href)
        }

        val result = merged.values.toList()
        Timber.d(
            "LearnwebScraper: select=${fromSelect.size} tree=${treeInfos.size} merged=${result.size}"
        )
        return result
    }

    /**
     * Wenn der Tree einen längeren Titel ohne „…"-Suffix liefert UND der
     * Select-Text gekürzt wirkt, nimm Tree-Title. Sonst Select.
     */
    private fun pickBestName(selectName: String, treeTitle: String?): String {
        if (treeTitle.isNullOrBlank()) return selectName
        val selectLooksTruncated = selectName.endsWith("...") || selectName.endsWith("…")
        return when {
            selectLooksTruncated && treeTitle.length > selectName.length -> treeTitle
            treeTitle.length > selectName.length + 5 -> treeTitle
            else -> selectName
        }
    }

    private data class TreeInfo(val title: String?, val href: String?)
}

/**
 * Roh-Repräsentation eines Learnweb-Kurses nach dem Parsen. Die Repository-
 * Schicht konvertiert das in [LearnwebCourse] und ergänzt `url` falls
 * `treeHref` null war.
 */
data class ParsedCourse(
    val courseId: Long,
    val name: String,
    val treeHref: String? = null
)
