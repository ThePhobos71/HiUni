package de.transio.hiuni.core.network

/**
 * Zentrale, sprechende Offline-Fehlermeldungen. Ersetzt in den Refresh-Pfaden die
 * technische „Server nicht erreichbar"-Meldung, sobald der [ConnectivityObserver]
 * offline meldet — der User soll wissen, dass gecachte Daten angezeigt werden.
 */
object OfflineMessages {
    const val NO_CONNECTION = "Keine Verbindung – gespeicherte Daten werden angezeigt"
}
