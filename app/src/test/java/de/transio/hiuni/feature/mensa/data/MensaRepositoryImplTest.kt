package de.transio.hiuni.feature.mensa.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.datastore.SettingsDataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime

/**
 * Hinweise und Öffnungszeiten waren bisher nur im Speicher: nach einem Prozess-Neustart
 * innerhalb der 6-h-TTL fehlten sie ganz, nach abgelaufener TTL ploppten sie erst nach
 * dem Netz-Call auf. Diese Tests sichern ab, dass sie aus dem persistenten Cache kommen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MensaRepositoryImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val dao = mockk<MealDao>(relaxed = true)
    private val api = mockk<MensaApiService>()
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private lateinit var extras: MensaExtrasStore

    private val today = LocalDate.now()
    private val announcement = Announcement(
        date = today,
        text = "Heute nur Ausgabe 1 geöffnet",
        time = AnnouncementTime.NOON,
        lane = null
    )
    private val hours = listOf(
        OpeningHourBlock("noon", 1, 5, LocalTime.of(11, 30), LocalTime.of(14, 15))
    )

    @Before
    fun setUp() {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher),
            produceFile = { tmp.newFile("mensa_repo.preferences_pb") }
        )
        extras = MensaExtrasStore(dataStore)
        every { settings.mensaLocationId } returns flowOf(150)
        every { settings.lastMensaRefreshEpoch } returns flowOf(0L)
        coEvery { api.fetchMenu(any(), any(), any()) } returns MensaApiService.FetchResult(
            meals = emptyList(),
            announcements = listOf(announcement),
            openingHours = hours
        )
    }

    private fun newRepository() = MensaRepositoryImpl(dao, api, settings, extras)

    @Test
    fun `Hinweise sind nach Neustart ohne Netz-Call sofort da`() = runTest(dispatcher) {
        assertTrue(newRepository().refresh(force = true) is AppResult.Success)

        // Neue Instanz = Prozess-Neustart; kein refresh() aufgerufen.
        val restarted = newRepository()
        assertEquals(listOf(announcement), restarted.observeAnnouncements(today).first())
    }

    @Test
    fun `Oeffnungszeiten sind nach Neustart ohne Netz-Call sofort da`() = runTest(dispatcher) {
        newRepository().refresh(force = true)

        assertEquals(hours, newRepository().observeOpeningHours().first())
    }

    @Test
    fun `fehlgeschlagener Refresh laesst gecachte Hinweise stehen`() = runTest(dispatcher) {
        val repo = newRepository()
        repo.refresh(force = true)
        coEvery { api.fetchMenu(any(), any(), any()) } throws IOException("offline")

        assertTrue(repo.refresh(force = true) is AppResult.Failure)
        assertEquals(listOf(announcement), repo.observeAnnouncements(today).first())
    }
}
