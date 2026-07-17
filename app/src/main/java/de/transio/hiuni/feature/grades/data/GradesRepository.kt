package de.transio.hiuni.feature.grades.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.auth.CasCookieStore
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.AuthRequiredException
import de.transio.hiuni.core.common.runCatchingApp
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationPresenter
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.core.common.Semester
import de.transio.hiuni.di.IoDispatcher
import de.transio.hiuni.feature.courses.data.CourseDao
import de.transio.hiuni.feature.lsf.data.LsfClient
import de.transio.hiuni.core.sync.PrefetchOrchestrator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class GradesSyncResult(
    val imported: Int,
    val updated: Int,
    val pruned: Int,
    /** Wie viele Push-Meldungen für neue/aktualisierte Noten ausgelöst wurden. */
    val notified: Int
)

interface GradesRepository {
    /** Beobachtet alle Leistungen, konto-gruppiert + chronologisch. */
    fun observeAll(): Flow<List<GradeEntity>>

    /** Beobachtet die Kopf-Summen (GPA / gewichtete LP / Gesamt-LP). */
    fun observeSummary(): Flow<GradesSummaryEntity?>

    /**
     * Lädt den Notenspiegel aus dem LSF (Menü → asi → P_vx=lang), diffed gegen die
     * DB (Upsert per Merge-Key + Prune verschwundener Zeilen), schreibt die Summen
     * und feuert Push-Meldungen für NEUE bzw. neu benotete Leistungen.
     *
     * @param force `true` (Pull-to-Refresh) erzwingt einen frischen LSF-Roundtrip.
     *   `false` (Cold-Start / Warmup) überspringt, wenn der letzte erfolgreiche
     *   Refresh jünger als [de.transio.hiuni.core.sync.PrefetchOrchestrator.TTL_GRADES_MS]
     *   ist — so vermeidet das Öffnen des Noten-Screens einen teuren LSF-Roundtrip,
     *   wenn der Cache noch frisch ist.
     */
    suspend fun refresh(force: Boolean = false): AppResult<GradesSyncResult>
}

