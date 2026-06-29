package de.transio.hiuni.feature.learnweb.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.runCatchingApp
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.sync.LearnwebAssignmentReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface LearnwebRepository {
    /** Persistierte Kurse als Stream — UI sortiert/filtert selbst falls nötig. */
    fun observeCourses(): Flow<List<LearnwebCourse>>

    /** Alle Assignment-Deadlines, sortiert nach Abgabe-Zeitpunkt aufsteigend. */
    fun observeAssignments(): Flow<List<LearnwebAssignment>>

    /**
     * Anstehende Assignment-Deadlines (`dueEpoch >= now`). `now` wird vom DAO
     * beim Subscribe ausgewertet — bei langlaufenden Subscriptions kann ein
     * gerade-eben-abgelaufener Eintrag noch kurz in der Liste verbleiben, das
     * ist für die UI tolerabel.
     */
    fun observeUpcomingAssignments(): Flow<List<LearnwebAssignment>>

    /**
     * Direktzugriff für die Kalender-Klick-Behandlung: liefert die URL einer
     * Assignment-Spiegelung anhand der Moodle-Event-ID (steckt im
     * `CustomEventEntity.sourceReference`).
     */
    suspend fun findAssignmentUrl(eventId: Long): String?

    /**
     * Liefert die im iCal-Feed gespiegelte URL eines Events (Phase 4).
     * `sourceReference` der gespiegelten Calendar-Entity ist die VEVENT-UID;
     * wir halten den letzten Parse-Snapshot in-memory, damit der Click-Handler
     * den URL-Lookup ohne zusätzliche DB-Tabelle erledigen kann.
     *
     * Kann `null` zurückgeben — bei App-Neustart vor erstem Refresh, oder wenn
     * der Server-Event keinen URL-Wert hatte (reine Calendar-Notizen).
     */
    suspend fun findICalEventUrl(uid: String): String?

    /**
     * Holt das Dashboard, parst Kurse + Assignments, upsertet beides und triggert
     * die Calendar-Spiegelung und den Reminder-Sync. Drosselt sich selbst auf
     * einmal pro [THROTTLE_MS]; `force = true` (Pull-to-Refresh) umgeht das.
     */
    suspend fun refresh(force: Boolean = false): AppResult<Unit>
}

