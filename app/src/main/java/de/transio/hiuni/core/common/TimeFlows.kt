package de.transio.hiuni.core.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * System-Uhr, die die Zeitzone bei jedem Zugriff frisch vom System holt.
 * `Clock.systemDefaultZone()` friert die Zone beim Erzeugen ein, damit würde ein
 * Zeitzonenwechsel (Reise, manuelle Umstellung) bei laufender App übersehen.
 */
object SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
    override fun instant(): Instant = Instant.now()
    override fun millis(): Long = System.currentTimeMillis()
}

/**
 * Liefert das heutige Datum (in der aktuellen Zone der [clock]) sofort beim Sammeln
 * und erneut direkt nach jeder lokalen Mitternacht. Gedacht als Schlüssel für
 * "heute"-abhängige Abfragen per `flatMapLatest`, damit sie bei Tageswechsel neu laufen,
 * statt das Datum einmalig beim Erzeugen eines ViewModels einzufrieren.
 *
 * Kein Polling im Sekundentakt: der Flow schläft bis zur nächsten Mitternacht, aber
 * höchstens [recheckInterval] am Stück. Der Deckel fängt Fälle ab, in denen ein langer
 * `delay` nicht zur Wanduhr passt (Zeitzonenwechsel, manuell verstellte Uhrzeit, Deep
 * Sleep). Gleiche Daten werden per `distinctUntilChanged` nicht doppelt gemeldet.
 * Mitternacht wird über `atStartOfDay(zone)` bestimmt, also auch an Tagen mit 23 oder
 * 25 Stunden (Sommerzeit) korrekt.
 */
fun currentDateFlow(
    clock: Clock = SystemZoneClock,
    recheckInterval: Duration = 15.minutes
): Flow<LocalDate> = flow {
    while (true) {
        val now = clock.instant()
        val zone = clock.zone
        val today = now.atZone(zone).toLocalDate()
        emit(today)
        val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant()
        delay(minOf(millisUntil(now, nextMidnight), recheckInterval.inWholeMilliseconds))
    }
}.distinctUntilChanged()

/**
 * Tickt sofort beim Sammeln und danach zu jeder vollen Minute mit dem aktuellen
 * Zeitpunkt der [clock]. Für Anzeigen, die an der Uhrzeit hängen ("nächster Termin",
 * "Mensa geöffnet"), ohne die zugrunde liegenden Datenbankabfragen jede Minute neu
 * zu starten: die Abfrage hängt am Datum, der Ticker filtert nur im Speicher nach.
 */
fun minuteTickerFlow(clock: Clock = SystemZoneClock): Flow<Instant> = flow {
    while (true) {
        val now = clock.instant()
        emit(now)
        val nextMinute = now.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES)
        delay(millisUntil(now, nextMinute))
    }
}

/** Millisekunden bis [target], aufgerundet und mindestens 1, damit die Schleife nie leerläuft. */
private fun millisUntil(now: Instant, target: Instant): Long {
    val nanos = java.time.Duration.between(now, target).toNanos()
    return ((nanos + 999_999) / 1_000_000).coerceAtLeast(1)
}
