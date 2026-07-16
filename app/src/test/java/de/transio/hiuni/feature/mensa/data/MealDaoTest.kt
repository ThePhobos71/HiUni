package de.transio.hiuni.feature.mensa.data

import de.transio.hiuni.core.database.AppDatabase
import de.transio.hiuni.core.database.newInMemoryDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * DAO-Tests für [MealDao]. Schwerpunkt ist die [MealDao.replaceWindow]-Transaktion
 * (der Sync-Kern: altes Fenster weg, neue Daten rein, Nachbar-Tage außerhalb des
 * Fensters bleiben) sowie die Prune- und Range-Grenzen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MealDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MealDao

    private val loc = 1

    private fun meal(
        sourceId: String,
        date: LocalDate,
        name: String = sourceId,
        locationId: Int = loc,
        category: String = "Hauptgericht"
    ) = MealEntity(
        sourceId = sourceId,
        locationId = locationId,
        date = date,
        category = category,
        name = name,
        description = null,
        priceStudentCents = 250,
        priceEmployeeCents = 350,
        priceGuestCents = 450,
        tags = ""
    )

    @Before
    fun setUp() {
        db = newInMemoryDatabase()
        dao = db.mealDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- replaceWindow ---------------------------------------------------------

    @Test
    fun `replaceWindow löscht alte Zeilen im Fenster und ersetzt sie durch neue`() = runTest {
        val from = LocalDate.of(2026, 7, 13)
        val to = LocalDate.of(2026, 7, 17)
        dao.upsertAll(listOf(meal("alt-1", LocalDate.of(2026, 7, 14), name = "Altes Gericht")))

        dao.replaceWindow(
            loc, from, to,
            listOf(meal("neu-1", LocalDate.of(2026, 7, 14), name = "Neues Gericht"))
        )

        val names = dao.observeRange(from, to, loc).first().map { it.name }
        assertEquals(listOf("Neues Gericht"), names)
    }

    @Test
    fun `replaceWindow lässt Tage außerhalb des Fensters unangetastet`() = runTest {
        val from = LocalDate.of(2026, 7, 13)
        val to = LocalDate.of(2026, 7, 17)
        // Nachbar-Tage links und rechts vom Fenster.
        dao.upsertAll(
            listOf(
                meal("vorher", LocalDate.of(2026, 7, 12)),
                meal("nachher", LocalDate.of(2026, 7, 18))
            )
        )

        dao.replaceWindow(loc, from, to, listOf(meal("im-fenster", LocalDate.of(2026, 7, 15))))

        val all = dao.observeAvailableDates(loc, LocalDate.of(2026, 1, 1)).first()
        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 12),
                LocalDate.of(2026, 7, 15),
                LocalDate.of(2026, 7, 18)
            ),
            all
        )
    }

    @Test
    fun `replaceWindow löscht Fenster-Grenztage inklusive (BETWEEN)`() = runTest {
        val from = LocalDate.of(2026, 7, 13)
        val to = LocalDate.of(2026, 7, 17)
        dao.upsertAll(
            listOf(
                meal("am-from", from),
                meal("am-to", to)
            )
        )

        // Leeres Ersatz-Fenster: beide Grenztage müssen weg sein.
        dao.replaceWindow(loc, from, to, emptyList())

        assertNull(dao.findById("am-from", loc))
        assertNull(dao.findById("am-to", loc))
    }

    @Test
    fun `replaceWindow isoliert nach locationId`() = runTest {
        val from = LocalDate.of(2026, 7, 13)
        val to = LocalDate.of(2026, 7, 17)
        val otherLoc = 2
        dao.upsertAll(listOf(meal("loc2", LocalDate.of(2026, 7, 14), locationId = otherLoc)))

        dao.replaceWindow(loc, from, to, listOf(meal("loc1", LocalDate.of(2026, 7, 14))))

        // Location 2 bleibt trotz überlappendem Datum erhalten.
        assertEquals("loc2", dao.findById("loc2", otherLoc)?.sourceId)
    }

    @Test
    fun `upsertAll überschreibt bei gleichem zusammengesetztem Primärschlüssel`() = runTest {
        val d = LocalDate.of(2026, 7, 14)
        dao.upsertAll(listOf(meal("x", d, name = "erst")))
        dao.upsertAll(listOf(meal("x", d, name = "danach")))

        assertEquals("danach", dao.findById("x", loc)?.name)
    }

    // --- pruneOlderThan --------------------------------------------------------

    @Test
    fun `pruneOlderThan löscht strikt vor before und behält den Grenztag`() = runTest {
        val before = LocalDate.of(2026, 7, 14)
        dao.upsertAll(
            listOf(
                meal("gestern", before.minusDays(1)),
                meal("grenztag", before),
                meal("morgen", before.plusDays(1))
            )
        )

        dao.pruneOlderThan(loc, before)

        // `date < before` -> Grenztag (==before) bleibt erhalten.
        assertNull(dao.findById("gestern", loc))
        assertEquals("grenztag", dao.findById("grenztag", loc)?.sourceId)
        assertEquals("morgen", dao.findById("morgen", loc)?.sourceId)
    }

    // --- observeRange ----------------------------------------------------------

    @Test
    fun `observeRange sortiert nach date dann category dann name`() = runTest {
        val d1 = LocalDate.of(2026, 7, 14)
        val d2 = LocalDate.of(2026, 7, 15)
        dao.upsertAll(
            listOf(
                meal("b", d1, name = "Bravo", category = "Suppe"),
                meal("a", d1, name = "Alpha", category = "Hauptgericht"),
                meal("c", d2, name = "Charlie", category = "Hauptgericht")
            )
        )

        val ordered = dao.observeRange(d1, d2, loc).first().map { it.name }

        // d1 zuerst; innerhalb d1 category ASC (Hauptgericht < Suppe).
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), ordered)
    }
}
