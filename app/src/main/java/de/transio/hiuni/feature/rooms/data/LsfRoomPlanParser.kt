package de.transio.hiuni.feature.rooms.data

import org.jsoup.Jsoup

/**
 * Liest aus dem Wochenplan eines Raums (`state=wplan&act=Raum&show=plan`) nur den
 * iCal-Export-Link. Die Belegung selbst kommt aus dem iCal ([RoomIcsParser]), weil
 * das HTML-Raster aus rowspan-Zellen besteht und Ausfälle dort nicht verlässlich
 * erkennbar sind.
 *
 * Beobachtet (2026-11-14): Der Link trägt die Termin-IDs der angezeigten Woche,
 * `...&termine=329009,325386,...&moduleCall=iCalendarPlan&...`. Hat der Raum in der
 * Woche keine Termine, fehlt der Link, das Raster (`plan_rahmen`) ist aber da. Eine
 * unbekannte Raum-ID liefert eine Seite ohne Raster.
 */
object LsfRoomPlanParser {

    sealed interface Result {
        /** Termine vorhanden, Export unter [url]. */
        data class Ical(val url: String) : Result

        /** Gültiger Plan ohne Termine in dieser Woche. */
        data object NoEvents : Result

        /** Kein Plan erkennbar (Fehlerseite, Wartung, geänderte Struktur). */
        data object Invalid : Result
    }

    fun parse(html: String, baseUri: String = LSF_BASE): Result {
        val doc = Jsoup.parse(html, baseUri)
        val url = doc.select("a[href*=moduleCall=iCalendarPlan]")
            .firstOrNull { it.attr("href").contains("termine=") }
            ?.absUrl("href")
            ?.takeIf { it.isNotBlank() }
        if (url != null) return Result.Ical(url)
        return if (doc.selectFirst(".plan_rahmen") != null) Result.NoEvents else Result.Invalid
    }

    private const val LSF_BASE = "https://lsf.uni-hildesheim.de/qisserver/"
}
