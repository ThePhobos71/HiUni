package de.transio.hiuni.core.notifications.lecture

import de.transio.hiuni.core.notifications.data.NotificationCategory
import de.transio.hiuni.core.notifications.data.NotificationSettingsStore
import de.transio.hiuni.di.ApplicationScope
import de.transio.hiuni.feature.calendar.data.CustomEventDao
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Zeigt und entfernt die Laufende-Vorlesung-Mitteilungen (Android-Seite: [AndroidLiveLectureNotifier]). */
interface LiveLectureNotifier {
    /** Zeigt bzw. aktualisiert die Mitteilung für ein laufendes Vorkommen (still, ohne erneuten Alarm). */
    fun show(occurrence: LiveLectureOccurrence, now: Instant)

    fun cancel(notificationId: Int)

    /** System-IDs der aktuell sichtbaren Laufende-Vorlesung-Mitteilungen. */
    fun activeNotificationIds(): Set<Int>
}

/** Der eine Weck-Alarm des Planers (Android-Seite: [AndroidLiveLectureAlarms]). */
interface LiveLectureAlarms {
    /**
     * Stellt den Weck-Alarm, ein vorher gestellter wird ersetzt.
     * @param exact `true` für einen Vorlesungsbeginn, `false` für die Nachplanung.
     */
    fun scheduleWake(at: Instant, exact: Boolean)

    fun cancelWake()
}

/**
 * Orchestriert die Vorlesungs-Mitteilungen aus dem LSF-Stundenplan:
 *
 *  - Laufende Vorlesung: „Analysis I", darunter „F101 · bis 11:45", im Kopf
 *    „läuft noch · 23:12" als selbst laufender Countdown.
 *  - Erinnerung vor Beginn: je Kurs einstellbar ([CourseNotificationPrefs]),
 *    geplant im rollierenden Fenster des [LectureReminderPlanner].
 *
 * ### Planen ([replan])
 *
 *  1. Schalter „Laufende Vorlesung" aus UND kein Kurs mit Erinnerung: alle sichtbaren
 *     Mitteilungen und offenen Erinnerungen weg, kein Weck-Alarm.
 *  2. LSF-Termine im Fenster `[now, now + 72 h + Vorlauf)` laden.
 *  3. Erinnerungen der Kurse mit Vorlauf über [LectureReminderAlarms] abgleichen
 *     (neue stellen, überholte abräumen). Unabhängig vom Live-Schalter.
 *  4. Live-Anzeige (nur bei globalem Schalter an): Kurse mit ausgeschalteter
 *     Live-Anzeige überspringen, sichtbare Mitteilungen, die nicht mehr laufen
 *     (Termin gelöscht/verschoben, Kurs abgeschaltet), entfernen, laufende (erneut)
 *     zeigen.
 *  5. Den EINEN Weck-Alarm auf den nächsten Vorlesungsbeginn legen, bzw. ungenau auf
 *     das Horizont-Ende (48 h), wenn nichts ansteht oder nur Erinnerungen aktiv sind.
 *     Dieses Nachplanen hält das Erinnerungs-Fenster am Rollen.
 *
 * Kein Polling: Der Countdown läuft im System (Chronometer), das Ende räumt
 * `setTimeoutAfter` ab. Der Prozess wird nur zu Vorlesungsbeginn geweckt.
 *
 * ### Auslöser
 *
 *  - [start] (App-Start): beobachtet Stundenplan, Schalter und Kurs-Einstellungen,
 *    plant bei jeder Änderung neu. Deckt LSF-Sync, Kalender-Edits und Toggles ab
 *    (ein abgeschalteter Kurs verliert seine sichtbare Mitteilung sofort), und mit
 *    der ersten Emission auch den Prozessstart mitten in einer Vorlesung.
 *  - [LiveLectureReceiver]: Weck-Alarm zu Vorlesungsbeginn bzw. am Horizont-Ende.
 *  - [de.transio.hiuni.core.sync.ReminderRestorer]: Reboot, App-Update, Zeit-/Zonenwechsel.
 *
 * Läufe sind per [Mutex] serialisiert und idempotent (gleiche ID, gleicher Alarm-Slot).
 */
