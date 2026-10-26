package de.transio.hiuni.feature.email

import android.content.Context
import app.cash.turbine.test
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.search.PendingSearchSelection
import de.transio.hiuni.core.search.SearchFixtures
import de.transio.hiuni.core.search.SearchSelectionHolder
import de.transio.hiuni.core.security.CredentialsManager
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.email.data.EmailEntity
import de.transio.hiuni.feature.email.data.EmailRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Deep-Link aus der Spotlight-Suche in die Mail-Detailansicht. Die Treffer stammen aus
 * dem Posteingang, können aber älter als die 200 neuesten Mails der Liste sein. Das
 * Detail muss trotzdem aufgehen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmailViewModelSearchSelectionTest {

    private val repository = mockk<EmailRepository>(relaxed = true)
    private val credentials = mockk<CredentialsManager>(relaxed = true)
    private val calendarRepository = mockk<CalendarRepository>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val selectionHolder = SearchSelectionHolder()

    private val inboxFlow = MutableStateFlow<List<EmailEntity>>(emptyList())
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { credentials.hasCredentials() } returns false
        every { repository.observeSearch(any(), any()) } returns inboxFlow
        every { settings.mailSwipeRightAction } returns flowOf(MailSwipeAction.DEFAULT_RIGHT.storageKey)
        every { settings.mailSwipeLeftAction } returns flowOf(MailSwipeAction.DEFAULT_LEFT.storageKey)
        every { settings.mailRequiresBiometric } returns flowOf(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = EmailViewModel(
        repository, credentials, calendarRepository, settings, context, selectionHolder
    )

    @Test
    fun `vorgemerkte Mail oeffnet die Detailansicht im Posteingang`() = runTest {
        val email = SearchFixtures.email(rowId = 42L)
        inboxFlow.value = listOf(SearchFixtures.email(rowId = 1L, subject = "Andere"), email)
        selectionHolder.stage(PendingSearchSelection.Email(email))

        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals(EmailFolder.INBOX, s.folder)
            assertEquals(42L, s.selectedEmail?.rowId)
            assertEquals("Hallo", s.selectedBodyPlain)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(selectionHolder.pending.value)
    }

    @Test
    fun `alte Mail ausserhalb der Liste wird trotzdem geoeffnet`() = runTest {
        val old = SearchFixtures.email(rowId = 7L)
        inboxFlow.value = listOf(SearchFixtures.email(rowId = 1L, subject = "Neu"))
        selectionHolder.stage(PendingSearchSelection.Email(old))

        val vm = newVm()
        vm.state.test {
            advanceUntilIdle()
            assertEquals(old, expectMostRecentItem().selectedEmail)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `lebendes ViewModel im Gesendet-Ordner mit offener Suche springt in den Posteingang`() =
        runTest {
            val vm = newVm()
            vm.selectFolder(EmailFolder.SENT)
            vm.openSearch()
            vm.setSearchQuery("foo")
            val email = SearchFixtures.email(rowId = 42L)
            inboxFlow.value = listOf(email)

            selectionHolder.stage(PendingSearchSelection.Email(email))

            vm.state.test {
                advanceUntilIdle()
                val s = expectMostRecentItem()
                assertEquals(EmailFolder.INBOX, s.folder)
                assertFalse(s.isSearchOpen)
                assertEquals(42L, s.selectedEmail?.rowId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `geschlossene Mail oeffnet sich nach Neuaufbau nicht erneut`() = runTest {
        val email = SearchFixtures.email(rowId = 42L)
        inboxFlow.value = listOf(email)
        selectionHolder.stage(PendingSearchSelection.Email(email))
        val vm = newVm()
        advanceUntilIdle()

        vm.closeEmail()
        val recreated = newVm()

        vm.state.test {
            advanceUntilIdle()
            assertNull(expectMostRecentItem().selectedEmail)
            cancelAndIgnoreRemainingEvents()
        }
        recreated.state.test {
            advanceUntilIdle()
            assertNull(expectMostRecentItem().selectedEmail)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
