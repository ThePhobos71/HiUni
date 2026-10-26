package de.transio.hiuni.feature.search

import de.transio.hiuni.core.search.PendingSearchSelection
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.email.data.EmailEntity
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.mensa.data.MealEntity
import de.transio.hiuni.feature.sport.data.SportEventEntity
import de.transio.hiuni.navigation.Destination
import de.transio.hiuni.feature.learnweb.data.LearnwebAssignment as LearnwebAssignmentEntity
import de.transio.hiuni.feature.learnweb.data.LearnwebCourse as LearnwebCourseEntity

/** Ein angetippter Treffer der Spotlight-Suche, typisiert nach Kategorie. */
sealed interface SearchHit {
    data class Email(val email: EmailEntity) : SearchHit
    data class Event(val event: CustomEventEntity) : SearchHit
    data class Course(val course: CourseEntity) : SearchHit
    data class Exam(val exam: ExamEntity) : SearchHit
    data class Meal(val meal: MealEntity) : SearchHit
    data class Sport(val slot: SportEventEntity) : SearchHit
    data class LearnwebCourse(val course: LearnwebCourseEntity) : SearchHit
    data class LearnwebAssignment(val assignment: LearnwebAssignmentEntity) : SearchHit
}

/** Wohin der Screen nach einem Treffer-Tap navigiert. */
sealed interface SearchNavTarget {
    /** Tab öffnen. Das konkrete Item klappt der Tab über die vorgemerkte Auswahl selbst auf. */
    data class Tab(val destination: Destination) : SearchNavTarget

    /** Eigene Detail-Route (Push), kein Umweg über den Sport-Tab. */
    data class SportDetail(val slotId: Long) : SearchNavTarget

    /** Moodle-Seite im Browser, analog zum Tap im Learnweb-Tab. */
    data class ExternalUrl(val url: String) : SearchNavTarget
}

/**
 * Ziel und (optional) vorzumerkende Auswahl für einen Treffer. Pure Funktion, damit
 * das Mapping ohne Navigation testbar bleibt.
 */
internal fun SearchHit.resolve(): Pair<SearchNavTarget, PendingSearchSelection?> = when (this) {
    is SearchHit.Email ->
        SearchNavTarget.Tab(Destination.Email) to PendingSearchSelection.Email(email)
    is SearchHit.Event ->
        SearchNavTarget.Tab(Destination.Calendar) to PendingSearchSelection.CalendarEvent(event)
    is SearchHit.Course ->
        SearchNavTarget.Tab(Destination.Courses) to PendingSearchSelection.Course(course)
    is SearchHit.Exam ->
        SearchNavTarget.Tab(Destination.Exams) to PendingSearchSelection.Exam(exam)
    is SearchHit.Meal ->
        SearchNavTarget.Tab(Destination.Mensa) to PendingSearchSelection.MensaMeal(meal)
    is SearchHit.Sport -> SearchNavTarget.SportDetail(slot.supersaasSlotId) to null
    is SearchHit.LearnwebCourse -> SearchNavTarget.ExternalUrl(course.url) to null
    is SearchHit.LearnwebAssignment -> SearchNavTarget.ExternalUrl(assignment.url) to null
}
