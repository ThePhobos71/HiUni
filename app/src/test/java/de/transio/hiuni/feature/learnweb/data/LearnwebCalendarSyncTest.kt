package de.transio.hiuni.feature.learnweb.data

import de.transio.hiuni.core.database.AppDatabase
import de.transio.hiuni.core.database.newInMemoryDatabase
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.feature.calendar.data.CustomEventDao
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Tests für die Dedup-Logik in [LearnwebCalendarSync]: Eine Moodle-Abgabe kommt
 * sowohl über den Assignment-Scraper (`sourceReference = 4875`) als auch über
 * den iCal-Feed (`sourceReference = event_4875@…`) herein und darf im Kalender
 * nur einmal auftauchen, egal in welcher Reihenfolge die Quellen laufen und
 * egal welche Quelle den Eintrag später wieder fallen lässt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class LearnwebCalendarSyncTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CustomEventDao
    private lateinit var sync: LearnwebCalendarSync

    private val due: Instant = Instant.parse("2026-11-02T22:59:00Z")

    private fun assignment(eventId: Long, title: String = "Hausaufgabe $eventId ist fällig.") =
        LearnwebAssignment(
            rowId = eventId,
            eventId = eventId,
            title = title,
            dueEpoch = due.toEpochMilli(),
            url = "https://learnweb.example/mod/assign/view.php?id=$eventId",
            syncedAt = 0L
        )

    private fun iCal(
        uid: String,
        title: String = "Hausaufgabe ist fällig",
        start: Instant = due
    ) = ParsedICalEvent(
        uid = uid,
        title = title,
        description = "Bitte als PDF abgeben.",
        startEpoch = start.toEpochMilli(),
        endEpoch = null,
        url = "https://learnweb.example/mod/assign/view.php?id=1",
        courseName = "Logistik und Produktion 1"
    )

    private suspend fun allEvents(): List<CustomEventEntity> = dao.observeAll().first()

    @Before
    fun setUp() {
        db = newInMemoryDatabase()
        dao = db.customEventDao()
        val settings = mockk<SettingsDataStore>(relaxed = true)
        every { settings.notificationMinutesBefore } returns flowOf(30)
        sync = LearnwebCalendarSync(dao, settings)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `Scraper zuerst, dann Feed ergibt genau einen Eintrag`() = runTest {
        val assignments = listOf(assignment(4875))
        sync.mirror(assignments)
        sync.mirrorICalEvents(listOf(iCal("event_4875@learnweb.uni-hildesheim.de")), assignments)

        val events = allEvents()
        assertEquals(1, events.size)
        assertEquals(CustomEventEntity.SOURCE_LEARNWEB_ICAL, events.single().sourceKind)
        // Gleicher Anzeigetitel wie bei der Scraper-Zeile (ohne „ist fällig").
        assertEquals("Hausaufgabe", events.single().title)
    }

    @Test
    fun `Feed zuerst, dann Scraper ergibt genau einen Eintrag`() = runTest {
        val assignments = listOf(assignment(4875))
        sync.mirrorICalEvents(listOf(iCal("event_4875@learnweb.uni-hildesheim.de")), emptyList())
        sync.mirror(assignments)

        val events = allEvents()
        assertEquals(1, events.size)
        assertEquals(CustomEventEntity.SOURCE_LEARNWEB_ICAL, events.single().sourceKind)
    }

    @Test
    fun `wiederholte Refreshes bleiben bei einem Eintrag`() = runTest {
        val assignments = listOf(assignment(4875))
        val feed = listOf(iCal("4875@www.uni-hildesheim.de/learnweb2026"))
        repeat(3) {
            sync.mirror(assignments)
            sync.mirrorICalEvents(feed, assignments)
        }

        assertEquals(1, allEvents().size)
    }

    @Test
    fun `Feed laesst Abgabe fallen, Scraper-Eintrag kommt zurueck`() = runTest {
        val assignments = listOf(assignment(4875))
        sync.mirror(assignments)
        sync.mirrorICalEvents(listOf(iCal("event_4875@learnweb.uni-hildesheim.de")), assignments)

        // Nächster Feed enthält die Abgabe nicht mehr (nur einen anderen Termin),
        // der Scraper kennt sie aber weiterhin.
        sync.mirrorICalEvents(
            listOf(iCal("event_9000@learnweb.uni-hildesheim.de", title = "Sprechstunde")),
            assignments
        )

        val survivor = dao.findBySourceReference(CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT, "4875")
        assertEquals("Hausaufgabe 4875", survivor?.title)
        assertNull(
            dao.findBySourceReference(
                CustomEventEntity.SOURCE_LEARNWEB_ICAL,
                "event_4875@learnweb.uni-hildesheim.de"
            )
        )
        assertEquals(2, allEvents().size)
    }

    @Test
    fun `Scraper laesst Abgabe fallen, Feed-Eintrag bleibt stehen`() = runTest {
        sync.mirror(listOf(assignment(4875)))
        sync.mirrorICalEvents(
            listOf(iCal("event_4875@learnweb.uni-hildesheim.de")),
            listOf(assignment(4875))
        )

        // Scraper kennt 4875 nicht mehr (z.B. älter als 24h), dafür eine neue Abgabe.
        sync.mirror(listOf(assignment(5037)))

        val feedEntry = dao.findBySourceReference(
            CustomEventEntity.SOURCE_LEARNWEB_ICAL,
            "event_4875@learnweb.uni-hildesheim.de"
        )
        assertEquals(due, feedEntry?.startTime)
        assertEquals(
            listOf("5037"),
            dao.sourceReferencesFor(CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT)
        )
        assertEquals(2, allEvents().size)
    }

    @Test
    fun `verschiedene Moodle-IDs werden nicht zusammengelegt`() = runTest {
        val assignments = listOf(assignment(4875))
        sync.mirror(assignments)
        sync.mirrorICalEvents(listOf(iCal("event_4876@learnweb.uni-hildesheim.de")), assignments)

        assertEquals(2, allEvents().size)
    }

    @Test
    fun `UID ohne Moodle-Event-ID fuehrt nicht zur Dedup`() = runTest {
        val assignments = listOf(assignment(4875))
        sync.mirror(assignments)
        sync.mirrorICalEvents(listOf(iCal("4875-irgendwas@extern.example")), assignments)

        assertEquals(2, allEvents().size)
    }

    @Test
    fun `moodleEventIdFromUid liest Moodle-Formate und lehnt Fremdes ab`() {
        assertEquals(4875L, LearnwebICalParser.moodleEventIdFromUid("event_4875@learnweb.uni-hildesheim.de"))
        assertEquals(4875L, LearnwebICalParser.moodleEventIdFromUid("4875@www.uni-hildesheim.de/learnweb2026"))
        assertEquals(4875L, LearnwebICalParser.moodleEventIdFromUid("  4875@host "))
        assertNull(LearnwebICalParser.moodleEventIdFromUid("has_uid@x"))
        assertNull(LearnwebICalParser.moodleEventIdFromUid("4875-irgendwas@extern.example"))
        assertNull(LearnwebICalParser.moodleEventIdFromUid("4875"))
        assertNull(LearnwebICalParser.moodleEventIdFromUid(""))
    }
}
