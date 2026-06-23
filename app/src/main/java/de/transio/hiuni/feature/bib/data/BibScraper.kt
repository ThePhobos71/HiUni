package de.transio.hiuni.feature.bib.data

import org.jsoup.Jsoup
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parsed das Belegungs-Grid aus `index.php`. Format pro Zelle:
 *   `<td id="cell-YYYYMMDD-HHMM-ROOMID" style="background-color: #92CD00" …>`
 *   `style="background-color: #92CD00"` = frei (`#92CD00` grün)
 *   `style="background-color: #DF2E3B"` = belegt (rot), oft mit `rowspan="N"` für N halbe Stunden
 *   Zellen ohne id mit Hintergrund `#e8e3e3` und Title `geschlossen` = geschlossen
 *
 * Die Index-Page rendert 28 Tage × 4 Räume × 24 Halbe-Stunden-Slots. Wir
 * brauchen die genaue UID jeder Zelle nur, um sie zur Tabelle zu mappen —
 * Slot-Start/Ende stehen direkt im Title-Attribut UND in der ID. Wir lesen
 * sie aus der ID, weil das stabiler ist als Title-Parsing.
 */
@Singleton
class BibScraper @Inject constructor() {

    /**
     * Extrahiert alle Slots aus dem Roh-HTML. Liefert pro Raum+Datum eine
     * sortierte Slot-Liste. Closed-Zellen werden mit synthetischen Slots
     * eingefügt — der Frontend braucht sie nur für die Auslastungsstatistik
     * nicht, aber sie helfen für die spätere Detail-Ansicht.
     */
    fun parseAvailability(html: String): Map<Pair<LocalDate, Int>, RoomDayAvailability> {
        val doc = Jsoup.parse(html)
        val cells = doc.select("td[id^=cell-]")
        val ownMarkers = cells.count { "#999999" in it.attr("style").lowercase() }
        val takenMarkers = cells.count { "#df2e3b" in it.attr("style").lowercase() }
        Timber.d("BibScraper: ${cells.size} Belegungs-Zellen gefunden (own=$ownMarkers, taken=$takenMarkers)")

        val grouped = mutableMapOf<Pair<LocalDate, Int>, MutableList<SlotEntry>>()
        for (cell in cells) {
            val id = cell.id() // cell-YYYYMMDD-HHMM-ROOM
            val parts = id.removePrefix("cell-").split('-')
            if (parts.size < 3) continue
            val date = parseDate(parts[0]) ?: continue
            val startMinute = parts[1].toIntOrNull() ?: continue
            val roomId = parts[2].toIntOrNull() ?: continue
            val start = militaryToTime(startMinute) ?: continue

            val style = cell.attr("style").lowercase()
            val onclick = cell.attr("onclick")
            val status = when {
                "#df2e3b" in style -> SlotStatus.BOOKED
                "#999999" in style -> SlotStatus.OWN_BOOKING
                "#92cd00" in style -> SlotStatus.FREE
                "#e8e3e3" in style -> SlotStatus.CLOSED
                // Unbekannte Farbe → defensiv CLOSED, statt einen Slot als
                // FREE zu markieren der eigentlich gar nicht buchbar ist.
                else -> SlotStatus.CLOSED
            }
            // Bookbar ist nur eine FREE-Zelle, die das Backend auch klickbar
            // gemacht hat — vergangene Slots am heutigen Tag haben keinen
            // getBookingForm-Handler mehr.
            val bookable = status == SlotStatus.FREE && "getBookingForm" in onclick
            val rowspan = cell.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
            for (i in 0 until rowspan) {
                val slotStart = start.plusMinutes((i * 30).toLong())
                val slotEnd = slotStart.plusMinutes(30)
                if (slotEnd.isBefore(slotStart)) break
                grouped.getOrPut(date to roomId) { mutableListOf() }
                    .add(SlotEntry(slotStart, slotEnd, status, bookable))
            }
        }

        return grouped
            .mapValues { (key, slots) ->
                val sorted = slots
                    .distinctBy { it.startTime }
                    .sortedBy { it.startTime }
                RoomDayAvailability(date = key.first, roomId = key.second, slots = sorted)
            }
    }

