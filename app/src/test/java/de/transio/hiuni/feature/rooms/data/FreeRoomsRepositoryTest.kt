package de.transio.hiuni.feature.rooms.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.transio.hiuni.core.common.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class FreeRoomsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: FreeRoomsStore
    private val source = FakeSource()

    /** Donnerstag, 12.11.2026, 13:42 in Berlin. */
    private var clock: Clock = Clock.fixed(Instant.parse("2026-11-12T12:42:00Z"), ZoneId.of("Europe/Berlin"))

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/raumfinder/$name")).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun repo() = FreeRoomsRepository(source, store, clock)

    @Before
    fun setUp() {
        val file = tmp.newFile("rooms.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher), produceFile = { file })
        store = FreeRoomsStore(dataStore, Json)
        source.roomList = fixture("raumliste.html")
        // SC.A.0.09 (rgid 2) hat Termine, SC.B.0.37 (rgid 245) ist leer.
        source.plans[2] = fixture("raumplan_woche.html")
        source.plans[245] = fixture("raumplan_leer.html")
        source.ics = fixture("sc_a_1_19_kw46.ics")
    }

    @Test
    fun `laedt nur Raeume des Campus, wertet heute aus und fragt die aktuelle Woche an`() = runTest(dispatcher) {
        val progress = mutableListOf<LoadProgress>()
        val result = repo().load(Campus.SAMELSON) { progress += it }

        val snap = (result as AppResult.Success).data
        assertEquals("2026-11-12", snap.date)
        assertEquals(listOf("SC.A.0.09", "SC.B.0.37"), snap.rooms.map { it.room.code })
        assertEquals(
            listOf(BusySpan(540, 600), BusySpan(660, 720), BusySpan(780, 840), BusySpan(960, 1080)),
            snap.rooms.first { it.room.rgid == 2 }.busy
        )
        assertEquals(emptyList<BusySpan>(), snap.rooms.first { it.room.rgid == 245 }.busy)
        assertEquals(setOf("46_2026"), source.requestedWeeks.toSet())
        assertEquals(LoadProgress(0, 2), progress.first())
        assertEquals(LoadProgress(2, 2), progress.last())
    }

    @Test
    fun `Stand gilt bis Tagesende und kommt danach nicht mehr aus dem Speicher`() = runTest(dispatcher) {
        repo().load(Campus.SAMELSON)
        val calls = source.calls.get()
        assertEquals("2026-11-12", repo().cached(Campus.SAMELSON)?.date)
        assertEquals(calls, source.calls.get())

        clock = Clock.fixed(Instant.parse("2026-11-12T22:59:00Z"), ZoneId.of("Europe/Berlin"))
        assertEquals("2026-11-12", repo().cached(Campus.SAMELSON)?.date)

        clock = Clock.fixed(Instant.parse("2026-11-12T23:01:00Z"), ZoneId.of("Europe/Berlin"))
        assertNull(repo().cached(Campus.SAMELSON))
    }

    @Test
    fun `Teilfehler - geladene Raeume bleiben, fehlende werden benannt und einzeln nachgeholt`() = runTest(dispatcher) {
        source.failing += 245
        val first = (repo().load(Campus.SAMELSON) as AppResult.Success).data
        assertEquals(listOf("SC.A.0.09"), first.rooms.map { it.room.code })
        assertEquals(listOf("SC.B.0.37"), first.missing.map { it.code })

        source.failing.clear()
        source.requestedRooms.clear()
        val second = (repo().retryMissing(Campus.SAMELSON) as AppResult.Success).data
        assertEquals(listOf(245), source.requestedRooms)
        assertEquals(listOf("SC.A.0.09", "SC.B.0.37"), second.rooms.map { it.room.code })
        assertTrue(second.missing.isEmpty())
        assertEquals(second, repo().cached(Campus.SAMELSON))
    }

    @Test
    fun `alles fehlgeschlagen ist ein Fehler und laesst den alten Stand stehen`() = runTest(dispatcher) {
        repo().load(Campus.SAMELSON)
        val before = repo().cached(Campus.SAMELSON)
        source.failing += listOf(2, 245)
        clock = Clock.fixed(Instant.parse("2026-11-12T13:30:00Z"), ZoneId.of("Europe/Berlin"))

        val result = repo().load(Campus.SAMELSON)
        assertTrue(result is AppResult.Failure)
        assertEquals(before, repo().cached(Campus.SAMELSON))
    }

    @Test
    fun `erreichbare Raumliste ohne Raeume ist ein Datenfehler, kein Verbindungsfehler`() = runTest(dispatcher) {
        source.roomList = "<html><body>nichts</body></html>"

        val result = repo().load(Campus.SAMELSON)

        assertTrue((result as AppResult.Failure).error is RoomDataException)
    }

    @Test
    fun `Raumliste nicht erreichbar ist ein Fehler`() = runTest(dispatcher) {
        source.roomListFails = true
        assertTrue(repo().load(Campus.SAMELSON) is AppResult.Failure)
        assertNull(repo().cached(Campus.SAMELSON))
    }

    @Test
    fun `hoechstens vier Abrufe gleichzeitig`() = runTest(dispatcher) {
        val many = (1..10).joinToString("\n") { i ->
            """<a class="regular" href="rds?raum.rgid=${1000 + i}&x=1">SC.Z.0.$i (Seminarraum) -</a>"""
        }
        source.roomList = "<html><body>$many</body></html>"
        (1..10).forEach { source.plans[1000 + it] = fixture("raumplan_leer.html") }
        source.planDelayMs = 50

        val snap = (repo().load(Campus.SAMELSON) as AppResult.Success).data
        assertEquals(10, snap.rooms.size)
        assertEquals(FreeRoomsRepository.MAX_PARALLEL, source.maxConcurrent)
    }

    @Test
    fun `gemerkter Campus und Aufraeumen alter Staende`() = runTest(dispatcher) {
        assertNull(store.selectedCampus.first())
        store.setCampus(Campus.HAUPTCAMPUS)
        assertEquals(Campus.HAUPTCAMPUS, store.selectedCampus.first())

        store.save(CampusSnapshot(Campus.HAUPTCAMPUS, "2026-11-11", 0, emptyList()))
        store.save(CampusSnapshot(Campus.SAMELSON, "2026-11-12", 0, emptyList()))
        assertNull(store.snapshot(Campus.HAUPTCAMPUS))
        assertEquals("2026-11-12", store.snapshot(Campus.SAMELSON)?.date)
    }

    @Test
    fun `LSF-Wochenschluessel nach ISO ohne fuehrende Null`() {
        assertEquals("46_2026", FreeRoomsRepository.lsfWeek(LocalDate.of(2026, 11, 12)))
        assertEquals("53_2026", FreeRoomsRepository.lsfWeek(LocalDate.of(2027, 1, 1)))
        assertEquals("1_2027", FreeRoomsRepository.lsfWeek(LocalDate.of(2027, 1, 4)))
    }

    private class FakeSource : LsfRoomSource {
        var roomList = ""
        var roomListFails = false
        val plans = mutableMapOf<Int, String>()
        var ics = ""
        val failing = mutableSetOf<Int>()
        val requestedWeeks = mutableListOf<String>()
        val requestedRooms = mutableListOf<Int>()
        val calls = AtomicInteger(0)
        var planDelayMs = 0L
        private var concurrent = 0
        var maxConcurrent = 0

        override suspend fun fetchRoomList(): String {
            calls.incrementAndGet()
            if (roomListFails) throw IOException("offline")
            return roomList
        }

        override suspend fun fetchWeekPlan(rgid: Int, week: String): String {
            calls.incrementAndGet()
            requestedWeeks += week
            requestedRooms += rgid
            concurrent++
            maxConcurrent = maxOf(maxConcurrent, concurrent)
            try {
                if (planDelayMs > 0) delay(planDelayMs)
                if (rgid in failing) throw IOException("HTTP 503")
                return plans[rgid] ?: throw IOException("unbekannt")
            } finally {
                concurrent--
            }
        }

        override suspend fun fetchText(url: String): String {
            calls.incrementAndGet()
            return ics
        }
    }
}
