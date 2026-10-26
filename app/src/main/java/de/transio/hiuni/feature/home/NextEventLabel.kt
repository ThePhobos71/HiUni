package de.transio.hiuni.feature.home

import de.transio.hiuni.feature.calendar.data.CustomEventEntity

/**
 * Überschrift über dem nächsten Termin im Home-Hero. Der Hero zeigt den nächsten
 * Kalendereintrag jeder Quelle, nicht nur Vorlesungen. Ein festes „Nächste
 * Vorlesung" machte aus Sport-, Film- und Raum-Pins scheinbare Vorlesungen (daran
 * war auch der Mensa-Pin gescheitert), daher richtet sich das Label nach der Quelle.
 */
internal fun nextEventLabel(event: CustomEventEntity?): String = when (event?.sourceKind) {
    CustomEventEntity.SOURCE_LSF_STUNDENPLAN -> "NÄCHSTE VORLESUNG"
    CustomEventEntity.SOURCE_MOVIE_PIN -> "NÄCHSTER FILM"
    CustomEventEntity.SOURCE_SPORT_PIN -> "NÄCHSTER SPORTKURS"
    CustomEventEntity.SOURCE_BIB_BOOKING -> "NÄCHSTE RAUMBUCHUNG"
    CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT,
    CustomEventEntity.SOURCE_LEARNWEB_ICAL -> "NÄCHSTE ABGABE"
    else -> "NÄCHSTER TERMIN"
}
