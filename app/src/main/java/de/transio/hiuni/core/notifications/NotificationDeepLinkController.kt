package de.transio.hiuni.core.notifications

import de.transio.hiuni.core.common.OneShotSignal
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge zwischen MainActivity (parsed Notification-Tap-Intents) und
 * AppNavGraph (navigiert auf den passenden Screen). Singleton, weil beide
 * Seiten denselben Strom sehen müssen.
 *
 * Die Signale sind [OneShotSignal]s: beim Kaltstart signalisiert MainActivity,
 * bevor der NavGraph zuhört, das Signal wartet dann, bis er es abholt.
 */
@Singleton
class NotificationDeepLinkController @Inject constructor() {

    private val center = OneShotSignal()
    private val todos = OneShotSignal()
    private val grades = OneShotSignal()

    /** Tap auf eine allgemeine Meldung → Push-Center. */
    val openCenter: Flow<Unit> = center.events

    /** Tap auf eine Aufgaben-Erinnerung → Aufgaben-Screen. */
    val openTodos: Flow<Unit> = todos.events

    /** Tap auf eine Neue-Note-Meldung → Noten-Screen. */
    val openGrades: Flow<Unit> = grades.events

    fun signalOpenCenter() = center.fire()

    fun signalOpenTodos() = todos.fire()

    fun signalOpenGrades() = grades.fire()
}
