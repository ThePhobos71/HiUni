package de.transio.hiuni.feature.calendar.data

import de.transio.hiuni.core.database.AppDatabase
import de.transio.hiuni.core.database.newInMemoryDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * DAO-Tests für [CustomEventDao] gegen eine echte In-Memory-Room-DB.
 *
 * Schwerpunkte: der Learnweb-Merge-Key (sourceKind + sourceReference), die
 * Prune-Semantik (NOT IN keep) und die Range-Query an den Grenzen. Das ist der
 * Regressionsschutz für den Learnweb-Kalender-Sync
 * ([de.transio.hiuni.feature.learnweb.data.LearnwebCalendarSync]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class CustomEventDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CustomEventDao

    private fun event(
        title: String,
        start: Instant,
        end: Instant = start.plusSeconds(3600),
        sourceKind: String = CustomEventEntity.SOURCE_USER,
        sourceReference: String? = null
    ) = CustomEventEntity(
        id = 0L,
        title = title,
        startTime = start,
        endTime = end,
        sourceKind = sourceKind,
        sourceReference = sourceReference
    )

    @Before
    fun setUp() {
        db = newInMemoryDatabase()
        dao = db.customEventDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- findBySourceReference: der Learnweb-Merge-Key -------------------------

    @Test
    fun `findBySourceReference matcht exakt über sourceKind plus sourceReference`() = runTest {
        val t = Instant.parse("2026-07-16T10:00:00Z")
        dao.insert(
            event(
                "Abgabe Statistik",
                t,
                sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ICAL,
                sourceReference = "event_4875@moodle"
            )
        )

        val found = dao.findBySourceReference(
            CustomEventEntity.SOURCE_LEARNWEB_ICAL,
            "event_4875@moodle"
        )

        assertEquals("Abgabe Statistik", found?.title)
    }

    @Test
    fun `findBySourceReference unterscheidet nach sourceKind bei gleicher Referenz`() = runTest {
        val t = Instant.parse("2026-07-16T10:00:00Z")
        // Dieselbe Referenz-ID unter zwei Quellen — Scraper (ASSIGNMENT) und Feed (ICAL)
        // laufen laut Doku parallel und dürfen NICHT gegenseitig gemerged werden.
        dao.insert(
            event("Scraper", t, sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT, sourceReference = "42")
        )
        dao.insert(
            event("Feed", t, sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ICAL, sourceReference = "42")
        )

        val scraper = dao.findBySourceReference(CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT, "42")
        val feed = dao.findBySourceReference(CustomEventEntity.SOURCE_LEARNWEB_ICAL, "42")

        assertEquals("Scraper", scraper?.title)
        assertEquals("Feed", feed?.title)
    }

    @Test
    fun `findBySourceReference liefert null wenn Referenz unbekannt`() = runTest {
        dao.insert(event("Egal", Instant.parse("2026-07-16T10:00:00Z")))

        val found = dao.findBySourceReference(CustomEventEntity.SOURCE_LEARNWEB_ICAL, "gibtsnicht")

        assertNull(found)
    }

    @Test
    fun `findBySourceReference matcht auch Titel mit Emoji (Migrations-Regression)`() = runTest {
        // Regressionsschutz für die heutige Emoji-Titel-Migration: der Merge-Key ist
        // sourceReference, NICHT der Titel — ein umgeschriebener Titel darf den
        // Wiederfund über die Referenz nicht brechen.
        val t = Instant.parse("2026-07-16T10:00:00Z")
        dao.insert(
            event(
                "📚 Abgabe",
                t,
                sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ICAL,
                sourceReference = "ev1"
            )
        )

        val found = dao.findBySourceReference(CustomEventEntity.SOURCE_LEARNWEB_ICAL, "ev1")

        assertEquals("ev1", found?.sourceReference)
    }

    // --- sourceReferencesFor ---------------------------------------------------

    @Test
    fun `sourceReferencesFor liefert nur Referenzen der passenden Quelle`() = runTest {
        val t = Instant.parse("2026-07-16T10:00:00Z")
        dao.insert(event("a", t, sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ICAL, sourceReference = "r1"))
        dao.insert(event("b", t, sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ICAL, sourceReference = "r2"))
        dao.insert(event("c", t, sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT, sourceReference = "x"))
        // USER-Event ohne Referenz darf NICHT auftauchen (Query filtert NOT NULL).
        dao.insert(event("user", t))

        val refs = dao.sourceReferencesFor(CustomEventEntity.SOURCE_LEARNWEB_ICAL)

        assertEquals(setOf("r1", "r2"), refs.toSet())
    }

    // --- pruneBySourceKind -----------------------------------------------------

    @Test
    fun `pruneBySourceKind löscht nur Events der Quelle die nicht in keep sind`() = runTest {
        val t = Instant.parse("2026-07-16T10:00:00Z")
        dao.insert(event("keep", t, sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ICAL, sourceReference = "keep"))
        dao.insert(event("drop", t, sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ICAL, sourceReference = "drop"))
        // Andere Quelle bleibt komplett unberührt, obwohl ihre Referenz nicht in keep steht.
        dao.insert(event("other", t, sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT, sourceReference = "drop"))
        // USER-Event bleibt ebenfalls unberührt.
        dao.insert(event("user", t))

        dao.pruneBySourceKind(CustomEventEntity.SOURCE_LEARNWEB_ICAL, listOf("keep"))

        val titles = dao.observeAll().first().map { it.title }.toSet()
        assertEquals(setOf("keep", "other", "user"), titles)
    }

    @Test
    fun `pruneBySourceKind mit keep-Liste ohne Treffer löscht alle Events der Quelle`() = runTest {
        val t = Instant.parse("2026-07-16T10:00:00Z")
        dao.insert(event("a", t, sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ICAL, sourceReference = "a"))
        dao.insert(event("b", t, sourceKind = CustomEventEntity.SOURCE_LEARNWEB_ICAL, sourceReference = "b"))
        dao.insert(event("user", t))

        // Sentinel-keep (kein existierender Treffer): alle ICAL-Events fliegen raus,
        // USER bleibt. Bewusst NICHT `emptyList()` — die Produktion ruft pruneBySourcekind
        // nie mit leerer keep-Liste (LearnwebCalendarSign guarded: `if keepRefs.isEmpty() return`),
        // und Room rendert `NOT IN ()` als ungültiges SQLite. Siehe Bug-Report-Test unten.
        dao.pruneBySourceKind(CustomEventEntity.SOURCE_LEARNWEB_ICAL, listOf("__none__"))

        val titles = dao.observeAll().first().map { it.title }
        assertEquals(listOf("user"), titles)
    }

    // --- observeRange: Grenzfälle ----------------------------------------------

    @Test
    fun `observeRange schließt Events exakt an beiden Range-Grenzen ein (BETWEEN inklusive)`() = runTest {
        val from = Instant.parse("2026-05-24T00:00:00Z")
        val to = Instant.parse("2026-05-31T00:00:00Z")
        dao.insert(event("am from-Rand", from))
        dao.insert(event("am to-Rand", to))
        dao.insert(event("kurz vor from", from.minusMillis(1)))
        dao.insert(event("kurz nach to", to.plusMillis(1)))

        val inRange = dao.observeRange(from.toEpochMilli(), to.toEpochMilli()).first()

        assertEquals(setOf("am from-Rand", "am to-Rand"), inRange.map { it.title }.toSet())
    }

    @Test
    fun `observeRange sortiert nach startTime aufsteigend`() = runTest {
        val from = Instant.parse("2026-05-24T00:00:00Z")
        val to = Instant.parse("2026-05-31T00:00:00Z")
        dao.insert(event("spät", Instant.parse("2026-05-30T00:00:00Z")))
        dao.insert(event("früh", Instant.parse("2026-05-25T00:00:00Z")))
        dao.insert(event("mitte", Instant.parse("2026-05-27T00:00:00Z")))

        val titles = dao.observeRange(from.toEpochMilli(), to.toEpochMilli()).first().map { it.title }

        assertEquals(listOf("früh", "mitte", "spät"), titles)
    }

    @Test
    fun `observeRecurringMastersUntil liefert nur Master mit Rule und Start bis toMillis`() = runTest {
        val to = Instant.parse("2026-05-31T00:00:00Z")
        val withRule = event("weekly", Instant.parse("2026-05-20T00:00:00Z")).copy(
            recurrenceRule = "{\"freq\":\"WEEKLY\",\"interval\":1}"
        )
        dao.insert(withRule)
        // Ohne Rule -> ignoriert.
        dao.insert(event("einmalig", Instant.parse("2026-05-20T00:00:00Z")))
        // Rule aber Start nach to -> ignoriert.
        dao.insert(
            event("später Master", to.plusSeconds(3600)).copy(
                recurrenceRule = "{\"freq\":\"DAILY\",\"interval\":1}"
            )
        )

        val masters = dao.observeRecurringMastersUntil(to.toEpochMilli()).first()

        assertEquals(listOf("weekly"), masters.map { it.title })
    }

    @Test
    fun `findNextEvent liefert das nächste Event ab now und ignoriert vergangene`() = runTest {
        val now = Instant.parse("2026-07-16T12:00:00Z")
        dao.insert(event("vergangen", now.minusSeconds(3600)))
        dao.insert(event("gleich jetzt", now))
        dao.insert(event("später", now.plusSeconds(7200)))

        val next = dao.findNextEvent(now.toEpochMilli())

        // Event exakt bei now ist inklusive (>=) und damit das nächste.
        assertEquals("gleich jetzt", next?.title)
    }

    @Test
    fun `insert mit REPLACE überschreibt bei gleichem Primärschlüssel`() = runTest {
        val id = dao.insert(event("original", Instant.parse("2026-07-16T10:00:00Z")))
        val updated = dao.findById(id)!!.copy(title = "ersetzt")

        val sameId = dao.insert(updated)

        assertEquals(id, sameId)
        assertEquals("ersetzt", dao.findById(id)?.title)
        assertTrue(dao.observeAll().first().size == 1)
    }
}
