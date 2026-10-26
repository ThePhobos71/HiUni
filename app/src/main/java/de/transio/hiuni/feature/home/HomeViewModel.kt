package de.transio.hiuni.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.UserProfile
import de.transio.hiuni.core.common.currentDateFlow
import de.transio.hiuni.core.common.minuteTickerFlow
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.data.NotificationLogRepository
import de.transio.hiuni.feature.bib.data.BibRepository
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.courses.data.CourseRepository
import de.transio.hiuni.feature.email.data.EmailRepository
import de.transio.hiuni.feature.learnweb.data.LearnwebRepository
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.lsf.data.LsfExamsRepository
import de.transio.hiuni.feature.mensa.data.MensaHours
import de.transio.hiuni.feature.mensa.data.MensaRepository
import de.transio.hiuni.feature.movies.data.MovieEntity
import de.transio.hiuni.feature.movies.data.MoviesRepository
import de.transio.hiuni.feature.settings.data.locationById
import de.transio.hiuni.feature.sport.data.SportRepository
import de.transio.hiuni.feature.todos.data.TodosRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
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
    learnwebRepository: LearnwebRepository,
    casSession: CasSession,
    settings: SettingsDataStore,
    private val clock: Clock
) : ViewModel() {

    // Taktgeber für alles Zeitabhängige auf Home. Geteilt, damit nicht jeder abgeleitete
    // Flow einen eigenen Timer startet; sie laufen nur, solange `state` beobachtet wird.
    // `replayExpirationMillis = 0`: nach dem Stoppen kein veraltetes Datum/Minute aus dem
    // Cache nachliefern, beim nächsten Subscribe wird frisch von der Uhr gelesen.
    private val currentDate: Flow<LocalDate> = currentDateFlow(clock)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), replay = 1)
    private val minuteTick: Flow<Instant> = minuteTickerFlow(clock)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), replay = 1)

    // Nächster Termin: Die DB-Abfrage hängt nur am Datum (Fenster heute 00:00 plus
    // NEXT_EVENT_WINDOW_DAYS, deckt also zu jeder Uhrzeit mindestens die nächsten 14 Tage ab).
    // Welcher Termin "der nächste" ist, entscheidet der Minutentakt im Speicher, damit ein
    // gerade begonnener Termin nicht bis zur nächsten Datenänderung oben stehen bleibt.
    private val nextEventFlow = combine(
        currentDate.flatMapLatest { day ->
            val zone = clock.zone
            calendarRepository.observeRange(
                day.atStartOfDay(zone).toInstant(),
                day.plusDays(NEXT_EVENT_WINDOW_DAYS).atStartOfDay(zone).toInstant()
            )
        },
        minuteTick
    ) { events, now -> events.firstOrNull { it.startTime.isAfter(now) } }

    // Heutige Calendar-Events (LSF + User). Range ist 00:00 lokal bis morgen 00:00 und
    // wird bei jedem Tageswechsel neu abgefragt.
    private val todayEventsFlow = currentDate.flatMapLatest { day ->
        val zone = clock.zone
        calendarRepository.observeRange(
            day.atStartOfDay(zone).toInstant(),
            day.plusDays(1).atStartOfDay(zone).toInstant()
        )
    }

    private val todaysMealsFlow = currentDate.flatMapLatest { day ->
        mensaRepository.observeForDate(day)
    }

    // Persistierte Öffnungszeiten der gewählten Location. Ohne sie hing „offen/geschlossen"
    // am In-Memory-Cache von MensaHours, der erst nach einem Besuch des Mensa-Screens warm
    // war; bis dahin galten die Fallback-Slots. Das Repo folgt selbst dem Location-Wechsel
    // und liefert neue Zeiten nach jedem Refresh.
    private val mensaOpeningHoursFlow = mensaRepository.observeOpeningHours()

    // Kino, Klausuren, Sport und Learnweb frieren "heute"/"jetzt" im Repo beim Aufruf ein.
    // Der erneute Aufruf pro Tageswechsel sorgt dafür, dass z. B. gestrige Filme oder
    // Klausuren rausfallen.
    private val upcomingMoviesFlow = currentDate.flatMapLatest { moviesRepository.observeUpcoming() }

    private val greetingNameFlow = combine(
        casSession.profile,
        settings.displayNameMode,
        settings.customDisplayName
    ) { profile, mode, custom -> computeGreetingName(profile, mode, custom) }

    // Nächste Bib-Buchung (heute oder Zukunft, früheste zuerst).
    // Am Minutentakt, damit eine abgelaufene Buchung ohne neuen Bib-Sync verschwindet.
    private val nextBibBookingFlow = combine(bibRepository.state, minuteTick) { data, tick ->
        val zoned = tick.atZone(clock.zone)
        val now = zoned.toLocalTime()
        val today = zoned.toLocalDate()
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
            .filter { it.source == CourseEntity.SOURCE_LSF }
            .mapNotNull { course ->
                val lsfId = course.lsfId ?: return@mapNotNull null
                val short = course.moduleAbbreviation?.takeIf { it.isNotBlank() }
                    ?: course.name
                lsfId to short
            }
            .toMap()
    }
    private val unreadNotificationsFlow = notificationLogRepository.observeUnreadCount()
    // Siehe upcomingMoviesFlow: erneuter Repo-Aufruf pro Tageswechsel.
    private val upcomingSportFlow = currentDate.flatMapLatest { sportRepository.countUpcoming() }
    private val upcomingExamsFlow = currentDate.flatMapLatest {
        examsRepository.observeUpcoming(limit = 3)
    }
    private val learnwebCourseCountFlow = learnwebRepository.observeCourses()
        .map { it.size }
    private val learnwebUpcomingAssignmentsFlow = currentDate
        .flatMapLatest { learnwebRepository.observeUpcomingAssignments() }
        .map { it.size }

    val state: StateFlow<HomeUiState> = combine(
        combine(
            nextEventFlow,
            todaysMealsFlow,
            todayEventsFlow,
            courseShortNameByLsfIdFlow,
            upcomingExamsFlow
        ) { next, meals, today, shortNames, exams ->
            HomeEventsBundle(next, meals, today, shortNames, exams)
        },
        combine(
            settings.mensaLocationId,
            mensaOpeningHoursFlow,
            upcomingMoviesFlow,
            currentDate,
            minuteTick
        ) { id, hours, movies, today, tick ->
            val zoned = tick.atZone(clock.zone)
            MensaMoviesBundle(
                locationId = id,
                movies = movies,
                isMensaOpen = MensaHours.isOpenNow(
                    locationId = id,
                    blocks = hours,
                    today = zoned.toLocalDate(),
                    now = zoned.toLocalTime()
                ),
                today = today,
                now = tick
            )
        },
        combine(greetingNameFlow, openTodosFlow, openTodosCountFlow) { name, todos, count ->
            Triple(name, todos, count)
        },
        combine(nextBibBookingFlow, unreadEmailsFlow, coursesByIdFlow) { b, u, c ->
            Triple(b, u, c)
        },
        combine(
            unreadNotificationsFlow,
            upcomingSportFlow,
            learnwebCourseCountFlow,
            learnwebUpcomingAssignmentsFlow
        ) { n, s, l, a -> NotifsAndSportBundle(n, s, l, a) }
    ) { events, mensaMovies, greetingTodos, bibEmailCourses, notifsAndSport ->
        val (greetingName, openTodos, openTodosCount) = greetingTodos
        val (nextBib, unread, coursesById) = bibEmailCourses
        val unreadNotifs = notifsAndSport.unreadNotifs
        val upcomingSport = notifsAndSport.upcomingSport
        val learnwebCount = notifsAndSport.learnwebCount
        val learnwebUpcoming = notifsAndSport.learnwebUpcoming
        HomeUiState(
            today = mensaMovies.today,
            now = mensaMovies.now,
            greetingName = greetingName,
            nextEvent = events.nextEvent,
            todaysMeals = events.todaysMeals,
            mensaLocation = locationById(mensaMovies.locationId),
            isMensaOpen = mensaMovies.isMensaOpen,
            upcomingMovies = mensaMovies.movies.take(5),
            unreadEmails = unread,
            nextBibBooking = nextBib,
            openTodos = openTodos,
            openTodosCount = openTodosCount,
            openTodosCoursesById = coursesById,
            unreadNotifications = unreadNotifs,
            upcomingSportCount = upcomingSport,
            todayEvents = events.todayEvents,
            courseShortNameByLsfId = events.courseShortNameByLsfId,
            upcomingExams = events.upcomingExams,
            learnwebCourseCount = learnwebCount,
            learnwebUpcomingAssignments = learnwebUpcoming,
            isContentLoaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), HomeUiState())

    /** Bündel für den Notifs/Sport/Learnweb-Slot des äußeren `combine` (max 5 Slots). */
    private data class NotifsAndSportBundle(
        val unreadNotifs: Int,
        val upcomingSport: Int,
        val learnwebCount: Int,
        val learnwebUpcoming: Int
    )

    /** Bündel für den Mensa/Kino-Slot inkl. Tagesdatum, Minutentakt und Öffnungsstatus dazu. */
    private data class MensaMoviesBundle(
        val locationId: Int,
        val movies: List<MovieEntity>,
        val isMensaOpen: Boolean,
        val today: LocalDate,
        val now: Instant
    )

    /** Bündel für die Events-Spalte des äußeren `combine`, damit wir nicht über 5 Slots hinaus kommen. */
    private data class HomeEventsBundle(
        val nextEvent: CustomEventEntity?,
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

    private companion object {
        /** 15 Tage ab heute 00:00 decken zu jeder Uhrzeit mindestens die nächsten 14 Tage ab. */
        const val NEXT_EVENT_WINDOW_DAYS = 15L
    }
}
