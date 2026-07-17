package de.transio.hiuni.feature.grades.data

import de.transio.hiuni.core.auth.CasCookieStore
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationPresenter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fokus-Tests für den neuen TTL-Gate in [GradesRepositoryImpl.refresh]:
 *  - frischer Cache + `force=false` → KEIN LSF-Roundtrip (kein `getServiceTicket`).
 *  - stale Cache + `force=false` → LSF-Roundtrip startet (getServiceTicket wird
 *    aufgerufen; danach schlägt der gemockte Netz-Pfad fehl, das ist ok — uns
 *    interessiert nur, DASS der Roundtrip überhaupt beginnt).
 *  - `force=true` → immer Roundtrip, egal wie frisch der Cache ist.
 *
 * Der eigentliche LSF-/Scraping-Pfad wird bewusst NICHT durchgespielt (er hängt an
 * echten HTTP-Roundtrips); wir prüfen ausschließlich das Gate am Methodenanfang.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GradesRepositoryTtlTest {

    private val casSession = mockk<CasSession>(relaxed = true)
    private val cookieStore = mockk<CasCookieStore>(relaxed = true)
    private val httpClient = OkHttpClient()
    private val gradeDao = mockk<GradeDao>(relaxed = true)
    private val courseDao = mockk<de.transio.hiuni.feature.courses.data.CourseDao>(relaxed = true)
    private val scraper = mockk<NotenspiegelScraper>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val presenter = mockk<NotificationPresenter>(relaxed = true)

    private fun newRepo() = GradesRepositoryImpl(
        casSession = casSession,
        cookieStore = cookieStore,
        httpClient = httpClient,
        gradeDao = gradeDao,
        courseDao = courseDao,
        scraper = scraper,
        settings = settings,
        presenter = presenter,
        // Unconfined, damit withContext(io) im Test eager durchläuft — ein frischer
        // StandardTestDispatcher würde nie advanced und refresh() bliebe hängen.
        io = UnconfinedTestDispatcher()
    )

    @Test
    fun `frischer Cache und force false ueberspringt den LSF-Roundtrip`() = runTest {
        // Vor 1 Minute gesynct → jünger als TTL → skip.
        every { settings.lastGradesRefreshEpoch } returns flowOf(System.currentTimeMillis() - 60_000)

        val result = newRepo().refresh(force = false)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 0) { casSession.getServiceTicket(any()) }
    }

    @Test
    fun `staler Cache und force false startet den LSF-Roundtrip`() = runTest {
        // Vor 7h gesynct → älter als TTL (6h) → Roundtrip.
        every { settings.lastGradesRefreshEpoch } returns
            flowOf(System.currentTimeMillis() - 7L * 60 * 60 * 1000)
        // getServiceTicket wirft → der Roundtrip beginnt, scheitert dann harmlos.
        coEvery { casSession.getServiceTicket(any()) } throws RuntimeException("no ticket")

        newRepo().refresh(force = false)

        coVerify(exactly = 1) { casSession.getServiceTicket(any()) }
    }

    @Test
    fun `force true erzwingt den Roundtrip trotz frischem Cache`() = runTest {
        every { settings.lastGradesRefreshEpoch } returns flowOf(System.currentTimeMillis())
        coEvery { casSession.getServiceTicket(any()) } throws RuntimeException("no ticket")

        newRepo().refresh(force = true)

        coVerify(exactly = 1) { casSession.getServiceTicket(any()) }
    }
}
