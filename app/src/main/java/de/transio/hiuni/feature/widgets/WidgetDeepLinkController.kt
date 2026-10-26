package de.transio.hiuni.feature.widgets

import android.content.Intent
import de.transio.hiuni.core.common.OneShotSignal
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Empfängt Deep-Link-Signale aus den Home-Screen-Widgets ([TodoWidget],
 * [StundenplanWidget]) und leitet sie an den `AppNavGraph` weiter.
 *
 * Analog zu `NotificationDeepLinkController`: die Widgets senden Intents an
 * MainActivity mit `action` = ACTION_OPEN_TODOS / ACTION_OPEN_CALENDAR.
 * MainActivity ruft [handleIntent] auf; wir emittieren ein Signal, das
 * AppNavGraph über eine ViewModel-Bridge konsumiert und in `navController
 * .navigate(...)` verwandelt.
 *
 * V1: nur Ziel-Tab. Sub-Deeplinks (z.B. Todo-Detail nach ID oder AddSheet-
 * Auto-Open) sind Follow-Ups — der Widget-Tap öffnet die App im richtigen
 * Tab, User tappt dann selbst weiter. Kein automagisches Sheet.
 */
@Singleton
class WidgetDeepLinkController @Inject constructor() {

    // OneShotSignal statt SharedFlow: beim Kaltstart per Widget-Tap hört der
    // NavGraph beim Signalisieren noch nicht zu, siehe [OneShotSignal].
    private val todos = OneShotSignal()
    val openTodos: Flow<Unit> = todos.events

    private val calendar = OneShotSignal()
    val openCalendar: Flow<Unit> = calendar.events

    private val mensa = OneShotSignal()
    val openMensa: Flow<Unit> = mensa.events

    private val calendarWeek = OneShotSignal()
    val openCalendarWeek: Flow<Unit> = calendarWeek.events

    private val exams = OneShotSignal()
    val openExams: Flow<Unit> = exams.events

    /**
     * Wird von MainActivity in `onCreate`/`onNewIntent` mit dem eingehenden
     * Intent aufgerufen. Verbraucht die Action + Extras direkt (setzt sie
     * NICHT zurück, das muss der Caller machen via `intent.setAction(null)`
     * um Wiederholung bei Config-Change zu vermeiden — siehe MainActivity).
     */
    fun handleIntent(intent: Intent): Boolean = when (intent.action) {
        ACTION_OPEN_TODOS -> {
            Timber.i("WidgetDeepLink: OPEN_TODOS")
            todos.fire()
            true
        }
        ACTION_OPEN_CALENDAR -> {
            Timber.i("WidgetDeepLink: OPEN_CALENDAR")
            calendar.fire()
            true
        }
        ACTION_OPEN_MENSA -> {
            Timber.i("WidgetDeepLink: OPEN_MENSA")
            mensa.fire()
            true
        }
        ACTION_OPEN_CALENDAR_WEEK -> {
            Timber.i("WidgetDeepLink: OPEN_CALENDAR_WEEK")
            calendarWeek.fire()
            true
        }
        ACTION_OPEN_EXAMS -> {
            Timber.i("WidgetDeepLink: OPEN_EXAMS")
            exams.fire()
            true
        }
        else -> false
    }

    companion object {
        const val ACTION_OPEN_TODOS = "de.transio.hiuni.OPEN_TODOS"
        const val ACTION_OPEN_CALENDAR = "de.transio.hiuni.OPEN_CALENDAR"
        const val ACTION_OPEN_MENSA = "de.transio.hiuni.OPEN_MENSA"
        const val ACTION_OPEN_CALENDAR_WEEK = "de.transio.hiuni.OPEN_CALENDAR_WEEK"
        const val ACTION_OPEN_EXAMS = "de.transio.hiuni.OPEN_EXAMS"
    }
}
