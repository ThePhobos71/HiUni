package de.transio.hiuni.feature.lsf.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.auth.CasCookieStore
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.runCatchingApp
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationPresenter
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.di.IoDispatcher
import de.transio.hiuni.feature.courses.data.CourseDao
import de.transio.hiuni.feature.courses.data.CourseEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class MyCoursesSyncResult(
    val imported: Int,
    val updated: Int,
    val pruned: Int,
    val detailsFetched: Int,
    val semester: String
)

interface LsfMyCoursesRepository {
    /**
     * Holt die "Meine Veranstaltungen"-Liste aus LSF und upsertet jede Veranstaltung
     * als [CourseEntity] mit source=LSF. Vorhandene LSF-Kurse, die nicht mehr in
     * der Antwort stehen, werden gelöscht. USER-Kurse bleiben unangetastet.
     *
     * Für noch undetaillierte Einträge (keine credits + description aus früherem Sync)
     * wird zusätzlich die Veranstaltungs-Detailseite gefetcht — gedrosselt mit einer
     * kurzen Pause zwischen den Requests, um LSF nicht zu hämmern.
     */
    suspend fun sync(): AppResult<MyCoursesSyncResult>
}

@Singleton
class LsfMyCoursesRepositoryImpl @Inject constructor(
    private val casSession: CasSession,
    private val cookieStore: CasCookieStore,
    private val httpClient: OkHttpClient,
    private val courseDao: CourseDao,
    private val scraper: LsfMyCoursesScraper,
    private val detailScraper: LsfCourseDetailScraper,
    private val settings: SettingsDataStore,
    private val presenter: NotificationPresenter,
    @IoDispatcher private val io: CoroutineDispatcher
) : LsfMyCoursesRepository {

    override suspend fun sync(): AppResult<MyCoursesSyncResult> = runCatchingApp {
        withContext(io) {
            // Erst-Sync-Erkennung: Ist noch NIE ein vollständiger LSF-Sync
            // durchgelaufen (Epoch == 0), importieren wir nur den Bestand OHNE
            // Pushes. Sonst würde der User beim ersten Login mit einer Flut von
            // „Neuer Kurs"-Meldungen für seinen gesamten Stundenplan zugespammt.
            // Der Epoch ist das robustere Kriterium als „Tabelle leer": eine leere
            // Tabelle kann auch nach einem Prune-auf-0 entstehen, während der
            // Epoch verlässlich sagt, ob jemals synchronisiert wurde.
            val isFirstSync = settings.lastLsfSyncEpoch.first() == 0L
            val bootstrapTicket = casSession.getServiceTicket(LsfClient.LSF_LOGIN_SERVICE)
            val ua = cookieStore.userAgent()
            val lsfClient = httpClient.newBuilder()
                .followRedirects(true)
                .cookieJar(LsfHostCookieJar(LSF_HOST))
                .build()

            // 1) Bootstrap mit Ticket → setzt JSESSIONID. Anschließend brauchen wir
            //    das `asi`-Session-CSRF-Token, das LSF in jeden internen Link einbaut;
            //    ohne dieses Token antwortet LSF auf wscheck mit HTTP 500.
            executeLsf(lsfClient, ua, "${LsfClient.LSF_LOGIN_SERVICE}&ticket=$bootstrapTicket")
                .use { it.body?.string() }

            // Studienmenu liefert den fertigen "Meine Veranstaltungen"-Link inkl.
            // korrektem asi-Token. Wir greifen den Anchor direkt heraus, statt URL +
            // asi selber zusammenzubauen — Jsoup dekodiert dabei HTML-Entities,
            // sodass `&amp;asi=…` zu `&asi=…` wird und der Token vollständig drin
            // steht.
            val menuHtml = executeLsf(lsfClient, ua, STUDY_SOS_MENU_URL)
                .use { it.body?.string().orEmpty() }
            val coursesLink = Jsoup.parse(menuHtml)
                .selectFirst("a[href*=wscheck=leistungen]")
                ?.attr("href")
                ?.takeIf { it.isNotBlank() }
                ?: error("'Meine Veranstaltungen'-Link im LSF-Studienmenu nicht gefunden")
            val asi = ASI_REGEX.find(coursesLink)?.groupValues?.get(1)
                ?: error("Konnte asi-Token aus 'Meine Veranstaltungen'-Link nicht extrahieren")
            Timber.d("LSF MyCourses: link=${coursesLink.take(80)}… asi-len=${asi.length}")

            // 2) "Meine Veranstaltungen" abrufen — direkt der extrahierte Anchor-Link.
            val html = executeLsf(lsfClient, ua, coursesLink).use { resp ->
                resp.body?.string().orEmpty()
            }
            val page = scraper.parse(html)
            Timber.i("LSF MyCourses: current=${page.currentSemester} entries=${page.entries.size}")

            // 3) Upsert pro LSF-Eintrag, USER-Felder (grade/notes/attended/total) erhalten.
            var imported = 0
            var updated = 0
            val keepIds = mutableListOf<String>()
            val needsDetail = mutableListOf<LsfCourseEntry>()
            // Neu hinzugekommene LSF-Kurse für die „Neuer Kurs"-Pushes sammeln
            // (Titel für die Meldung, lsfId als RefKey-Anker). Erst nach dem
            // DB-Write feuern, damit ein Push-Fehler den Sync nicht kippt.
            val newCourses = mutableListOf<Pair<String, String>>() // (lsfId, titel)
            for (entry in page.entries) {
                val rowId = CourseEntity.lsfRowId(entry.lsfId)
                keepIds += rowId
                val existing = courseDao.findById(rowId)
                val name = entry.code?.let { "${entry.title} ($it)" } ?: entry.title
                val professor = entry.lecturer.orEmpty()
                val merged = if (existing == null) {
                    CourseEntity(
                        id = rowId,
                        name = name,
                        professor = professor,
                        credits = 0,
                        semester = entry.semester,
                        source = CourseEntity.SOURCE_LSF,
                        lsfId = entry.lsfId,
                        room = entry.room,
                        lsfStatus = entry.status,
                        lsfCode = entry.code
                    )
                } else {
                    existing.copy(
                        name = name,
                        professor = professor.ifBlank { existing.professor },
                        semester = entry.semester.ifBlank { existing.semester },
                        source = CourseEntity.SOURCE_LSF,
                        lsfId = entry.lsfId,
                        room = entry.room,
                        lsfStatus = entry.status,
                        lsfCode = entry.code ?: existing.lsfCode
                    )
                }
                courseDao.upsert(merged)
                if (existing == null) {
                    imported += 1
                    newCourses += entry.lsfId to entry.title
                } else {
                    updated += 1
                }
                // Detail-Fetch ist teuer (~300ms LSF). Wir holen ihn nur einmal pro
                // Veranstaltung: solange weder LP noch Beschreibung gesetzt sind,
                // gehen wir davon aus, dass noch nie gefetcht wurde.
                if (merged.credits == 0 && merged.description.isNullOrBlank()) {
                    needsDetail += entry
                }
            }

            // 4) LSF-Kurse, die nicht mehr im aktuellen Sync vorkommen, löschen.
            //    USER-Kurse bleiben unberührt (separate source).
            val pruned = if (keepIds.isEmpty()) {
                0
            } else {
                courseDao.deleteSourcedNotIn(CourseEntity.SOURCE_LSF, keepIds)
            }

            // 5) Detail-Fetch für neue/incomplete Einträge — gedrosselt, damit wir
            //    LSF nicht mit 15 parallelen Requests beballern.
            var detailsFetched = 0
            for ((index, entry) in needsDetail.withIndex()) {
                if (index > 0) delay(DETAIL_THROTTLE_MS)
                runCatching {
                    val detailUrl = "$COURSE_DETAIL_URL_PREFIX&publishid=${entry.lsfId}" +
                        "&moduleCall=webInfo&publishConfFile=webInfo&publishSubDir=veranstaltung&asi=$asi"
                    val detailHtml = executeLsf(lsfClient, ua, detailUrl)
                        .use { it.body?.string().orEmpty() }
                    val detail = detailScraper.parse(detailHtml)
                    if (detail.isEmpty) return@runCatching
                    val rowId = CourseEntity.lsfRowId(entry.lsfId)
                    val existing = courseDao.findById(rowId) ?: return@runCatching
                    val enriched = existing.copy(
                        credits = detail.credits ?: existing.credits,
                        sws = detail.sws ?: existing.sws,
                        description = detail.description ?: existing.description,
                        remark = detail.remark ?: existing.remark,
                        targetAudience = detail.targetAudience ?: existing.targetAudience,
                        moduleAbbreviation = detail.moduleAbbreviation ?: existing.moduleAbbreviation,
                        courseType = detail.veranstaltungsart ?: existing.courseType,
                        professor = detail.responsiblePerson ?: existing.professor
                    )
                    courseDao.upsert(enriched)
                    detailsFetched += 1
                }.onFailure {
                    Timber.w(it, "LSF detail-fetch failed lsfId=${entry.lsfId}")
                }
            }

            // 6) Tutorien/Übungen/Praktika ihrer Mutter-Vorlesung zuordnen.
            //    Gleichheit per (Semester, Modulkürzel) — Vorlesung ist der Eintrag
            //    derselben Gruppe, dessen Veranstaltungsart "Vorlesung" enthält und
            //    der nicht selbst Tutorium-ähnlich ist. Re-runs der gleichen Logik
            //    sind idempotent (parentLsfId wird nur überschrieben wenn nötig).
            val parentLinks = linkTutoriaToLectures()

            // 7) „Neuer Kurs im LSF"-Pushes — NUR wenn nicht Erst-Sync (sonst
            //    Spam beim ersten Login). Bei vielen neuen Kursen auf einmal
            //    (Re-Login / Semesterwechsel) EINE Sammel-Meldung statt N
            //    einzelner. Dedup per RefKey je lsfId. Fehler kippen den Sync
            //    nicht.
            notifyNewCourses(isFirstSync, newCourses)

            Timber.i(
                "LSF MyCourses: imported=$imported updated=$updated pruned=$pruned " +
                    "detailsFetched=$detailsFetched parentLinks=$parentLinks " +
                    "firstSync=$isFirstSync newCourses=${newCourses.size}"
            )
            MyCoursesSyncResult(imported, updated, pruned, detailsFetched, page.currentSemester)
        }
    }

    /**
     * Feuert Pushes für neu hinzugekommene LSF-Kurse.
     *  - Erst-Sync ([isFirstSync]) → gar nichts (nur Bestandsimport).
     *  - > [BULK_THRESHOLD] neue Kurse → EINE Sammel-Meldung (Re-Login/Semesterwechsel).
     *  - sonst je Kurs eine Meldung, dedupliziert per RefKey `course:<lsfId>`.
     * Push-Fehler werden verschluckt, damit der Sync konsistent bleibt.
     */
    private suspend fun notifyNewCourses(isFirstSync: Boolean, newCourses: List<Pair<String, String>>) {
        val pushes = CourseDiffNotifier.decide(isFirstSync, newCourses)
        if (pushes.isEmpty()) return
        runCatching {
            for (push in pushes) {
                presenter.present(
                    kind = NotificationKind.SYSTEM,
                    title = push.title,
                    body = push.body,
                    refKey = push.refKey,
                    systemId = coursePushSystemId(push.refKey)
                )
            }
        }.onFailure { Timber.w(it, "LSF MyCourses: Neuer-Kurs-Push fehlgeschlagen") }
    }

    /** Stabile, kollisionsarme System-Notification-ID aus dem RefKey. Eigener Block, disjunkt von Grades/Events. */
    private fun coursePushSystemId(refKey: String): Int =
        COURSE_SYSTEM_ID_BASE - (refKey.hashCode() and 0x0FFFFFFF)

    /**
     * Verbindet Tutorien/Übungen/Praktika mit ihrer Mutter-Vorlesung über
     * (Semester, Modulkürzel) und schreibt `parentLsfId` zurück. Returns die
     * Anzahl der Verknüpfungen, die in dieser Iteration neu/geändert wurden.
     */
    private suspend fun linkTutoriaToLectures(): Int {
        val all = courseDao.findBySource(CourseEntity.SOURCE_LSF)
        val groups = all.groupBy { it.semester to it.moduleAbbreviation }
        var changes = 0
        for ((key, members) in groups) {
            val (semester, modAbk) = key
            if (semester.isBlank() || modAbk.isNullOrBlank()) continue
            val lecture = members.firstOrNull { entry ->
                !entry.isTutoriumLike && entry.courseType?.contains("vorlesung", ignoreCase = true) == true
            } ?: continue
            val parentLsfId = lecture.lsfId ?: continue
            for (child in members) {
                if (child.id == lecture.id) continue
                if (!child.isTutoriumLike) continue
                if (child.parentLsfId == parentLsfId) continue
                courseDao.upsert(child.copy(parentLsfId = parentLsfId))
                changes += 1
            }
        }
        return changes
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

        /**
         * Studien-/Veranstaltungsmenü. Jeder interne Link auf dieser Seite trägt
         * `asi=<token>` — daraus ziehen wir das Session-CSRF-Token für nachfolgende
         * Anfragen.
         */
        private const val STUDY_SOS_MENU_URL =
            "${LsfClient.LSF_BASE}?state=change&type=1&moduleParameter=studySOSMenu&nextdir=change&next=menu.vm&subdir=applications&xml=menu&purge=y&navigationPosition=functions%2CstudySOSMenu&breadcrumb=studySOSMenu&topitem=functions&subitem=studySOSMenu"

        /** Präfix der Detail-Page-URL; publishid + asi werden pro Veranstaltung ergänzt. */
        private const val COURSE_DETAIL_URL_PREFIX =
            "${LsfClient.LSF_BASE}?state=verpublish&status=init&vmfile=no"

        // Token kann Sonderzeichen wie `$` enthalten (gesehen: "UdiZ$lA1mwAEkilAZ9KD").
        // Wir lesen alles bis zum nächsten URL-Separator (`&`) oder Quote/Whitespace,
        // damit der vollständige Token erhalten bleibt.
        private val ASI_REGEX = Regex("asi=([^&\"\\s<>]+)")

        /**
         * Pause zwischen aufeinanderfolgenden Detail-Page-Calls — schonend für LSF.
         * Plus zusätzlich der PolitenessInterceptor (200-1200ms Random-Delay pro
         * HTTP-Request); bei einer Kurs-Liste mit 8 Modulen sind das im Worst-
         * Case 9*1200ms + 8*400ms ≈ 14s, aber Uni-Server bekommt verteilt
         * statt einem Burst.
         */
        private const val DETAIL_THROTTLE_MS = 600L

        /**
         * Basis für die Course-Push-System-IDs. Disjunkt von Grades
         * (−100_000_000) und Events (positiv), damit sich die IDs nicht
         * überschneiden.
         */
        private const val COURSE_SYSTEM_ID_BASE = -200_000_000
    }
}

/**
 * Cookie-Jar das nur Cookies für einen einzelnen Host speichert — verhindert,
 * dass z.B. das CAS-TGC versehentlich an LSF gesendet wird oder umgekehrt.
 *
 * Duplikat zu [LsfStundenplanRepositoryImpl]s privatem SingleHostCookieJar, weil
 * Beides separat injectable bleibt und ein Refactor "share-once" hier nicht den
 * Aufwand wert ist.
 */
private class LsfHostCookieJar(private val host: String) : okhttp3.CookieJar {
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
abstract class LsfMyCoursesRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindLsfMyCoursesRepository(
        impl: LsfMyCoursesRepositoryImpl
    ): LsfMyCoursesRepository
}
