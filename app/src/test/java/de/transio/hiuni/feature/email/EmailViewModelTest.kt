package de.transio.hiuni.feature.email

import android.content.Context
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.search.SearchSelectionHolder
import de.transio.hiuni.core.security.CredentialsManager
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.email.data.EmailRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pop-in im Postfach: vor der ersten Room-Emission darf weder „Kein Zugang" noch
 * „Postfach leer" aufblitzen, und die erste Liste darf nicht am 200-ms-Debounce
 * der Suche hängen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmailViewModelTest {

    private val repo = mockk<EmailRepository>(relaxed = true)
    private val credentials = mockk<CredentialsManager>(relaxed = true)
    private val calendar = mockk<CalendarRepository>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { credentials.hasCredentials() } returns true
        every { repo.observeSearch(any(), any()) } returns flowOf(emptyList())
        every { settings.mailSwipeRightAction } returns flowOf("")
        every { settings.mailSwipeLeftAction } returns flowOf("")
        every { settings.mailRequiresBiometric } returns flowOf(false)
        coEvery { repo.refresh(any()) } returns AppResult.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = EmailViewModel(repo, credentials, calendar, settings, context, SearchSelectionHolder())

    @Test
    fun `solange Room noch nicht geliefert hat, gilt das Postfach als nicht geladen`() = runTest {
        every { repo.observeSearch(any(), any()) } returns MutableSharedFlow()
        val vm = newVm()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertFalse(vm.state.value.isContentLoaded)
        job.cancel()
    }

    @Test
    fun `erste Liste kommt ohne Warten auf den Such-Debounce`() = runTest {
        val vm = newVm()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        // Keine virtuelle Zeit vorgespult: der leere Initial-Query darf nicht entprellt werden.
        assertTrue(vm.state.value.isContentLoaded)
        assertTrue(vm.state.value.hasCredentials)
        job.cancel()
    }
}
