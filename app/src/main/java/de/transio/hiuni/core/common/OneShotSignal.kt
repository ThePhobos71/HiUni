package de.transio.hiuni.core.common

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Einmal-Signal für Navigations-Sprünge (Notification-, Widget-, NFC-Tap), das
 * auch dann ankommt, wenn beim Auslösen noch niemand zuhört.
 *
 * Warum kein `MutableSharedFlow(replay = 0, extraBufferCapacity = 1)`: dessen
 * Puffer greift nur bei vorhandenen Abonnenten, ohne Abonnent verwirft
 * `tryEmit` den Wert. Beim Kaltstart signalisiert `MainActivity.onCreate` aber
 * BEVOR der `AppNavGraph` komponiert ist, der Sprung ging also verloren und die
 * App landete auf Home.
 *
 * Ein konflatierter [Channel] hält das letzte Signal, bis es abgeholt wird, und
 * liefert es genau einmal aus: nach Rotation/Recomposition springt die App
 * nicht erneut. Gedacht für genau einen Collector (den NavGraph).
 */
class OneShotSignal {
    private val channel = Channel<Unit>(Channel.CONFLATED)

    val events: Flow<Unit> = channel.receiveAsFlow()

    fun fire() {
        channel.trySend(Unit)
    }
}