@Singleton
class GradesRepositoryImpl @Inject constructor(
    private val casSession: CasSession,
    private val cookieStore: CasCookieStore,
    private val httpClient: OkHttpClient,
    private val gradeDao: GradeDao,
    private val courseDao: CourseDao,
    private val scraper: NotenspiegelScraper,
    private val settings: SettingsDataStore,
    private val presenter: NotificationPresenter,
    @IoDispatcher private val io: CoroutineDispatcher
) : GradesRepository {

    override fun observeAll(): Flow<List<GradeEntity>> = gradeDao.observeAll()

    override fun observeSummary(): Flow<GradesSummaryEntity?> = gradeDao.observeSummary()

    override suspend fun refresh(force: Boolean): AppResult<GradesSyncResult> = runCatchingApp {
        withContext(io) {
            // TTL-Gate: beim Cold-Start/Warmup (force=false) den teuren LSF-Roundtrip
            // überspringen, wenn der letzte Sync jünger als die Grades-TTL ist. Pull-
            // to-Refresh (force=true) umgeht das immer.
            if (!force) {
                val lastRefresh = settings.lastGradesRefreshEpoch.first()
                val age = System.currentTimeMillis() - lastRefresh
                if (lastRefresh > 0 && age < THROTTLE_MS) {
                    Timber.d("Grades: refresh übersprungen (Alter ${age / 1000}s < ${THROTTLE_MS / 1000}s)")
                    return@withContext GradesSyncResult(0, 0, 0, 0)
                }
            }
            val bootstrapTicket = casSession.getServiceTicket(LsfClient.LSF_LOGIN_SERVICE)
            val ua = cookieStore.userAgent()
            val lsfClient = httpClient.newBuilder()
                .followRedirects(true)
                .cookieJar(GradesHostCookieJar(LSF_HOST))
                .build()

            // 1) Bootstrap mit Ticket → JSESSIONID + Portal-Redirect.
            executeLsf(lsfClient, ua, "${LsfClient.LSF_LOGIN_SERVICE}&ticket=$bootstrapTicket")
                .use { it.body?.string() }

            // 2) "Veranstaltungsmanagement"-Menü holen und den Notenspiegel-Link
            //    (inkl. frischem session-gebundenem asi-Token) extrahieren.
            val menuHtml = executeLsf(lsfClient, ua, VERANSTALTUNGSMANAGEMENT_MENU)
                .use { it.body?.string().orEmpty() }
            val notenspiegelUrl = scraper.findNotenspiegelUrl(menuHtml)
                ?: throw AuthRequiredException(
                    "Notenspiegel-Link im Veranstaltungsmanagement-Menü nicht gefunden — " +
                        "vermutlich CAS-Session abgelaufen oder Studiengang nicht freigeschaltet"
                )

            // 3) Notenspiegel in Ansicht "lang" laden (P_vx=lang erzwingen) + parsen.
            val langUrl = withLangView(notenspiegelUrl)
            val html = executeLsf(lsfClient, ua, langUrl)
                .use { it.body?.string().orEmpty() }
            val result = scraper.parse(html)
            Timber.i("Grades: ${result.grades.size} Leistungen geparsed, summary=${result.summary != null}")

            // 4) Diff-Basis: aktueller DB-Stand, indexiert per Merge-Key.
            val existingByKey = gradeDao.findAll().associateBy { it.mergeKey }
            val now = Instant.now().toEpochMilli()

            var imported = 0
            var updated = 0
            val seenKeys = mutableListOf<String>()
            // Push-Kandidaten sammeln und ERST nach dem DB-Write feuern, damit ein
            // Push-Fehler den Sync nicht kippt und die DB in jedem Fall konsistent ist.
            val pushTitles = mutableListOf<Pair<Long?, String>>() // (labnr, titel) für RefKey-Dedup

            for (grade in result.grades) {
                val key = grade.mergeKey
                seenKeys += key
                val existing = existingByKey[key]
                val entity = GradeEntity(
                    rowId = existing?.rowId ?: 0,
                    mergeKey = key,
                    labnr = grade.labnr,
                    pruefungsNr = grade.pruefungsNr,
                    titel = grade.titel,
                    veranstaltungsNr = grade.veranstaltungsNr,
                    kontoNr = grade.kontoNr,
                    kontoName = grade.kontoName,
                    semester = grade.semester,
                    note = grade.note,
                    status = grade.status,
                    bonusLp = grade.bonusLp,
                    vermerk = grade.vermerk,
                    versuch = grade.versuch,
                    pruefungsDatum = grade.pruefungsDatum,
                    fetchedAt = now
                )
                gradeDao.upsert(entity)
                if (existing == null) {
                    imported += 1
                    // Neue Zeile → Push, aber nur wenn sie tatsächlich benotet/abgeschlossen
                    // ist (nicht für frisch angemeldete Prüfungen ohne Note).
                    if (isNewlyGraded(grade)) pushTitles += grade.labnr to grade.titel
                } else {
                    updated += 1
                    // Übergang REGISTERED → PASSED/FAILED bzw. Note frisch eingetragen.
                    if (becameGraded(existing, grade)) pushTitles += grade.labnr to grade.titel
                }
            }

            // 5) Prune: verschwundene Zeilen entfernen. Leerer Parse → nichts löschen
            //    (einmaliger LSF-Schluckauf darf nicht alle Noten wegräumen).
            val pruned = if (seenKeys.isEmpty()) 0 else gradeDao.pruneNotIn(seenKeys)

            // 6) Summen schreiben (falls vorhanden).
            result.summary?.let {
                gradeDao.upsertSummary(
                    GradesSummaryEntity(
                        gpa = it.gpa,
                        weightedLp = it.weightedLp,
                        totalLp = it.totalLp,
                        fetchedAt = now
                    )
                )
            }

            // 7) Push für neue/neu-benotete Noten — DATENSCHUTZ: KEINE Note im Text
            //    (Sperrbildschirm!). Dedup per RefKey je labnr (bzw. mergeKey-Fallback).
            //    Fehler im Push-Pfad kippen den Sync nicht.
            var notified = 0
            for ((labnr, titel) in pushTitles) {
                val refKey = if (labnr != null) "grade:$labnr" else "grade:t:$titel"
                val systemId = gradePushSystemId(refKey)
                val ok = runCatching {
                    presenter.present(
                        kind = NotificationKind.GRADE,
                        title = "Neue Note eingetragen",
                        body = titel,
                        refKey = refKey,
                        systemId = systemId
                    )
                }.onFailure { Timber.w(it, "Grades: Neue-Note-Push fehlgeschlagen") }.isSuccess
                if (ok) notified += 1
            }

            settings.setLastGradesRefreshEpoch(now)
            // Icon-Unlock-Anker an den echten Studienverlauf angleichen. Fehler hier
            // dürfen den Sync nicht kippen → eigenes runCatching.
            runCatching { updateIconUnlockAnchor() }
                .onFailure { Timber.w(it, "Grades: Icon-Unlock-Anker-Update fehlgeschlagen") }
            Timber.i("Grades: imported=$imported updated=$updated pruned=$pruned notified=$notified")
            GradesSyncResult(imported, updated, pruned, notified)
        }
    }

    /**
     * Setzt den „erstes Semester"-Anker (Icon-Unlock) auf das FRÜHESTE Semester
     * aus dem echten Studienverlauf — Noten + Kurse. So startet ein Student im
     * höheren Semester nicht bei 0 Übergängen. Verschiebt den Anker nur nach vorne
     * (siehe [SettingsDataStore.anchorFirstSemesterAtLeast]).
     */
    private suspend fun updateIconUnlockAnchor() {
        val labels = gradeDao.findDistinctSemesters() + courseDao.findDistinctSemesters()
        val earliest = Semester.earliestOf(labels) ?: return
        settings.anchorFirstSemesterAtLeast(earliest)
    }

    /** Neue Zeile gilt als benotet, wenn sie eine Note trägt ODER bereits PASSED/FAILED ist. */
    private fun isNewlyGraded(g: ParsedGrade): Boolean =
        g.note != null || g.status == GradeStatus.PASSED || g.status == GradeStatus.FAILED

    /**
     * Bestehende Zeile hat gerade eine Note/ein Endergebnis bekommen:
     *  - Status wechselte von REGISTERED auf PASSED/FAILED, ODER
     *  - vorher keine Note, jetzt eine Note.
     */
    private fun becameGraded(existing: GradeEntity, now: ParsedGrade): Boolean {
        val statusUnlocked = existing.status == GradeStatus.REGISTERED &&
            (now.status == GradeStatus.PASSED || now.status == GradeStatus.FAILED)
        val noteAppeared = existing.note == null && now.note != null
        return statusUnlocked || noteAppeared
    }

    /**
     * Hängt `P_vx=lang` an die Notenspiegel-URL, um die Langansicht zu erzwingen.
     * Ist der Param schon gesetzt (anderer Wert), wird er ersetzt.
     */
    private fun withLangView(url: String): String {
        val stripped = url.replace(Regex("([?&])P_vx=[^&]*&?", RegexOption.IGNORE_CASE)) { m ->
            // Trenner erhalten, doppelte & vermeiden.
            if (m.groupValues[1] == "?") "?" else "&"
        }.trimEnd('?', '&')
        val sep = if (stripped.contains('?')) "&" else "?"
        return "$stripped${sep}P_vx=lang"
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

    /** Stabile, kollisionsarme System-Notification-ID aus dem RefKey. Negativ, im SYSTEM-Bereich. */
    private fun gradePushSystemId(refKey: String): Int =
        GRADE_SYSTEM_ID_BASE - (refKey.hashCode() and 0x0FFFFFFF)

    companion object {
        /** TTL für den Cold-Start/Warmup-Skip — geteilt mit dem PrefetchOrchestrator. */
        private const val THROTTLE_MS = PrefetchOrchestrator.TTL_GRADES_MS

        private const val LSF_HOST = "lsf.uni-hildesheim.de"

        /**
         * Direktlink auf das "Veranstaltungsmanagement"-Menü (studySOSMenu). Dort
         * steht in `#makronavigation` der Notenspiegel-Link mit frischem asi-Token.
         */
        private const val VERANSTALTUNGSMANAGEMENT_MENU =
            "${LsfClient.LSF_BASE}?state=change&type=1&moduleParameter=studySOSMenu" +
                "&nextdir=change&next=menu.vm&subdir=applications&xml=menu&purge=y" +
                "&navigationPosition=functions%2CstudySOSMenu&breadcrumb=studySOSMenu" +
                "&topitem=functions&subitem=studySOSMenu"

        /**
         * Basis für die Grade-Push-System-IDs. Weit im negativen Bereich, damit sie
         * sich nicht mit Event-/Exam-IDs (positiv bzw. ab 10^9) überschneiden.
         */
        private const val GRADE_SYSTEM_ID_BASE = -100_000_000
    }
}

/**
 * Host-gebundener Cookie-Jar (nur `lsf.uni-hildesheim.de`). Bewusstes Duplikat zu
 * den Pendants in den LSF-Repos — sharing wäre den Refactor nicht wert.
 */
private class GradesHostCookieJar(private val host: String) : okhttp3.CookieJar {
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
abstract class GradesRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindGradesRepository(impl: GradesRepositoryImpl): GradesRepository
}
