package de.transio.hiuni.feature.learnweb.data

import org.jsoup.Jsoup
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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
     * Parsed alle Assignment-Deadlines (`mod_assign`, eventtype `due`) aus dem
     * Calendar-Block. Funktioniert identisch fürs Dashboard-Markup (eingebetteter
     * Mini-Kalender) als auch fürs `/calendar/view.php?view=upcoming`-Markup —
     * Moodle nutzt dasselbe `<td.hasevent> > li[data-event-component]`-Schema.
     *
     * Heuristik für die exakte Uhrzeit: `data-day-timestamp` ist nur Tag-Mitternacht
     * (lokale TZ). Wenn der `<a title>` einen Zeitstempel wie „23:59 Uhr" enthält,
     * nehmen wir den; sonst Default 23:59 (Moodle-Standard-Deadline-Uhrzeit).
     *
     * Deduplizierung über `eventId` — selbe Event-ID kann mehrfach im DOM auftauchen
     * (z.B. wenn der Dashboard-Kalender im Monatswechsel ein Event sowohl im "diese
     * Woche"-Block als auch im Monatsraster rendert).
     */
    fun parseAssignments(html: String): List<ParsedAssignment> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)

        val items = doc.select(
            "td.hasevent li[data-event-component=mod_assign][data-event-eventtype=due]"
        )
        val byEventId = linkedMapOf<Long, ParsedAssignment>()
        val zone = ZoneId.systemDefault()
        for (li in items) {
            val td = li.closest("td.hasevent") ?: continue
            val dayTs = td.attr("data-day-timestamp").trim().toLongOrNull() ?: continue
            val anchor = li.selectFirst("a[data-event-id]") ?: continue
            val eventId = anchor.attr("data-event-id").trim().toLongOrNull() ?: continue
            val title = anchor.attr("title").trim().ifBlank {
                anchor.selectFirst(".eventname")?.text()?.trim().orEmpty()
            }
            if (title.isBlank()) continue
            val url = anchor.attr("href").trim().ifBlank { anchor.attr("abs:href").trim() }

            val time = extractTimeFromTitle(title) ?: LocalTime.of(23, 59)
            // dayTs ist UTC-Sekunden für Tag-Mitternacht in der LSF-Zeitzone (Berlin/lokal).
            // Wir interpretieren das als Datum in der System-Zeitzone, dann hängen wir die
            // extrahierte Uhrzeit dran — das matched, was der User im Calendar sieht.
            val day = java.time.Instant.ofEpochSecond(dayTs).atZone(zone).toLocalDate()
            val dueMillis = day.atTime(time).atZone(zone).toInstant().toEpochMilli()

            val parsed = ParsedAssignment(
                eventId = eventId,
                title = title,
                dueEpochMillis = dueMillis,
                url = url,
                rawComponent = "mod_assign"
            )
            // putIfAbsent erhält die erste gesehene Variante (deduplikat-stabil).
            if (!byEventId.containsKey(eventId)) {
                byEventId[eventId] = parsed
            }
        }
        Timber.d("LearnwebScraper: parseAssignments rohe-li=${items.size} eindeutig=${byEventId.size}")
        return byEventId.values.toList()
    }

    /**
     * Extrahiert die erste HH:MM-Uhrzeit aus dem Title (typisch:
     * „Abgabe der Bonusaufgabe [bis 19.06.2026, 23:59 Uhr] ist fällig."). Liefert
     * `null` wenn nichts matched (Default-Behandlung übernimmt der Caller).
     */
    private fun extractTimeFromTitle(title: String): LocalTime? {
        val match = TIME_REGEX.find(title) ?: return null
        val h = match.groupValues[1].toIntOrNull() ?: return null
        val m = match.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return LocalTime.of(h, m)
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

    companion object {
        // „23:59 Uhr" oder einfach „23:59" — wir nehmen den ersten Treffer.
        private val TIME_REGEX = Regex("""(\d{1,2}):(\d{2})""")
    }
}

/**
 * Roh-Repräsentation einer Assignment-Deadline aus dem Moodle-Calendar.
 *
 * - `eventId` = Moodle-Calendar-Event-ID (eindeutig pro Sync-Lauf)
 * - `dueEpochMillis` = Abgabetermin in Millis seit Epoch (lokale Berlin-Zeit interpretiert)
 * - `url` = direkter Assignment-Link für Browser-Open
 * - `rawComponent` = Moodle-Komponente (aktuell nur `mod_assign`; künftig evtl.
 *   `mod_quiz`/`mod_forum` wenn wir Quiz-Deadlines mit dazu nehmen)
 */
data class ParsedAssignment(
    val eventId: Long,
    val title: String,
    val dueEpochMillis: Long,
    val url: String,
    val rawComponent: String
)

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
