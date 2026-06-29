package de.transio.hiuni.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.UserProfile
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.data.NotificationLogRepository
import de.transio.hiuni.feature.bib.data.BibRepository
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.courses.data.CourseRepository
import de.transio.hiuni.feature.email.data.EmailRepository
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.lsf.data.LsfExamsRepository
import de.transio.hiuni.feature.mensa.data.MensaHours
import de.transio.hiuni.feature.mensa.data.MensaRepository
import de.transio.hiuni.feature.movies.data.MoviesRepository
import de.transio.hiuni.feature.settings.data.locationById
import de.transio.hiuni.feature.sport.data.SportRepository
import de.transio.hiuni.feature.todos.data.TodosRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    calendarRepository: CalendarRepository,
    mensaRepository: MensaRepository,
    moviesRepository: MoviesRepository,
    bibRepository: BibRepository,
    emailRepository: EmailRepository,
    courseRepository: CourseRepository,
    private val todosRepository: TodosRepository,
    notificationLogRepository: NotificationLogRepository,
    sportRepository: SportRepository,
    examsRepository: LsfExamsRepository,
    casSession: CasSession,
    settings: SettingsDataStore
) : ViewModel() {

    private val nextEventFlow = calendarRepository
        .observeRange(Instant.now(), Instant.now().plusSeconds(60L * 60 * 24 * 14))

    // Heutige Calendar-Events (LSF + User). Range ist 00:00 lokal bis morgen 00:00 —
    // ein Snapshot zur ViewModel-Erstellung. Bei Tageswechsel (selten via Process-Death,
    // oft via App-Resume um Mitternacht) recomputiert sich das nicht automatisch; das
    // ist OK für v1, der Nutzer öffnet den Kalender direkt für die Ground-Truth.
    private val todayEventsFlow = run {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val from = today.atStartOfDay(zone).toInstant()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant()
        calendarRepository.observeRange(from, to)
    }

    private val todaysMealsFlow = mensaRepository.observeForDate(LocalDate.now())
    private val upcomingMoviesFlow = moviesRepository.observeUpcoming()

    private val greetingNameFlow = combine(
        casSession.profile,
        settings.displayNameMode,
        settings.customDisplayName
    ) { profile, mode, custom -> computeGreetingName(profile, mode, custom) }

    // Nächste Bib-Buchung (heute oder Zukunft, früheste zuerst).
    private val nextBibBookingFlow = bibRepository.state.map { data ->
        val now = LocalTime.now()
        val today = LocalDate.now()
        data.snapshot?.myBookings
            ?.filter { b ->
                b.date.isAfter(today) || (b.date == today && b.endTime.isAfter(now))
            }
            ?.minByOrNull { it.date.atTime(it.startTime) }
    }
    private val unreadEmailsFlow = emailRepository.observeInbox()
        .map { mails -> mails.count { !it.isRead } }

    // Home zeigt nur die ersten 3 offenen Aufgaben. Der Open-Count steckt via
    // `observeOpenCount` separat dabei, damit der Quick-Access-Tile auch dann
    // einen Zähler hat, wenn mehr als 3 offen sind.
    private val openTodosFlow = todosRepository.observeOpen(limit = 3)
    private val openTodosCountFlow = todosRepository.observeOpenCount()
    private val coursesByIdFlow = courseRepository.observeAll()
        .map { courses -> courses.associateBy { it.id } }
    // Modulkürzel-Lookup über die LSF-publishid. Wird auf der Heute-Sektion benutzt,
    // damit lange LSF-Titel ("3204 Einführung in die Informatik") gegen die knackige
    // Abkürzung ("IT-EINF1") getauscht werden können.
    private val courseShortNameByLsfIdFlow = courseRepository.observeAll().map { courses ->
        courses
            .filter { it.source == CourseEntity.SOURCE_LSF && it.lsfId != null }
            .associate { course ->
                val short = course.moduleAbbreviation?.takeIf { it.isNotBlank() }
                    ?: course.name
                course.lsfId!! to short
            }
    }
    private val unreadNotificationsFlow = notificationLogRepository.observeUnreadCount()
    private val upcomingSportFlow = sportRepository.countUpcoming()
    private val upcomingExamsFlow = examsRepository.observeUpcoming(limit = 3)

    val state: StateFlow<HomeUiState> = combine(
        combine(
            nextEventFlow,
            todaysMealsFlow,
            todayEventsFlow,
            courseShortNameByLsfIdFlow,
            upcomingExamsFlow
        ) { upcoming, meals, today, shortNames, exams ->
            HomeEventsBundle(upcoming, meals, today, shortNames, exams)
        },
        combine(settings.mensaLocationId, upcomingMoviesFlow) { id, movies -> id to movies },
        combine(greetingNameFlow, openTodosFlow, openTodosCountFlow) { name, todos, count ->
            Triple(name, todos, count)
        },
        combine(nextBibBookingFlow, unreadEmailsFlow, coursesByIdFlow) { b, u, c ->
            Triple(b, u, c)
        },
        combine(unreadNotificationsFlow, upcomingSportFlow) { n, s -> n to s }
    ) { events, locationAndMovies, greetingTodos, bibEmailCourses, notifsAndSport ->
        val (locationId, movies) = locationAndMovies
        val (greetingName, openTodos, openTodosCount) = greetingTodos
        val (nextBib, unread, coursesById) = bibEmailCourses
        val (unreadNotifs, upcomingSport) = notifsAndSport
        val now = Instant.now()
        val nextEvent = events.upcomingEvents.firstOrNull { it.startTime.isAfter(now) }
        HomeUiState(
            today = LocalDate.now(),
            greetingName = greetingName,
            nextEvent = nextEvent,
            todaysMeals = events.todaysMeals,
            mensaLocation = locationById(locationId),
            isMensaOpen = MensaHours.isOpenNow(locationId = locationId),
            upcomingMovies = movies.take(5),
            unreadEmails = unread,
            nextBibBooking = nextBib,
            openTodos = openTodos,
            openTodosCount = openTodosCount,
            openTodosCoursesById = coursesById,
            unreadNotifications = unreadNotifs,
            upcomingSportCount = upcomingSport,
            todayEvents = events.todayEvents,
            courseShortNameByLsfId = events.courseShortNameByLsfId,
            upcomingExams = events.upcomingExams
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), HomeUiState())

    /** Bündel für die Events-Spalte des äußeren `combine`, damit wir nicht über 5 Slots hinaus kommen. */
    private data class HomeEventsBundle(
        val upcomingEvents: List<CustomEventEntity>,
        val todaysMeals: List<de.transio.hiuni.feature.mensa.data.MealEntity>,
        val todayEvents: List<CustomEventEntity>,
        val courseShortNameByLsfId: Map<String, String>,
        val upcomingExams: List<ExamEntity>
    )

    /**
     * Toggle einer Aufgabe direkt aus der Home-Vorschau heraus — die Aufgabe verschwindet
     * danach aus `openTodos`, weil der Flow nur `isDone = 0` liefert.
     */
    fun toggleTodoDone(todo: de.transio.hiuni.feature.todos.data.TodoEntity) {
        viewModelScope.launch {
            todosRepository.setDone(todo.id, !todo.isDone)
        }
    }

    private fun computeGreetingName(
        profile: UserProfile,
        mode: String,
        custom: String
    ): String {
        return when (mode) {
            SettingsDataStore.DISPLAY_NAME_MODE_CUSTOM ->
                custom.trim().takeIf { it.isNotBlank() } ?: defaultName(profile)
            SettingsDataStore.DISPLAY_NAME_MODE_ALL ->
                profile.vorname?.takeIf { it.isNotBlank() } ?: defaultName(profile)
            else /* FIRST */ ->
                profile.firstName ?: defaultName(profile)
        }
    }

    private fun defaultName(profile: UserProfile): String =
        profile.uid?.takeIf { it.isNotBlank() } ?: "Studi"
}
