package de.transio.hiuni.core.search

import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.email.data.EmailEntity
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.mensa.data.MealEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ein in der Spotlight-Suche angetippter Treffer, den der Ziel-Tab beim Öffnen
 * selbst aufklappen soll (Mail-Detail, Kurs-Detail, Mensa-Detail-Sheet, …).
 *
 * Nur Typen, deren Ziel-Screen kein eigenes Detail-Routing hat, landen hier. Sport
 * hat eine echte Detail-Route, Learnweb öffnet direkt die Moodle-Seite im Browser.
 */
sealed interface PendingSearchSelection {
    data class Email(val email: EmailEntity) : PendingSearchSelection
    data class CalendarEvent(val event: CustomEventEntity) : PendingSearchSelection
    data class Course(val course: CourseEntity) : PendingSearchSelection
    data class Exam(val exam: ExamEntity) : PendingSearchSelection
    data class MensaMeal(val meal: MealEntity) : PendingSearchSelection
}

/**
 * One-Shot-Übergabekanal von der Spotlight-Suche zum Ziel-ViewModel.
 *
 * Warum kein Nav-Argument? Die Tab-Navigation läuft mit `restoreState = true`. Ist
 * der Ziel-Tab schon einmal besucht worden, stellt Navigation-Compose den alten
 * Back-Stack-Eintrag samt alter Argumente wieder her, ein neues Argument käme nie an.
 * Außerdem würde ein Argument nach Rotation oder Tab-Wechsel erneut greifen und das
 * Detail wieder aufklappen, obwohl der User es längst geschlossen hat.
 *
 * Ablauf: Die Suche ruft [stage] direkt vor dem Navigieren auf. Jedes Ziel-ViewModel
 * sammelt über [collectPending] nur seinen eigenen Typ ein und räumt den Eintrag dabei
 * per [consume] ab. Damit wird eine Auswahl genau einmal angewendet, egal ob das
 * ViewModel schon lebt (gespeicherter Tab) oder erst beim Navigieren entsteht.
 * Prozess-Tod übersteht der Holder bewusst nicht: eine verlorene Vorauswahl ist
 * harmlos, der User landet dann einfach auf dem Tab.
 */
@Singleton
class SearchSelectionHolder @Inject constructor() {

    private val _pending = MutableStateFlow<PendingSearchSelection?>(null)
    val pending: StateFlow<PendingSearchSelection?> = _pending.asStateFlow()

    /** Merkt eine Auswahl vor. Eine ältere, nie abgeholte Auswahl wird ersetzt. */
    fun stage(selection: PendingSearchSelection) {
        _pending.value = selection
    }

    /** Verwirft eine liegengebliebene Auswahl (z. B. wenn ein Treffer im Browser aufgeht). */
    fun clear() {
        _pending.value = null
    }

    /**
     * Räumt [selection] atomar ab. `true` nur für genau einen Aufrufer, alle weiteren
     * (oder ein Aufruf nach einer neueren Auswahl) bekommen `false`.
     */
    fun consume(selection: PendingSearchSelection): Boolean =
        _pending.compareAndSet(selection, null)
}

/**
 * Sammelt dauerhaft alle Vormerkungen vom Typ [T] ein und reicht jede genau einmal an
 * [onSelection] weiter. Fremde Typen bleiben liegen, damit der zuständige Screen sie
 * abholen kann. Gedacht für `viewModelScope.launch { … }` im `init` eines ViewModels.
 */
suspend inline fun <reified T : PendingSearchSelection> SearchSelectionHolder.collectPending(
    crossinline onSelection: (T) -> Unit
) {
    pending.collect { selection ->
        if (selection is T && consume(selection)) onSelection(selection)
    }
}
