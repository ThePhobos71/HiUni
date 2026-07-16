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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests fürs Tickle-Routing im [MailPushSyncWorker]: Bei gesetztem
 * [MailPushSyncWorker.KEY_RUN_PREFETCH] (sync_tickle) wird nach dem Mail-Refresh
 * der [PrefetchOrchestrator] angestoßen; ohne Flag (mail_tickle) NICHT.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MailPushSyncWorkerTest {

    private val emailRepository = mockk<EmailRepository>(relaxed = true)
    private val credentials = mockk<CredentialsManager>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val prefetch = mockk<PrefetchOrchestrator>(relaxed = true)

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        every { settings.mailPushEnabled } returns MutableStateFlow(true)
        every { credentials.hasCredentials() } returns true
        coEvery { emailRepository.refresh(any()) } returns AppResult.Success(Unit)
    }

    private fun buildWorker(runPrefetch: Boolean?): MailPushSyncWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker =
                MailPushSyncWorker(
                    appContext,
                    workerParameters,
                    emailRepository,
                    credentials,
                    settings,
                    prefetch
                )
        }
        val builder = TestListenableWorkerBuilder<MailPushSyncWorker>(context)
            .setWorkerFactory(factory)
        if (runPrefetch != null) {
            builder.setInputData(workDataOf(MailPushSyncWorker.KEY_RUN_PREFETCH to runPrefetch))
        }
        return builder.build()
    }

    @Test
    fun `sync_tickle - Mail-Refresh UND Prefetch werden ausgeloest`() = runBlocking {
        val result = buildWorker(runPrefetch = true).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        coVerifyMailRefresh()
        verify(exactly = 1) { prefetch.prefetch() }
    }

    @Test
    fun `mail_tickle - nur Mail-Refresh, KEIN Prefetch`() = runBlocking {
        val result = buildWorker(runPrefetch = false).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        coVerifyMailRefresh()
        verify(exactly = 0) { prefetch.prefetch() }
    }

    @Test
    fun `fehlendes Flag verhaelt sich wie mail_tickle - kein Prefetch`() = runBlocking {
        buildWorker(runPrefetch = null).doWork()
        verify(exactly = 0) { prefetch.prefetch() }
    }

    @Test
    fun `kein Mail-Konto - weder Refresh noch Prefetch`() = runBlocking {
        every { credentials.hasCredentials() } returns false
        val result = buildWorker(runPrefetch = true).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        io.mockk.coVerify(exactly = 0) { emailRepository.refresh(any()) }
        verify(exactly = 0) { prefetch.prefetch() }
    }

    private fun coVerifyMailRefresh() {
        io.mockk.coVerify(exactly = 1) { emailRepository.refresh(force = true) }
    }
}
