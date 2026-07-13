package de.transio.hiuni.feature.widgets

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _openTodos = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openTodos: SharedFlow<Unit> = _openTodos.asSharedFlow()

    private val _openCalendar = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openCalendar: SharedFlow<Unit> = _openCalendar.asSharedFlow()

    private val _openExams = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openExams: SharedFlow<Unit> = _openExams.asSharedFlow()

    /**
     * Wird von MainActivity in `onCreate`/`onNewIntent` mit dem eingehenden
     * Intent aufgerufen. Verbraucht die Action + Extras direkt (setzt sie
     * NICHT zurück, das muss der Caller machen via `intent.setAction(null)`
     * um Wiederholung bei Config-Change zu vermeiden — siehe MainActivity).
     */
    fun handleIntent(intent: Intent): Boolean = when (intent.action) {
        ACTION_OPEN_TODOS -> {
            Timber.i("WidgetDeepLink: OPEN_TODOS")
            _openTodos.tryEmit(Unit)
            true
        }
        ACTION_OPEN_CALENDAR -> {
            Timber.i("WidgetDeepLink: OPEN_CALENDAR")
            _openCalendar.tryEmit(Unit)
            true
        }
        ACTION_OPEN_EXAMS -> {
            Timber.i("WidgetDeepLink: OPEN_EXAMS")
            _openExams.tryEmit(Unit)
            true
        }
        else -> false
    }

    companion object {
        const val ACTION_OPEN_TODOS = "de.transio.hiuni.OPEN_TODOS"
        const val ACTION_OPEN_CALENDAR = "de.transio.hiuni.OPEN_CALENDAR"
        const val ACTION_OPEN_EXAMS = "de.transio.hiuni.OPEN_EXAMS"
    }
}
