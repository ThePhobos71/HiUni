package de.transio.hiuni.feature.home

import de.transio.hiuni.core.common.DateTimeUtils
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

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
    CustomEventEntity.SOURCE_STUDY_PLAN -> "NÄCHSTE LERNEINHEIT"
    CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT,
    CustomEventEntity.SOURCE_LEARNWEB_ICAL -> "NÄCHSTE ABGABE"
    else -> "NÄCHSTER TERMIN"
}

/**
 * Meta-Zeile unter dem nächsten Termin im Home-Hero („In 12 Min · 09:30 · F101").
 * [now] kommt aus dem Minutentakt des HomeViewModels, damit der Countdown live
 * herunterzählt; auch der relative Tag („Morgen") bezieht sich auf [now].
 */
internal fun nextEventMeta(
    event: CustomEventEntity?,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    if (event == null) return "Lege im Kalender deinen ersten Termin an"
    val minutesUntil = Duration.between(now, event.startTime).toMinutes()
    val relative = when {
        minutesUntil < 0L -> "läuft"
        minutesUntil < 60L -> "In $minutesUntil Min"
        minutesUntil < 24L * 60 -> "In ${minutesUntil / 60} Std"
        else -> DateTimeUtils.formatRelativeDay(
            event.startTime,
            zone,
            today = now.atZone(zone).toLocalDate()
        )
    }
    val time = DateTimeUtils.formatTime(event.startTime, zone)
    return buildString {
        append(relative)
        append(" · ")
        append(time)
        event.location?.takeIf { it.isNotBlank() }?.let {
            append(" · ")
            append(it)
        }
    }
}
