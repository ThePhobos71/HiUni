package de.transio.hiuni.feature.lsf.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.auth.CasCookieStore
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.runCatchingApp
import de.transio.hiuni.di.IoDispatcher
import de.transio.hiuni.feature.courses.data.CourseDao
import de.transio.hiuni.feature.courses.data.CourseEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class ExamsSyncResult(
    val imported: Int,
    val updated: Int,
    val pruned: Int,
    val matched: Int,
    val unmatched: Int,
    val semesterCode: String
)

interface LsfExamsRepository {
    fun observeUpcoming(limit: Int = 10): Flow<List<ExamEntity>>
    fun observeUpcomingForCourse(courseId: String): Flow<List<ExamEntity>>
    fun countUpcoming(): Flow<Int>
    /**
     * @param force aktuell nur Marker — der Worker triggert immer einen frischen
     *              LSF-Roundtrip; das Flag macht das explizit für künftige
     *              Cache-Strategien (z.B. Min-Interval).
     */
    suspend fun refresh(force: Boolean = false): AppResult<ExamsSyncResult>
}

@Singleton
class LsfExamsRepositoryImpl @Inject constructor(
    private val casSession: CasSession,
    private val cookieStore: CasCookieStore,
    private val httpClient: OkHttpClient,
    private val examDao: ExamDao,
    private val courseDao: CourseDao,
    private val scraper: LsfExamsScraper,
    @IoDispatcher private val io: CoroutineDispatcher
) : LsfExamsRepository {

    override fun observeUpcoming(limit: Int): Flow<List<ExamEntity>> =
        examDao.observeUpcoming(LocalDate.now().toEpochDay(), limit)

    override fun observeUpcomingForCourse(courseId: String): Flow<List<ExamEntity>> =
        examDao.observeUpcomingForCourse(courseId, LocalDate.now().toEpochDay())

    override fun countUpcoming(): Flow<Int> =
        examDao.observeUpcomingCount(LocalDate.now().toEpochDay())

    override suspend fun refresh(force: Boolean): AppResult<ExamsSyncResult> = runCatchingApp {
        withContext(io) {
            val bootstrapTicket = casSession.getServiceTicket(LsfClient.LSF_LOGIN_SERVICE)
            val ua = cookieStore.userAgent()
            val lsfClient = httpClient.newBuilder()
                .followRedirects(true)
                .cookieJar(ExamsHostCookieJar(LSF_HOST))
                .build()

            // 1) Bootstrap mit Ticket → JSESSIONID + Portal-Page-Redirect.
            executeLsf(lsfClient, ua, "${LsfClient.LSF_LOGIN_SERVICE}&ticket=$bootstrapTicket")
                .use { it.body?.string() }

            // 2) Portal-Startseite holen, dort den fertigen "Meine POS-Anmeldungen"-Link
            //    extrahieren (enthält bereits asi-Token + nodeID + semester=).
            val portalHtml = executeLsf(lsfClient, ua, LsfClient.LSF_PORTAL)
                .use { it.body?.string().orEmpty() }
            val examsLink = Jsoup.parse(portalHtml)
                .select("a[href*=examsinfosStudent]")
                .firstOrNull()
                ?.absUrl("href")
                ?.takeIf { it.isNotBlank() }
                ?: Jsoup.parse(portalHtml)
                    .select("a")
                    .firstOrNull { it.text().contains("POS-Anmeldung", ignoreCase = true) }
                    ?.absUrl("href")
                ?: throw ScrapeException(
                    "'Meine POS-Anmeldungen'-Link auf der LSF-Startseite nicht gefunden — " +
                        "vermutlich ist der User-Studiengang nicht für POS-Anmeldungen freigeschaltet"
                )

            val semesterCode = SEMESTER_REGEX.find(examsLink)?.groupValues?.get(1)
                ?: throw ScrapeException("Konnte semester=… aus POS-Link nicht parsen: $examsLink")
            Timber.d("LSF Exams: link=${examsLink.take(80)}… semester=$semesterCode")

            // 3) Tabelle holen + parsen. Bei strukturellem Defekt: ScrapeException
            //    wandert via runCatchingApp -> AppResult.Failure -> Worker == Fatal.
            val tableHtml = executeLsf(lsfClient, ua, examsLink)
                .use { it.body?.string().orEmpty() }
            val parsed = scraper.parse(tableHtml, semesterCode)
            Timber.i("LSF Exams: ${parsed.size} Einträge im Semester $semesterCode geparsed")

            // 4) Course-Lookup vorbauen — wir matchen via Veranstaltungs-Nummer im Kurs-Namen
            //    (LSF-Kurse haben Namen wie "3204 Logistik und Produktion 1 (3204)").
            val allCourses = courseDao.findBySource(CourseEntity.SOURCE_LSF)
            var matched = 0
            var unmatched = 0

            // 5) Pro Eintrag upsert. Wir merken uns die Veranstaltungs-Nummern, die im
            //    Sync drin waren, damit wir alte Einträge desselben Semesters wegräumen.
            val seenNumbers = mutableListOf<String>()
            var imported = 0
            var updated = 0
            for (entry in parsed) {
                val courseId = findMatchingCourseId(entry, allCourses)
                if (courseId != null) matched += 1 else unmatched += 1

                val existing = examDao.findByNumberAndSemester(
                    entry.veranstaltungsNumber, entry.semesterCode
                )
                val entity = ExamEntity(
                    rowId = existing?.rowId ?: 0,
                    veranstaltungsNumber = entry.veranstaltungsNumber,
                    pruefungstext = entry.pruefungstext,
                    moduleName = entry.moduleName,
                    parentModule = entry.parentModule,
                    examDate = entry.examDate,
                    examTime = entry.examTime,
                    rooms = entry.rooms,
                    semester = entry.semester,
                    semesterCode = entry.semesterCode,
                    registrationDate = entry.registrationDate,
                    cancellationDeadline = entry.cancellationDeadline,
                    pruefer = entry.pruefer,
                    courseId = courseId,
                    fetchedAt = Instant.now()
                )
                examDao.upsert(entity)
                if (existing == null) imported += 1 else updated += 1
                seenNumbers += entry.veranstaltungsNumber
            }

            // 6) Cleanup: was im aktuellen Semester nicht mehr vorkommt, fliegt raus.
            //    Andere Semester (z.B. später-historisch) bleiben unangetastet.
            val pruned = if (seenNumbers.isEmpty()) {
                // Komplett leerer Sync: lieber gar nichts löschen, sonst kann ein einmaliger
                // LSF-Schluckauf alle Einträge wegräumen.
                0
            } else {
                examDao.pruneSemester(semesterCode, seenNumbers)
            }

            Timber.i(
                "LSF Exams: imported=$imported updated=$updated pruned=$pruned " +
                    "matched=$matched unmatched=$unmatched semester=$semesterCode"
            )
            ExamsSyncResult(imported, updated, pruned, matched, unmatched, semesterCode)
        }
    }

    /**
     * Course-Matching-Heuristik. Unsere LSF-Kurse haben `lsfId` = publishid (lange Zahl,
     * nicht die Veranstaltungs-Nr), aber der `name` enthält oft die Veranstaltungs-Nr
     * als Prefix ("3204 Logistik und Produktion 1"). Wenn das fehlschlägt, fallback
     * auf einen Substring-Match des Modul-Namens (case-insensitive).
     */
    private fun findMatchingCourseId(
        entry: ParsedExam,
        allCourses: List<CourseEntity>
    ): String? {
        val number = entry.veranstaltungsNumber
        val byNumber = allCourses.firstOrNull { c -> c.name.startsWith("$number ") }
        if (byNumber != null) return byNumber.id
        val modName = entry.moduleName.takeIf { it.isNotBlank() } ?: return null
        return allCourses.firstOrNull { c ->
            c.name.contains(modName, ignoreCase = true)
        }?.id
    }

    private fun executeLsf(
        client: OkHttpClient,
        userAgent: String?,
        url: String
    ): okhttp3.Response {
        val builder = Request.Builder().url(url)
            .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
        userAgent?.takeIf { it.isNotBlank() }?.let { builder.header("User-Agent", it) }
        return client.newCall(builder.build()).execute()
    }

    companion object {
        private const val LSF_HOST = "lsf.uni-hildesheim.de"
        /** Semester aus URL: `semester=20261` → 5-stellig, Format YYYY[1|2]. */
        private val SEMESTER_REGEX = Regex("semester=(\\d{5})")
    }
}

/**
 * Cookie-Jar das nur Cookies für einen einzelnen Host speichert. Duplikat zu den
 * privaten Pendants in [LsfMyCoursesRepositoryImpl]/[LsfStundenplanRepositoryImpl];
 * sharing-once wäre Refactor-Kandidat, ist hier den Aufwand aber nicht wert.
 */
private class ExamsHostCookieJar(private val host: String) : okhttp3.CookieJar {
    private val cookies = mutableListOf<okhttp3.Cookie>()
    override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
        if (url.host != host) return
        synchronized(this.cookies) {
            this.cookies.removeAll { existing -> cookies.any { it.name == existing.name } }
            this.cookies.addAll(cookies)
        }
    }
    override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
        if (url.host != host) return emptyList()
        return synchronized(this.cookies) { this.cookies.toList() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LsfExamsRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindLsfExamsRepository(
        impl: LsfExamsRepositoryImpl
    ): LsfExamsRepository
}
