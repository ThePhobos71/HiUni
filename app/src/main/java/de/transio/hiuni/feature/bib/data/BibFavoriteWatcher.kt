package de.transio.hiuni.feature.bib.data

import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.notifications.NotificationActionPlanner
import de.transio.hiuni.core.notifications.NotificationPresenter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prüft nach jedem frisch geladenen Bib-Snapshot, ob ein Lieblings-Raum wieder
 * frei geworden ist, und meldet das über den [NotificationPresenter] (der die
 * In-App-Kategorie „Bibliothek" respektiert). Die Entscheidung selbst liegt im
 * reinen [BibFreeSlotNotifier], gesteuert vom Raum-Alarm aus dem [BibRoomAlarmStore].
 *
 * Jede Meldung bekommt, wenn die Regeln es erlauben, einen „Buchen"-Button
 * (siehe [NotificationActionPlanner.bibFreeRoomActions]): eingeloggt, heute noch
 * keine eigene Buchung laut Snapshot, Zeitraum in der Zukunft.
 *
 * Reihenfolge: erst die neue Baseline speichern, dann melden. Stürzt das Melden
 * ab, wird derselbe Slot beim nächsten Check trotzdem nicht doppelt gemeldet.
 */
@Singleton
class BibFavoriteWatcher @Inject constructor(
    private val store: BibFavoritesStore,
    private val alarmStore: BibRoomAlarmStore,
    private val casSession: CasSession,
    private val presenter: NotificationPresenter,
    private val clock: Clock
) {

    /**
     * Serialisiert parallele Checks (z.B. Screen-Refresh und Tickle gleichzeitig),
     * damit nicht beide gegen dieselbe alte Baseline diffen und doppelt melden.
     */
    private val mutex = Mutex()

    suspend fun onSnapshot(snapshot: BibSnapshot) = mutex.withLock { check(snapshot) }

    private suspend fun check(snapshot: BibSnapshot) {
        val favorites = store.currentFavorites()
        if (favorites.isEmpty()) {
            store.setBookedBaseline(emptySet())
            return
        }
        val now = LocalDateTime.now(clock)
        // Lässt sich der Alarm nicht lesen, gilt er als aus: lieber eine Meldung
        // verpassen als ungewollt piepen. Die Baseline läuft trotzdem weiter.
        val alarm = runCatching { alarmStore.current(now.toLocalDate()) }
            .onFailure { Timber.w(it, "Bib: Raum-Alarm nicht lesbar, gilt als aus") }
            .getOrDefault(BibRoomAlarm(mode = BibAlarmMode.OFF))
        val decision = BibFreeSlotNotifier.decide(
            days = snapshot.allDays,
            favorites = favorites,
            previousBooked = store.bookedBaseline(),
            now = now,
            alarm = alarm
        )
        store.setBookedBaseline(decision.bookedBaseline)
        val authenticated = casSession.state.value is CasState.Authenticated
        for (push in decision.pushes) {
            runCatching {
                val actions = NotificationActionPlanner.bibFreeRoomActions(
                    roomId = push.roomId,
                    date = push.date,
                    blockStart = push.from,
                    blockEnd = push.until,
                    desiredMinutes = alarm.durationMinutes,
                    authenticated = authenticated,
                    hasOwnBookingOnDate = snapshot.myBookings.any { it.date == push.date },
                    now = now
                )
                presenter.present(
                    kind = push.kind,
                    title = push.title,
                    body = push.body,
                    refKey = push.refKey,
                    systemId = systemIdFor(push.refKey),
                    actions = actions
                )
            }.onFailure { Timber.w(it, "Bib: Frei-Meldung für Raum ${push.roomId} fehlgeschlagen") }
        }
        if (decision.pushes.isNotEmpty()) {
            Timber.i("Bib: ${decision.pushes.size} Frei-Meldung(en) für Lieblings-Räume")
        }
    }

    /**
     * Negative IDs sind synthetischen Quellen vorbehalten (siehe
     * [NotificationPresenter.TEST_NOTIFICATION_ID]). Bib-Frei-Meldungen liegen
     * zwischen ca. -770_000_000 und -500_000_000, also zwischen den Noten-/Kurs-
     * Pushes (bis ca. -470_000_000) und den Aufgaben (ab -1_000_000_001). Je Raum
     * und Startzeit eine eigene ID, damit zwei Räume sich nicht überschreiben.
     */
    private fun systemIdFor(refKey: String): Int =
        SYSTEM_ID_BASE - (refKey.hashCode() and 0x0FFFFFFF)

    private companion object {
        const val SYSTEM_ID_BASE = -500_000_000
    }
}
