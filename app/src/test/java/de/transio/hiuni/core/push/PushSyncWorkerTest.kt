package de.transio.hiuni.core.push

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.security.CredentialsManager
import de.transio.hiuni.core.sync.PrefetchOrchestrator
import de.transio.hiuni.feature.email.data.EmailRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests fürs Tickle-Routing im [PushSyncWorker]: [PushSyncWorker.KEY_RUN_MAIL]
 * steuert den Mail-Refresh (nur mit Mail-Konto), [PushSyncWorker.KEY_RUN_PREFETCH]
 * den Feature-Prefetch, auf dessen Job der Worker wartet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PushSyncWorkerTest {

    private val emailRepository = mockk<EmailRepository>(relaxed = true)
    private val credentials = mockk<CredentialsManager>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val prefetch = mockk<PrefetchOrchestrator>(relaxed = true)

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        every { settings.pushSyncEnabled } returns MutableStateFlow(true)
        every { credentials.hasCredentials() } returns true
        coEvery { emailRepository.refresh(any()) } returns AppResult.Success(Unit)
        every { prefetch.prefetch() } returns null
    }

    private fun buildWorker(runMail: Boolean?, runPrefetch: Boolean?): PushSyncWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker =
                PushSyncWorker(
                    appContext,
                    workerParameters,
                    emailRepository,
                    credentials,
                    settings,
                    prefetch
                )
        }
        val input = buildList {
            if (runMail != null) add(PushSyncWorker.KEY_RUN_MAIL to runMail)
            if (runPrefetch != null) add(PushSyncWorker.KEY_RUN_PREFETCH to runPrefetch)
        }
        return TestListenableWorkerBuilder<PushSyncWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(*input.toTypedArray()))
            .build()
    }

    @Test
    fun `sync_tickle - Mail-Refresh UND Prefetch werden ausgeloest`() = runBlocking {
        val result = buildWorker(runMail = true, runPrefetch = true).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        coVerifyMailRefresh(times = 1)
        verify(exactly = 1) { prefetch.prefetch() }
    }

    @Test
    fun `mail_tickle - nur Mail-Refresh, KEIN Prefetch`() = runBlocking {
        val result = buildWorker(runMail = true, runPrefetch = false).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        coVerifyMailRefresh(times = 1)
        verify(exactly = 0) { prefetch.prefetch() }
    }

    @Test
    fun `fehlende Flags - weder Refresh noch Prefetch`() = runBlocking {
        buildWorker(runMail = null, runPrefetch = null).doWork()
        coVerifyMailRefresh(times = 0)
        verify(exactly = 0) { prefetch.prefetch() }
    }

    @Test
    fun `kein Mail-Konto - Prefetch laeuft trotzdem, Mail nicht`() = runBlocking {
        every { credentials.hasCredentials() } returns false
        val result = buildWorker(runMail = true, runPrefetch = true).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        coVerifyMailRefresh(times = 0)
        verify(exactly = 1) { prefetch.prefetch() }
    }

    @Test
    fun `Feature aus - weder Refresh noch Prefetch`() = runBlocking {
        every { settings.pushSyncEnabled } returns MutableStateFlow(false)
        val result = buildWorker(runMail = true, runPrefetch = true).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        coVerifyMailRefresh(times = 0)
        verify(exactly = 0) { prefetch.prefetch() }
    }

    @Test
    fun `Worker wartet auf den Prefetch-Job`() = runBlocking {
        val prefetchJob = Job()
        every { prefetch.prefetch() } returns prefetchJob
        val work = async { buildWorker(runMail = false, runPrefetch = true).doWork() }
        repeat(5) { yield() }
        assertFalse("Worker darf vor dem Prefetch nicht fertig sein", work.isCompleted)
        prefetchJob.complete()
        assertEquals(ListenableWorker.Result.success(), work.await())
    }

    @Test
    fun `transienter Mail-Fehler - retry, Prefetch laeuft trotzdem`() = runBlocking {
        coEvery { emailRepository.refresh(any()) } returns AppResult.Failure(java.io.IOException("offline"))
        val result = buildWorker(runMail = true, runPrefetch = true).doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
        verify(exactly = 1) { prefetch.prefetch() }
    }

    private fun coVerifyMailRefresh(times: Int) {
        io.mockk.coVerify(exactly = times) { emailRepository.refresh(force = true) }
    }
}