@Singleton
class LearnwebRepositoryImpl @Inject constructor(
    private val client: LearnwebClient,
    private val scraper: LearnwebScraper,
    private val iCalParser: LearnwebICalParser,
    private val dao: LearnwebCourseDao,
    private val assignmentDao: LearnwebAssignmentDao,
    private val calendarSync: LearnwebCalendarSync,
    private val reminderScheduler: LearnwebAssignmentReminderScheduler,
    private val settings: SettingsDataStore,
    private val casSession: CasSession
) : LearnwebRepository {

    /**
     * In-memory URL-Cache pro VEVENT-UID, gefüllt beim letzten erfolgreichen
     * iCal-Sync. Lookup für das Click-Handling — wir wollen keinen extra Room-
     * Layer für reine URL-Strings.
     *
     * Volatile reicht: Writes laufen aus einem einzelnen Refresh-Coroutine,
     * Reads aus dem CalendarViewModel — kein Multi-Writer-Konflikt.
     */
    @Volatile
    private var iCalUrlCache: Map<String, String> = emptyMap()

    override fun observeCourses(): Flow<List<LearnwebCourse>> = dao.observeAll()

    override fun observeAssignments(): Flow<List<LearnwebAssignment>> =
        assignmentDao.observeAll()

    override fun observeUpcomingAssignments(): Flow<List<LearnwebAssignment>> =
        // Snapshot von `now` zum Subscribe-Zeitpunkt — für die UI gut genug,
        // weil ein neu gestarteter ViewModel ohnehin re-subscribed. Hardcoded
        // also kein periodischer Tick.
        assignmentDao.observeAll()
            .map { list -> list.filter { it.dueEpoch >= System.currentTimeMillis() } }

    override suspend fun findAssignmentUrl(eventId: Long): String? =
        assignmentDao.findByEventId(eventId)?.url

    override suspend fun findICalEventUrl(uid: String): String? =
        iCalUrlCache[uid]

    override suspend fun refresh(force: Boolean): AppResult<Unit> = runCatchingApp {
        // Ohne CAS-Session geht der ganze Flow ohnehin auf die Nase. Lieber
        // früh aufgeben statt einen sinnlosen Login-Page-Roundtrip zu machen.
        if (casSession.state.value !is CasState.Authenticated) {
            Timber.d("LearnwebRepository.refresh: keine CAS-Session — abort")
            error("Keine CAS-Session — bitte zuerst über Einstellungen anmelden")
        }
        if (!force) {
            val lastRefresh = settings.lastLearnwebRefreshEpoch.first()
            val age = System.currentTimeMillis() - lastRefresh
            if (lastRefresh > 0 && age < THROTTLE_MS) {
                Timber.d("LearnwebRepository.refresh: throttled (age=${age / 1000}s < ${THROTTLE_MS / 1000}s)")
                return@runCatchingApp
            }
        }
        val html = client.fetchDashboardHtml()
        val parsed = scraper.parseCourses(html)
        Timber.i("LearnwebRepository: parsed ${parsed.size} courses from learnweb dashboard")

        val now = System.currentTimeMillis()
        val base = LearnwebClient.baseUrl()
        val entities = parsed.map { p ->
            val url = p.treeHref?.takeIf { it.startsWith("http") }
                ?: "$base/course/view.php?id=${p.courseId}"
            LearnwebCourse(
                courseId = p.courseId,
                name = p.name,
                url = url,
                syncedAt = now
            )
        }
        if (entities.isNotEmpty()) {
            dao.upsertAll(entities)
            dao.pruneNotIn(entities.map { it.courseId })
        } else {
            // Leer-Antwort ist ungewöhnlich — wir lassen den Bestand stehen,
            // statt versehentlich alles wegzulöschen (z.B. wenn das HTML wegen
            // Layout-Variation kein Course-Filter-Block hatte).
            Timber.w("LearnwebRepository: scraper lieferte 0 Kurse — DB bleibt unverändert")
        }

        // --- Phase 3: Assignment-Deadlines ---
        // Dashboard liefert nur den aktuellen Monats-Kalender; mit
        // `fetchUpcomingHtml()` holen wir zusätzlich die Upcoming-Liste, damit
        // wir Deadlines auch außerhalb dieses Monats sehen. Beides verwendet
        // dasselbe Markup, also kann der gleiche Scraper drüberlaufen.
        val parsedAssignments = run {
            val combined = mutableListOf<ParsedAssignment>()
            combined += scraper.parseAssignments(html)
            runCatching { client.fetchUpcomingHtml() }
                .onSuccess { upcomingHtml ->
                    combined += scraper.parseAssignments(upcomingHtml)
                }
                .onFailure {
                    Timber.w(it, "LearnwebRepository: fetchUpcomingHtml fehlgeschlagen — nur Dashboard-Calendar genutzt")
                }
            // Deduplizieren nach eventId; erste Quelle gewinnt (Reihenfolge oben:
            // Dashboard zuerst). Inhalt ist identisch, daher beliebig.
            combined.associateBy { it.eventId }.values.toList()
        }
        Timber.i("LearnwebRepository: parsed ${parsedAssignments.size} Assignments")

        val assignmentEntities = parsedAssignments.map { a ->
            LearnwebAssignment(
                eventId = a.eventId,
                title = a.title,
                dueEpoch = a.dueEpochMillis,
                url = a.url,
                syncedAt = now
            )
        }
        if (assignmentEntities.isNotEmpty()) {
            assignmentDao.upsertAll(assignmentEntities)
            assignmentDao.pruneNotIn(assignmentEntities.map { it.eventId })
        } else {
            // Symmetrisch zur Course-Logik: leerer Sync löscht NICHT, sonst
            // verlieren wir bei einem einmaligen Moodle-Schluckauf alles.
            Timber.w("LearnwebRepository: scraper lieferte 0 Assignments — DB bleibt unverändert")
        }

        // --- Submission-Status pro nahem Assignment ---
        // Sekundärer Hit pro Assignment-Detail-Page. Das fummelt am Uni-Moodle,
        // also drosseln wir hart: nur Assignments mit Deadline in den nächsten
        // SUBMISSION_LOOKUP_WINDOW_DAYS Tagen, maximal SUBMISSION_LOOKUP_MAX_HITS
        // pro Refresh, dazwischen SUBMISSION_LOOKUP_DELAY_MS (kombiniert mit dem
        // Random-Delay aus dem PolitenessInterceptor reicht das, damit wir nicht
        // wie ein Bot wirken).
        val cutoff = now + SUBMISSION_LOOKUP_WINDOW_DAYS * 24L * 60 * 60 * 1000
        val candidates = assignmentDao.findUpcoming(now)
            .filter { it.dueEpoch in now..cutoff }
            .sortedBy { it.dueEpoch }
            .take(SUBMISSION_LOOKUP_MAX_HITS)
        for ((index, assignment) in candidates.withIndex()) {
            if (index > 0) delay(SUBMISSION_LOOKUP_DELAY_MS)
            runCatching {
                val cmId = parseCmIdFromUrl(assignment.url)
                if (cmId == null) {
                    Timber.d(
                        "LearnwebRepository: cmId aus URL '${assignment.url}' nicht extrahierbar — skip"
                    )
                    return@runCatching
                }
                val detailHtml = client.fetchAssignmentDetailHtml(cmId)
                val parsedStatus = scraper.parseSubmissionStatus(detailHtml)
                assignmentDao.updateSubmissionStatus(
                    rowId = assignment.rowId,
                    status = parsedStatus.status,
                    submittedAt = parsedStatus.lastSubmittedEpoch
                )
                Timber.d(
                    "LearnwebRepository: assignment rowId=${assignment.rowId} cmId=$cmId " +
                        "status=${parsedStatus.status} submittedAt=${parsedStatus.lastSubmittedEpoch}"
                )
            }.onFailure {
                Timber.w(it, "LearnwebRepository: Submission-Status-Hit fehlgeschlagen für rowId=${assignment.rowId}")
            }
        }

        // Calendar-Spiegelung + Reminder-Sync laufen IMMER mit dem aktuellen
        // DB-Snapshot — auch bei leerem Parse-Lauf, damit verwaiste Reminder
        // gecancelt werden (z.B. Assignment war im letzten Sync da, jetzt
        // nicht mehr).
        val freshAssignments = assignmentDao.findUpcoming(now - 24L * 60 * 60 * 1000)
        runCatching { calendarSync.mirror(freshAssignments) }
            .onFailure { Timber.w(it, "LearnwebRepository: Calendar-Mirror fehlgeschlagen") }
        runCatching { reminderScheduler.syncReminders(freshAssignments) }
            .onFailure { Timber.w(it, "LearnwebRepository: Reminder-Sync fehlgeschlagen") }

        // --- Phase 4: iCal-Subscription-Feed ---
        // Parallel zur Assignment-Spiegelung holen wir Moodle's User-Calendar-
        // Feed (alle Event-Typen, nicht nur Assignments) und spiegeln den in
        // custom_events mit sourceKind=LEARNWEB_ICAL. Fehler beim Token-Holen
        // oder Parse sind nicht fatal — wir loggen und überspringen.
        runCatching {
            val icalBody = client.fetchICalFeed()
            if (icalBody.isNullOrBlank()) {
                Timber.i("LearnwebRepository: kein iCal-Feed verfügbar — überspringe Phase 4")
                return@runCatching
            }
            val parsedICalEvents = iCalParser.parseFeed(icalBody)
            Timber.i("LearnwebRepository: parsed ${parsedICalEvents.size} iCal-Events")
            if (parsedICalEvents.isEmpty()) {
                // Leerer Parse → keine Spiegelung anpassen (gleiche Defensive wie
                // bei Assignments). URL-Cache lassen wir intakt, damit Click-
                // Handling bei kurzem Feed-Schluckauf nicht plötzlich blind ist.
                Timber.w("LearnwebRepository: iCal-Feed lieferte 0 Events — überspringe Mirror")
                return@runCatching
            }
            calendarSync.mirrorICalEvents(parsedICalEvents)
            // URL-Cache aktualisieren — nur Events mit nicht-null URL aufnehmen,
            // sonst stehen wir mit Geister-Keys da.
            iCalUrlCache = parsedICalEvents
                .mapNotNull { ev -> ev.url?.let { ev.uid to it } }
                .toMap()
        }.onFailure {
            Timber.w(it, "LearnwebRepository: iCal-Sync (Phase 4) fehlgeschlagen")
        }

        settings.setLastLearnwebRefreshEpoch(now)
    }

    companion object {
        // 15 Minuten — Learnweb-Kursliste ändert sich pro Semester nur einmal,
        // aber pro Refresh fummeln wir an einem Uni-Server. 15 Min ist ein
        // konservativer Default; `force = true` umgeht die Drossel.
        private const val THROTTLE_MS = 15L * 60 * 1000

        /** Nur Assignments mit Deadline innerhalb dieses Fensters bekommen Status-Lookup. */
        private const val SUBMISSION_LOOKUP_WINDOW_DAYS = 14L

        /** Hartes Cap pro Refresh — schützt das Uni-Moodle vor übermäßigen Hits. */
        private const val SUBMISSION_LOOKUP_MAX_HITS = 10

        /** Zusatzdelay zwischen Detail-Hits (oben drauf kommt PolitenessInterceptor-Random). */
        private const val SUBMISSION_LOOKUP_DELAY_MS = 500L

        /**
         * Extrahiert die Course-Module-ID aus einer Assignment-URL der Form
         * `…/mod/assign/view.php?id=12345`. Liefert `null`, wenn die URL kein
         * passendes `id=NNN`-Pattern hat.
         */
        internal fun parseCmIdFromUrl(url: String): Long? {
            val match = CM_ID_REGEX.find(url) ?: return null
            return match.groupValues[1].toLongOrNull()
        }

        private val CM_ID_REGEX = Regex("""\?id=(\d+)""")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LearnwebRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindLearnwebRepository(impl: LearnwebRepositoryImpl): LearnwebRepository
}
