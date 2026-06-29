package de.transio.hiuni.feature.home

import de.transio.hiuni.feature.bib.data.MyBooking
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.mensa.data.MealEntity
import de.transio.hiuni.feature.movies.data.MovieEntity
import de.transio.hiuni.feature.settings.data.MensaLocation
import de.transio.hiuni.feature.todos.data.TodoEntity
import java.time.LocalDate

data class HomeUiState(
    val today: LocalDate = LocalDate.now(),
    val greetingName: String = "",
    val nextEvent: CustomEventEntity? = null,
    val todaysMeals: List<MealEntity> = emptyList(),
    val mensaLocation: MensaLocation? = null,
    val isMensaOpen: Boolean = false,
    val upcomingMovies: List<MovieEntity> = emptyList(),
    val unreadEmails: Int = 0,
    val nextBibBooking: MyBooking? = null,
    val openTodos: List<TodoEntity> = emptyList(),
    val openTodosCount: Int = 0,
    /** Lookup für die Kurs-Pille auf den Home-Todo-Previews. */
    val openTodosCoursesById: Map<String, CourseEntity> = emptyMap(),
    /** Heutige Calendar-Events (LSF-Stundenplan + User-Custom-Events) für die "Heute"-Sektion. */
    val todayEvents: List<CustomEventEntity> = emptyList(),
    /** lsfId → Anzeige-Kurzform (Modulkürzel falls vorhanden, sonst Modulname). */
    val courseShortNameByLsfId: Map<String, String> = emptyMap(),
    /** Ungelesene Einträge im Push-Center — steuert das rote Badge auf der Glocke. */
    val unreadNotifications: Int = 0,
    /** Anzahl der anstehenden Hochschulsport-Termine — für die Quick-Access-Kachel. */
    val upcomingSportCount: Int = 0,
    /** Anstehende Klausuren (max. 3) aus LSF. Einträge mit `examDate==null` ans Ende. */
    val upcomingExams: List<ExamEntity> = emptyList(),
    /** Anzahl der im Learnweb (Moodle) eingeschriebenen Kurse — für die Quick-Access-Kachel. */
    val learnwebCourseCount: Int = 0,
    /** Anzahl der anstehenden Learnweb-Assignment-Deadlines — Subtitle-Override für die Kachel. */
    val learnwebUpcomingAssignments: Int = 0
)
