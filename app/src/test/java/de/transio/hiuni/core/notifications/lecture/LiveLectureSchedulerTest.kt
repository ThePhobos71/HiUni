package de.transio.hiuni.core.notifications.lecture

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.transio.hiuni.core.notifications.data.NotificationCategory
import de.transio.hiuni.core.notifications.data.NotificationSettingsStore
import de.transio.hiuni.feature.calendar.data.CustomEventDao
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Orchestrierung der Laufende-Vorlesung-Mitteilung: liest die LSF-Termine, zeigt
 * laufende an, räumt veraltete ab und stellt genau einen Weck-Alarm. Notification-
 * und Alarm-Seite sind Fakes, Kategorie-Schalter und Wegwisch-Merker laufen auf
 * einem echten Preferences-DataStore.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveLectureSchedulerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val now: Instant = LocalDateTime.of(2026, 10, 26, 10, 30).atZone(zone).toInstant()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: NotificationSettingsStore
    private lateinit var dismissStore: LiveLectureDismissStore

    private val dao = mockk<CustomEventDao>()
    private val lectures = MutableStateFlow<List<CustomEventEntity>>(emptyList())
    private val notifier = FakeNotifier()
    private val alarms = FakeAlarms()

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher),
            produceFile = { tmp.newFile("live_lecture.preferences_pb") }
        )
        settings = NotificationSettingsStore(dataStore)
        dismissStore = LiveLectureDismissStore(dataStore)
        coEvery { dao.getLiveLectureCandidates(any(), any(), any()) } answers { lectures.value }
        every { dao.observeBySourceKind(any()) } returns lectures
    }

    private fun scheduler(
        scope: CoroutineScope,
        settingsStore: NotificationSettingsStore = settings
    ) = LiveLectureScheduler(
        eventDao = dao,
        settingsStore = settingsStore,
        dismissStore = dismissStore,
        notifier = notifier,
        alarms = alarms,
        appScope = scope
    ).also { it.zone = zone }

    private fun lecture(id: Long, startOffset: Duration, length: Duration = Duration.ofMinutes(90)) =
        CustomEventEntity(
            id = id,
            title = "Vorlesung $id",
            location = "F10$id",
            startTime = now.plus(startOffset),
            endTime = now.plus(startOffset).plus(length),
            sourceKind = CustomEventEntity.SOURCE_LSF_STUNDENPLAN
        )

    @Test
    fun `laufende Vorlesung wird angezeigt und der naechste Start geweckt`() = runTest(dispatcher) {
        lectures.value = listOf(
            lecture(1, Duration.ofMinutes(-15)),
            lecture(2, Duration.ofHours(3))
        )
        scheduler(backgroundScope).replan(now)

        val shown = notifier.active.getValue(LiveLecturePlanner.notificationId(1))
        assertEquals("Vorlesung 1", shown.title)
        assertEquals(now.plus(Duration.ofMinutes(75)), shown.end)
        assertEquals(setOf(LiveLecturePlanner.notificationId(1)), notifier.active.keys)
        assertEquals(now.plus(Duration.ofHours(3)), alarms.wakeAt)
        assertTrue(alarms.exact)
    }

    @Test
    fun `ohne Vorlesungen im Horizont nur ungenaue Nachplanung`() = runTest(dispatcher) {
        lectures.value = listOf(lecture(1, Duration.ofDays(5)))
        scheduler(backgroundScope).replan(now)

        assertTrue(notifier.active.isEmpty())
        assertEquals(now.plus(LiveLecturePlanner.HORIZON), alarms.wakeAt)
        assertFalse(alarms.exact)
    }

    @Test
    fun `beendete oder geloeschte Vorlesung wird abgeraeumt`() = runTest(dispatcher) {
        val staleId = LiveLecturePlanner.notificationId(99)
        notifier.active[staleId] = LiveLectureOccurrence(99, "Alt", null, now.minusSeconds(600), now.plusSeconds(600))
        lectures.value = listOf(lecture(1, Duration.ofMinutes(-5)))

        scheduler(backgroundScope).replan(now)

        assertFalse(staleId in notifier.active)
        assertTrue(LiveLecturePlanner.notificationId(1) in notifier.active)
    }

    @Test
    fun `ausgeschaltete Kategorie raeumt alles ab und stellt keinen Alarm`() = runTest(dispatcher) {
        lectures.value = listOf(lecture(1, Duration.ofMinutes(-5)), lecture(2, Duration.ofHours(2)))
        val scheduler = scheduler(backgroundScope)
        scheduler.replan(now)
        assertTrue(notifier.active.isNotEmpty())

        settings.setEnabled(NotificationCategory.LIVE_LECTURE, false)
        scheduler.replan(now)

        assertTrue(notifier.active.isEmpty())
        assertNull(alarms.wakeAt)
        assertTrue(alarms.cancelled)
    }

    @Test
    fun `weggewischte Vorlesung kommt beim naechsten Planen nicht wieder`() = runTest(dispatcher) {
        lectures.value = listOf(lecture(1, Duration.ofMinutes(-5)))
        val scheduler = scheduler(backgroundScope)
        scheduler.replan(now)
        val key = notifier.active.getValue(LiveLecturePlanner.notificationId(1)).key

        // Nutzer wischt weg (Android 14+ erlaubt das auch bei ongoing).
        notifier.active.clear()
        scheduler.onDismissed(key)
        scheduler.replan(now.plusSeconds(60))

        assertTrue(notifier.active.isEmpty())
    }

    @Test
    fun `Aenderung am Stundenplan plant automatisch neu`() = runTest(dispatcher) {
        // Schalter als steuerbarer Flow. Der Wegwisch-Merker liest weiter aus dem echten
        // DataStore, dessen Datei-IO auf echten Threads läuft; deshalb [awaitUntil].
        val enabled = MutableStateFlow(true)
        val switchable = mockk<NotificationSettingsStore>()
        every { switchable.observeEnabled(NotificationCategory.LIVE_LECTURE) } returns enabled
        coEvery { switchable.isEnabled(NotificationCategory.LIVE_LECTURE) } answers { enabled.value }
        val scheduler = scheduler(backgroundScope, switchable)
        scheduler.clock = { now }
        scheduler.start()
        // Erste Emission (Prozessstart): plant und stellt den Weck-Alarm.
        assertTrue(awaitUntil { alarms.wakeAt != null })
        assertTrue(notifier.active.isEmpty())

        // LSF-Sync bzw. Kalender-Edit: neue laufende Vorlesung taucht auf.
        lectures.value = listOf(lecture(3, Duration.ofMinutes(-10)))
        assertTrue(awaitUntil { LiveLecturePlanner.notificationId(3) in notifier.active })

        // Schalter aus: Mitteilung verschwindet ohne weiteren Anstoß.
        enabled.value = false
        assertTrue(awaitUntil { notifier.active.isEmpty() && alarms.cancelled })
    }

    /**
     * Treibt virtuelle Zeit und wartet kurz real, bis [condition] gilt (max. ca. 5 s).
     * Bewusst `advanceTimeBy` + `runCurrent` statt `advanceUntilIdle`: letzteres hört
     * auf, sobald nur noch `backgroundScope`-Arbeit ansteht, und dort läuft der Beobachter.
     */
    private fun TestScope.awaitUntil(condition: () -> Boolean): Boolean {
        repeat(500) {
            advanceTimeBy(100)
            runCurrent()
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    // --- Fakes ------------------------------------------------------------------------

    private class FakeNotifier : LiveLectureNotifier {
        val active = linkedMapOf<Int, LiveLectureOccurrence>()
        override fun show(occurrence: LiveLectureOccurrence, now: Instant) {
            active[LiveLecturePlanner.notificationId(occurrence.masterId)] = occurrence
        }
        override fun cancel(notificationId: Int) {
            active.remove(notificationId)
        }
        override fun activeNotificationIds(): Set<Int> = active.keys.toSet()
    }

    private class FakeAlarms : LiveLectureAlarms {
        var wakeAt: Instant? = null
        var exact = false
        var cancelled = false
        override fun scheduleWake(at: Instant, exact: Boolean) {
            wakeAt = at
            this.exact = exact
            cancelled = false
        }
        override fun cancelWake() {
            wakeAt = null
            cancelled = true
        }
    }
}
