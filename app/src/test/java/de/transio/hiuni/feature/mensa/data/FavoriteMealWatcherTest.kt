package de.transio.hiuni.feature.mensa.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationPresenter
import de.transio.hiuni.core.notifications.data.NotificationKind
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Verdrahtung von [FavoriteMealWatcher]: Stichwörter und Gemeldetes aus dem echten
 * [FavoriteMealStore], Speiseplan aus dem [MealDao], Meldung über den
 * [NotificationPresenter].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteMealWatcherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val dao = mockk<MealDao>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val presenter = mockk<NotificationPresenter>(relaxed = true)
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 11, 16) // Montag
    private val thu = LocalDate.of(2026, 11, 19)
    private val clock = Clock.fixed(LocalDateTime.of(today, LocalTime.of(9, 0)).atZone(zone).toInstant(), zone)
    private lateinit var store: FavoriteMealStore

    private fun meal(name: String, date: LocalDate) = MealEntity(
        sourceId = "$name-$date",
        locationId = 150,
        date = date,
        category = "Mittag",
        name = name,
        description = null,
        priceStudentCents = null,
        priceEmployeeCents = null,
        priceGuestCents = null,
        tags = ""
    )

    @Before
    fun setUp() {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher),
            produceFile = { tmp.newFile("favorite_watcher.preferences_pb") }
        )
        store = FavoriteMealStore(dataStore)
        every { settings.mensaLocationId } returns flowOf(150)
    }

    private fun planContains(vararg meals: MealEntity) {
        every { dao.observeRange(today, today.plusDays(13), 150) } returns flowOf(meals.toList())
    }

    private fun watcher() = FavoriteMealWatcher(store, dao, settings, presenter, clock)

    @Test
    fun `neuer Treffer wird einmal gemeldet`() = runTest(dispatcher) {
        store.addKeyword("Currywurst")
        planContains(meal("Currywurst mit Pommes", thu))
        val watcher = watcher()

        watcher.check()
        watcher.check()

        coVerify(exactly = 1) {
            presenter.present(
                NotificationKind.MENSA,
                "Currywurst am Donnerstag",
                "Currywurst mit Pommes",
                any(),
                FavoriteMealWatcher.NOTIFICATION_ID,
                any()
            )
        }
    }

    @Test
    fun `notify false meldet nicht und unterdrueckt die spaetere Meldung`() = runTest(dispatcher) {
        store.addKeyword("Currywurst")
        planContains(meal("Currywurst", thu))
        val watcher = watcher()

        watcher.check(notify = false)
        watcher.check()

        coVerify(exactly = 0) { presenter.present(any(), any(), any(), any(), any(), any()) }
        assertEquals(setOf("150|currywurst|2026-11-19"), store.notifiedKeys())
    }

    @Test
    fun `ohne Stichwoerter passiert nichts und Gemeldetes wird geleert`() = runTest(dispatcher) {
        store.setNotifiedKeys(setOf("x"))
        planContains(meal("Currywurst", thu))

        watcher().check()

        assertTrue(store.notifiedKeys().isEmpty())
        coVerify(exactly = 0) { presenter.present(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `kein Treffer im Plan meldet nichts`() = runTest(dispatcher) {
        store.addKeyword("Currywurst")
        planContains(meal("Lasagne", thu))

        watcher().check()

        coVerify(exactly = 0) { presenter.present(any(), any(), any(), any(), any(), any()) }
    }
}
