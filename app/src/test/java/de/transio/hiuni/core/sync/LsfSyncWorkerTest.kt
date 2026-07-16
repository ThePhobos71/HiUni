package de.transio.hiuni.core.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.AuthRequiredException
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.core.notifications.data.NotificationLogEntity
import de.transio.hiuni.core.notifications.data.NotificationLogRepository
import de.transio.hiuni.feature.lsf.data.ExamsSyncResult
import de.transio.hiuni.feature.lsf.data.LsfExamsRepository
import de.transio.hiuni.feature.lsf.data.LsfMyCoursesRepository
import de.transio.hiuni.feature.lsf.data.LsfStundenplanRepository
import de.transio.hiuni.feature.lsf.data.MyCoursesSyncResult
import de.transio.hiuni.feature.lsf.data.StundenplanSyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant

/**
 * Tests für [LsfSyncWorker] — Fehler-Klassifikation (Retry vs. Fatal vs.
 * AuthRequired über die Cause-Kette) und der Exams-Scrape-Failure-Pfad mit
 * Push-Center-Dedup über EXAMS_SCRAPE_REF_KEY.
 *
 * Läuft als JVM-Unit-Test via Robolectric + work-testing
 * ([TestListenableWorkerBuilder]) — kein Emulator. Die Repositories sind
 * Interfaces und werden als relaxte mockk-Fakes injiziert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LsfSyncWorkerTest {

    private val myCourses = mockk<LsfMyCoursesRepository>(relaxed = true)
    private val stundenplan = mockk<LsfStundenplanRepository>(relaxed = true)
    private val exams = mockk<LsfExamsRepository>(relaxed = true)
    private val grades = mockk<de.transio.hiuni.feature.grades.data.GradesRepository>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val notificationLog = mockk<NotificationLogRepository>(relaxed = true)

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Default: alle drei Phasen erfolgreich — Tests überschreiben gezielt.
        coEvery { myCourses.sync() } returns AppResult.Success(OK_COURSES)
        coEvery { stundenplan.sync() } returns AppResult.Success(OK_PLAN)
        coEvery { exams.refresh(any()) } returns AppResult.Success(OK_EXAMS)
        coEvery { grades.refresh(any()) } returns AppResult.Success(OK_GRADES)
        // Push-Center: standardmäßig keine ungelesene Meldung vorhanden.
        every { notificationLog.observeRecent(any()) } returns
            MutableStateFlow(emptyList<NotificationLogEntity>())
    }

    private fun buildWorker(runAttemptCount: Int = 0): LsfSyncWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker =
                LsfSyncWorker(
                    appContext,
                    workerParameters,
                    myCourses,
                    stundenplan,
                    exams,
                    grades,
                    settings,
                    notificationLog
                )
        }
        return TestListenableWorkerBuilder<LsfSyncWorker>(context)
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(factory)
            .build()
    }

    // --- Erfolgs-Pfad -------------------------------------------------------

    @Test
    fun `alle Phasen erfolgreich liefert success und setzt Timestamps`() = runBlocking {
        val worker = buildWorker()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { settings.setLastLsfExamsRefreshEpoch(any()) }
        coVerify(exactly = 1) { settings.setLastLsfSyncEpoch(any()) }
    }

    // --- Klassifikation: Transient → retry ----------------------------------

    @Test
    fun `IOException beim MyCourses-Sync wird als transient klassifiziert und liefert retry`() =
        runBlocking {
            coEvery { myCourses.sync() } returns AppResult.Failure(IOException("no net"))
            val result = buildWorker().doWork()
            assertEquals(ListenableWorker.Result.retry(), result)
            coVerify(exactly = 0) { stundenplan.sync() }
        }

    @Test
    fun `SocketTimeoutException beim Stundenplan-Sync liefert retry`() = runBlocking {
        coEvery { stundenplan.sync() } returns AppResult.Failure(SocketTimeoutException("slow"))
        val result = buildWorker().doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `geworfene IOException statt Failure wird ebenfalls als transient klassifiziert`() =
        runBlocking {
            coEvery { myCourses.sync() } throws IOException("boom")
            val result = buildWorker().doWork()
            assertEquals(ListenableWorker.Result.retry(), result)
        }

    // --- Klassifikation: Fatal → failure ------------------------------------

    @Test
    fun `IllegalStateException beim MyCourses-Sync ist fatal und liefert failure`() = runBlocking {
        coEvery { myCourses.sync() } returns AppResult.Failure(IllegalStateException("scrape"))
        val result = buildWorker().doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    // --- Klassifikation: AuthRequired über Cause-Kette ----------------------

    @Test
    fun `direkte AuthRequiredException unter Threshold liefert retry statt failure`() = runBlocking {
        coEvery { myCourses.sync() } returns AppResult.Failure(AuthRequiredException("TGT weg"))
        // runAttemptCount 0 < AUTH_RETRY_THRESHOLD (2) → retry, KEINE Notification.
        val result = buildWorker(runAttemptCount = 0).doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { notificationLog.log(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `AuthRequiredException tief in der Cause-Kette wird erkannt`() = runBlocking {
        val wrapped = RuntimeException(
            "outer",
            RuntimeException("mid", AuthRequiredException("innerste Auth-Ursache"))
        )
        coEvery { myCourses.sync() } returns AppResult.Failure(wrapped)
        val titleSlot = slot<String>()
        val refKeySlot = slot<String>()
        coEvery {
            notificationLog.log(any(), capture(titleSlot), any(), capture(refKeySlot), any())
        } returns 1L
        // Ab Threshold erreicht → failure + Auth-Notification.
        val result = buildWorker(runAttemptCount = 2).doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 1) { notificationLog.log(any(), any(), any(), any(), any()) }
        assertEquals("LSF-Login abgelaufen", titleSlot.captured)
        assertEquals("lsf_auth_MyCourses", refKeySlot.captured)
    }

    @Test
    fun `AuthFailure ab AUTH_RETRY_THRESHOLD liefert failure und postet Auth-Notification`() =
        runBlocking {
            coEvery { stundenplan.sync() } returns AppResult.Failure(AuthRequiredException("weg"))
            val titleSlot = slot<String>()
            val refKeySlot = slot<String>()
            coEvery {
                notificationLog.log(any(), capture(titleSlot), any(), capture(refKeySlot), any())
            } returns 1L
            val result = buildWorker(runAttemptCount = 2).doWork()
            assertEquals(ListenableWorker.Result.failure(), result)
            coVerify(exactly = 1) { notificationLog.log(any(), any(), any(), any(), any()) }
            assertEquals("LSF-Login abgelaufen", titleSlot.captured)
            assertEquals("lsf_auth_Stundenplan", refKeySlot.captured)
        }

    // --- Exams-Scrape-Failure-Pfad (der neue Code) --------------------------

    @Test
    fun `fataler Exams-Scrape-Fehler bricht Gesamt-Sync NICHT ab und liefert success`() =
        runBlocking {
            coEvery { exams.refresh(any()) } returns AppResult.Failure(IllegalStateException("HTML changed"))
            val result = buildWorker().doWork()
            // MyCourses + Stundenplan waren ok → Worker meldet trotzdem Erfolg.
            assertEquals(ListenableWorker.Result.success(), result)
            // Exams-Timestamp bleibt alt: kein setLastLsfExamsRefreshEpoch.
            coVerify(exactly = 0) { settings.setLastLsfExamsRefreshEpoch(any()) }
            // Sync-Timestamp wird trotzdem gesetzt.
            coVerify(exactly = 1) { settings.setLastLsfSyncEpoch(any()) }
        }

    @Test
    fun `fataler Exams-Scrape-Fehler postet Push-Center-Meldung mit EXAMS_SCRAPE_REF_KEY`() =
        runBlocking {
            coEvery { exams.refresh(any()) } returns AppResult.Failure(IllegalStateException("boom"))
            val titleSlot = slot<String>()
            val refKeySlot = slot<String>()
            coEvery {
                notificationLog.log(any(), capture(titleSlot), any(), capture(refKeySlot), any())
            } returns 1L
            buildWorker().doWork()
            coVerify(exactly = 1) { notificationLog.log(any(), any(), any(), any(), any()) }
            assertEquals("Klausurtermine veraltet", titleSlot.captured)
            assertEquals("lsf_exams_scrape_failure", refKeySlot.captured)
        }

    @Test
    fun `Exams-Scrape-Dedup unterdrückt zweite Meldung bei bestehender ungelesener`() =
        runBlocking {
            coEvery { exams.refresh(any()) } returns AppResult.Failure(IllegalStateException("boom"))
            // Es liegt bereits eine ungelesene Meldung mit demselben refKey vor.
            every { notificationLog.observeRecent(any()) } returns MutableStateFlow(
                listOf(
                    NotificationLogEntity(
                        id = 7,
                        kind = NotificationKind.SYSTEM,
                        title = "Klausurtermine veraltet",
                        body = null,
                        firedAt = Instant.now(),
                        isRead = false,
                        refKey = "lsf_exams_scrape_failure"
                    )
                )
            )
            buildWorker().doWork()
            coVerify(exactly = 0) { notificationLog.log(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `Exams-Scrape-Dedup postet erneut wenn vorhandene Meldung bereits gelesen ist`() =
        runBlocking {
            coEvery { exams.refresh(any()) } returns AppResult.Failure(IllegalStateException("boom"))
            // Gleiche refKey, aber isRead = true → Dedup greift nicht, neue Meldung.
            every { notificationLog.observeRecent(any()) } returns MutableStateFlow(
                listOf(
                    NotificationLogEntity(
                        id = 7,
                        kind = NotificationKind.SYSTEM,
                        title = "Klausurtermine veraltet",
                        body = null,
                        firedAt = Instant.now(),
                        isRead = true,
                        refKey = "lsf_exams_scrape_failure"
                    )
                )
            )
            val refKeySlot = slot<String>()
            coEvery {
                notificationLog.log(any(), any(), any(), capture(refKeySlot), any())
            } returns 1L
            buildWorker().doWork()
            coVerify(exactly = 1) { notificationLog.log(any(), any(), any(), any(), any()) }
            assertEquals("lsf_exams_scrape_failure", refKeySlot.captured)
        }

    @Test
    fun `Exams-Scrape-Dedup ignoriert ungelesene Meldung mit anderem refKey`() = runBlocking {
        coEvery { exams.refresh(any()) } returns AppResult.Failure(IllegalStateException("boom"))
        every { notificationLog.observeRecent(any()) } returns MutableStateFlow(
            listOf(
                NotificationLogEntity(
                    id = 7,
                    kind = NotificationKind.SYSTEM,
                    title = "Irgendwas anderes",
                    body = null,
                    firedAt = Instant.now(),
                    isRead = false,
                    refKey = "lsf_auth_MyCourses"
                )
            )
        )
        val refKeySlot = slot<String>()
        coEvery {
            notificationLog.log(any(), any(), any(), capture(refKeySlot), any())
        } returns 1L
        buildWorker().doWork()
        coVerify(exactly = 1) { notificationLog.log(any(), any(), any(), any(), any()) }
        assertEquals("lsf_exams_scrape_failure", refKeySlot.captured)
    }

    @Test
    fun `transienter Exams-Fehler liefert retry und postet KEINE Scrape-Meldung`() = runBlocking {
        coEvery { exams.refresh(any()) } returns AppResult.Failure(IOException("timeout"))
        val result = buildWorker().doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { notificationLog.log(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `Fehler beim Push-Center-Log wird verschluckt und Worker meldet weiter success`() =
        runBlocking {
            coEvery { exams.refresh(any()) } returns AppResult.Failure(IllegalStateException("boom"))
            // observeRecent liefert einen Flow, der beim ersten Collect wirft.
            every { notificationLog.observeRecent(any()) } returns flow { throw RuntimeException("db down") }
            val result = buildWorker().doWork()
            // logExamsScrapeFailure kapselt in runCatching → Worker bleibt success.
            assertEquals(ListenableWorker.Result.success(), result)
        }

    private companion object {
        val OK_COURSES = MyCoursesSyncResult(
            imported = 0, updated = 0, pruned = 0, detailsFetched = 0, semester = "SoSe 26"
        )
        val OK_PLAN = StundenplanSyncResult(imported = 0, updated = 0, pruned = 0)
        val OK_EXAMS = ExamsSyncResult(
            imported = 0, updated = 0, pruned = 0, matched = 0, unmatched = 0,
            semesterCode = "20261"
        )
        val OK_GRADES = de.transio.hiuni.feature.grades.data.GradesSyncResult(
            imported = 0, updated = 0, pruned = 0, notified = 0
        )
    }
}