    /**
     * Parsed eigene Buchungen aus der HTML-Liste die `bookings.php` zurückgibt.
     * Format ist eine `<ul>`-Liste pro Eintrag — Beispiele finden sich in der
     * `js/functions.js` (`updateBookingsList`). Wir suchen nach `<li>`-Items
     * mit data-Attributen oder per Regex auf typischen Stringformaten.
     *
     * Fallback ist robust: liefert leere Liste wenn das Format unbekannt ist.
     */
    fun parseMyBookings(html: String): List<MyBooking> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse("<div>$html</div>")
        val bookings = mutableListOf<MyBooking>()
        // Heuristik: jeder Cancel-Link enthält die Buchungs-Koordinaten.
        // `<a href="javascript: deleteBookingFromList(20260526, 1600, 103)">` etc.
        val anchors = doc.select("a[href*=deleteBookingFromList], a[onclick*=deleteBookingFromList]")
        val pattern = Regex("""deleteBookingFromList\((\d{8})\s*,\s*(\d{3,4})\s*,\s*(\d{2,4})\s*(?:,\s*(\d{3,4}))?\)""")
        for (a in anchors) {
            val raw = (a.attr("href") + " " + a.attr("onclick"))
            val match = pattern.find(raw) ?: continue
            val date = parseDate(match.groupValues[1]) ?: continue
            val start = militaryToTime(match.groupValues[2].toInt()) ?: continue
            val roomId = match.groupValues[3].toInt()
            val endRaw = match.groupValues.getOrNull(4)?.toIntOrNull()
            val end = endRaw?.let { militaryToTime(it) } ?: start.plusMinutes(30)
            val roomLabel = BibConfig.ROOM_META[roomId]?.label ?: "F$roomId"
            bookings += MyBooking(
                id = "${match.groupValues[1]}-${match.groupValues[2]}-$roomId",
                date = date,
                startTime = start,
                endTime = end,
                roomId = roomId,
                roomLabel = roomLabel
            )
        }
        if (bookings.isEmpty() && html.contains("Buchung", ignoreCase = true)) {
            Timber.w("BibScraper: bookings.php Format nicht erkannt — bodyExcerpt=${html.take(400)}")
        }
        return bookings.distinctBy { it.id }
    }

    /**
     * Parsed `<option value="HHMM">HH:MM</option>` Snippets aus
     * `get_data.php?action=get_end_times&value=…`. Backend liefert eine reine
     * Optionsliste — wir kapseln sie in ein synthetisches `<select>` damit
     * Jsoup sie korrekt findet.
     */
    fun parseEndTimes(html: String): List<LocalTime> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse("<select>$html</select>")
        return doc.select("option")
            .mapNotNull { opt ->
                val raw = opt.attr("value").trim()
                raw.toIntOrNull()?.let(::militaryToTime)
            }
            .distinct()
            .sorted()
    }

    /** `20260525` → `LocalDate(2026,5,25)`. */
    private fun parseDate(yyyymmdd: String): LocalDate? = runCatching {
        if (yyyymmdd.length != 8) return null
        LocalDate.of(
            yyyymmdd.substring(0, 4).toInt(),
            yyyymmdd.substring(4, 6).toInt(),
            yyyymmdd.substring(6, 8).toInt()
        )
    }.getOrNull()

    /** `800` → 08:00, `1650` → 16:30. Wert ist HHMM ohne Doppelpunkt, Minuten 0 oder 50/30. */
    private fun militaryToTime(value: Int): LocalTime? = runCatching {
        // 800 → hour=8, minute=0; 1650 → hour=16, minute=50 (== 30 im Bib-Schema!)
        // Wichtig: das System nutzt 850 als "8:30" — Suffix '50' bedeutet "halbe Stunde später".
        val hour = value / 100
        val minutePart = value % 100
        val minute = when (minutePart) {
            0 -> 0
            50 -> 30
            else -> minutePart // Defensive: respect raw value
        }
        LocalTime.of(hour, minute)
    }.getOrNull()

    companion object {
        /** Externe Helper, weil das Backend "850" als 8:30 erwartet. */
        fun toMilitary(time: LocalTime): Int = time.hour * 100 + (if (time.minute == 30) 50 else time.minute)

        fun formatDate(date: LocalDate): String = "%04d%02d%02d".format(date.year, date.monthValue, date.dayOfMonth)
    }
}
