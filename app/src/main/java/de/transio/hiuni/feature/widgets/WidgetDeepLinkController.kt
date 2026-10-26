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
 * Auch App-Shortcuts (res/xml/shortcuts.xml) und die Quick-Settings-Kachel
 * nutzen diese Actions. Todos mit EXTRA_MODE = "new" öffnen zusätzlich das
 * Neu-Aufgabe-Sheet ([openNewTodo]).
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

    private val bib = OneShotSignal()
    val openBib: Flow<Unit> = bib.events

    private val mensaCard = OneShotSignal()
    val openMensaCard: Flow<Unit> = mensaCard.events

    // Zusatzsignal zu openTodos: Neu-Aufgabe-Sheet automatisch öffnen. Wird vom
    // TodosViewModel konsumiert, sobald der Todos-Screen existiert.
    private val newTodo = OneShotSignal()
    val openNewTodo: Flow<Unit> = newTodo.events

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
            if (intent.getStringExtra(EXTRA_MODE) == MODE_NEW) newTodo.fire()
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
        ACTION_OPEN_BIB -> {
            Timber.i("WidgetDeepLink: OPEN_BIB")
            bib.fire()
            true
        }
        ACTION_OPEN_MENSA_CARD -> {
            Timber.i("WidgetDeepLink: OPEN_MENSA_CARD")
            mensaCard.fire()
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
        const val ACTION_OPEN_BIB = "de.transio.hiuni.OPEN_BIB"
        const val ACTION_OPEN_MENSA_CARD = "de.transio.hiuni.OPEN_MENSA_CARD"
        const val EXTRA_MODE = "mode"
        const val MODE_NEW = "new"
    }
}
