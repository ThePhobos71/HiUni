package de.transio.hiuni.feature.mensa.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * STW closure notices encode the affected period inside the [Announcement.text].
 * The API itself only attaches the announcement to a single anchor date, so we parse the
 * text ("Vom 25.05.2026 bis 29.05.2026", "Am 25.05.2026", "vom 25.05. bis 29.05.") and
 * compute the actual coverage window. Falls back to the anchor date when nothing parseable.
 */
internal object AnnouncementCoverage {

    // Word boundaries (\b) don't work after literal dots, so we rely on the date shape itself.
    private val FULL_RANGE = Regex(
        """(?i)(\d{1,2}\.\d{1,2}\.\d{4})\s*(?:bis|–|-|—|‐)\s*(\d{1,2}\.\d{1,2}\.\d{4})"""
    )
    private val PARTIAL_RANGE = Regex(
        """(?i)(\d{1,2}\.\d{1,2}\.)(?!\d)\s*(?:bis|–|-|—|‐)\s*(\d{1,2}\.\d{1,2}\.)(?!\d)"""
    )
    private val SINGLE_FULL = Regex("""(\d{1,2}\.\d{1,2}\.\d{4})""")
    private val SINGLE_PARTIAL = Regex("""(\d{1,2}\.\d{1,2}\.)(?!\d)""")

    private val FULL_FMT = DateTimeFormatter.ofPattern("d.M.yyyy")
    private val PARTIAL_FMT = DateTimeFormatter.ofPattern("d.M.")

    fun coverage(announcement: Announcement): Pair<LocalDate, LocalDate> {
        FULL_RANGE.find(announcement.text)?.let { m ->
            val from = parseFull(m.groupValues[1])
            val to = parseFull(m.groupValues[2])
            if (from != null && to != null && !to.isBefore(from)) return from to to
        }
        PARTIAL_RANGE.find(announcement.text)?.let { m ->
            val year = announcement.date.year
            val from = parsePartial(m.groupValues[1], year)
            val to = parsePartial(m.groupValues[2], year)
            if (from != null && to != null && !to.isBefore(from)) return from to to
        }
        SINGLE_FULL.find(announcement.text)?.let { m ->
            parseFull(m.groupValues[1])?.let { return it to it }
        }
        SINGLE_PARTIAL.find(announcement.text)?.let { m ->
            parsePartial(m.groupValues[1], announcement.date.year)?.let { return it to it }
        }
        return announcement.date to announcement.date
    }

    fun covers(announcement: Announcement, date: LocalDate): Boolean {
        val (from, to) = coverage(announcement)
        return !date.isBefore(from) && !date.isAfter(to)
    }

    private fun parseFull(text: String): LocalDate? =
        runCatching { LocalDate.parse(text, FULL_FMT) }.getOrNull()

    private fun parsePartial(text: String, year: Int): LocalDate? = runCatching {
        val partial = "${text}$year"
        LocalDate.parse(partial, DateTimeFormatter.ofPattern("d.M.yyyy"))
    }.getOrNull()
}

internal fun Announcement.covers(date: LocalDate): Boolean =
    AnnouncementCoverage.covers(this, date)