@Singleton
class LiveLectureScheduler @Inject constructor(
    private val eventDao: CustomEventDao,
    private val settingsStore: NotificationSettingsStore,
    private val prefsStore: CourseNotificationPrefsStore,
    private val dismissStore: LiveLectureDismissStore,
    private val notifier: LiveLectureNotifier,
    private val alarms: LiveLectureAlarms,
    private val reminderAlarms: LectureReminderAlarms,
    @ApplicationScope private val appScope: CoroutineScope
) {

    /**
     * Zeitzonen-Override für Tests. In Produktion `null`: dann gilt bei jedem Lauf die
     * aktuelle System-Zone (wichtig nach `TIMEZONE_CHANGED`).
     */
    internal var zone: ZoneId? = null

    /** Uhr für die beobachtenden Läufe aus [start]; in Tests überschreibbar. */
    internal var clock: () -> Instant = { Instant.now() }

    private val mutex = Mutex()

    /** Plant neu. Sicher mehrfach aufrufbar. */
    suspend fun replan(now: Instant = clock()) = mutex.withLock {
        val liveOn = settingsStore.isEnabled(NotificationCategory.LIVE_LECTURE)
        val coursePrefs = prefsStore.current()
        val reminderMinutes = coursePrefs.reminderMinutesByKey()
        if (!liveOn && reminderMinutes.isEmpty()) {
            notifier.activeNotificationIds().forEach(notifier::cancel)
            reminderAlarms.sync(emptyList(), now)
            alarms.cancelWake()
            return@withLock
        }
        val zone = zone ?: ZoneId.systemDefault()
        val loadUntil = now
            .plus(maxOf(LiveLecturePlanner.HORIZON, LectureReminderPlanner.HORIZON))
            .plusSeconds(CourseNotificationPrefs.MAX_REMINDER_MINUTES * 60L)
        val candidates = eventDao.getLiveLectureCandidates(
            kind = CustomEventEntity.SOURCE_LSF_STUNDENPLAN,
            fromMillis = now.toEpochMilli(),
            toMillis = loadUntil.toEpochMilli()
        )

        val reminders = LectureReminderPlanner.plan(candidates, reminderMinutes, now, zone)
        reminderAlarms.sync(reminders, now)

        if (!liveOn) {
            notifier.activeNotificationIds().forEach(notifier::cancel)
            // Nur Erinnerungen aktiv: ungenaue Nachplanung hält ihr Fenster am Rollen.
            alarms.scheduleWake(now.plus(LiveLecturePlanner.HORIZON), exact = false)
            Timber.d("LiveLecture: Live-Anzeige aus, %d Erinnerungen geplant", reminders.size)
            return@withLock
        }

        val dismissed = dismissStore.current(now)
        val plan = LiveLecturePlanner.plan(
            events = candidates,
            now = now,
            zone = zone,
            dismissed = dismissed,
            liveDisabledCourses = coursePrefs.liveDisabledKeys()
        )

        val keep = plan.running.map { LiveLecturePlanner.notificationId(it.masterId) }.toSet()
        (notifier.activeNotificationIds() - keep).forEach(notifier::cancel)
        plan.running.forEach { notifier.show(it, now) }
        alarms.scheduleWake(plan.nextWakeAt, exact = plan.nextWakeIsLectureStart)

        Timber.d(
            "LiveLecture: %d laufend, %d Erinnerungen, nächster Weckzeitpunkt %s (%s)",
            plan.running.size,
            reminders.size,
            plan.nextWakeAt,
            if (plan.nextWakeIsLectureStart) "Vorlesungsbeginn" else "Nachplanung"
        )
    }

    /** Nutzer hat die Mitteilung weggewischt: dieses Vorkommen nicht erneut zeigen. */
    suspend fun onDismissed(key: String) {
        dismissStore.add(key)
    }

    /**
     * Beobachtet Stundenplan, Schalter und Kurs-Einstellungen im App-Scope. Mehrfache
     * Aufrufe würden mehrere Beobachter starten, deshalb nur aus `HiUniApplication.onCreate`.
     */
    @OptIn(FlowPreview::class)
    fun start() {
        appScope.launch {
            combine(
                eventDao.observeBySourceKind(CustomEventEntity.SOURCE_LSF_STUNDENPLAN),
                settingsStore.observeEnabled(NotificationCategory.LIVE_LECTURE),
                prefsStore.observeAll()
            ) { events, enabled, coursePrefs -> Triple(events, enabled, coursePrefs) }
                .distinctUntilChanged()
                // Ein LSF-Sync schreibt Zeile für Zeile, jede Zeile invalidiert die
                // Tabelle. Erst nach einer kurzen Ruhepause planen.
                .debounce(OBSERVE_DEBOUNCE_MS)
                .collectLatest {
                    try {
                        replan(clock())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        Timber.w(t, "LiveLecture: Neuplanung nach Änderung fehlgeschlagen")
                    }
                }
        }
    }

    private companion object {
        const val OBSERVE_DEBOUNCE_MS = 500L
    }
}
