package de.transio.hiuni.feature.movies

import de.transio.hiuni.core.common.DateTimeFormats
import java.time.LocalDate
import java.time.LocalTime

/**
 * Text für „Film teilen" im Film-Detail, z.B.:
 * „Hey, im Uni-Kino läuft „Sweeney Todd". Wollen wir da hin? Ist am Donnerstag,
 * 30. Oktober um 20:30 Uhr."
 *
 * Fehlt Datum oder Uhrzeit, entfällt der jeweilige Teil. Überraschungsfilme
 * nennen keinen Titel (der ist vorab ja nicht bekannt).
 */
internal fun movieShareText(
    title: String,
    isSurprise: Boolean,
    date: LocalDate?,
    time: LocalTime?
): String {
    val what = if (isSurprise) "ein Überraschungsfilm" else "„$title“"
    val whenPart = when {
        date != null && time != null ->
            " Ist am ${date.format(DateTimeFormats.dayFull)} um ${time.format(DateTimeFormats.time24)} Uhr."
        date != null -> " Ist am ${date.format(DateTimeFormats.dayFull)}."
        time != null -> " Ist um ${time.format(DateTimeFormats.time24)} Uhr."
        else -> ""
    }
    return "Hey, im Uni-Kino läuft $what. Wollen wir da hin?$whenPart"
}
