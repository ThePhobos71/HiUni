package de.transio.hiuni.feature.bib.data

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
 * reinen [BibFreeSlotNotifier].
 *
 * Reihenfolge: erst die neue Baseline speichern, dann melden. Stürzt das Melden
 * ab, wird derselbe Slot beim nächsten Check trotzdem nicht doppelt gemeldet.
 */
@Singleton
class BibFavoriteWatcher @Inject constructor(
    private val store: BibFavoritesStore,
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
        val decision = BibFreeSlotNotifier.decide(
            days = snapshot.allDays,
            favorites = favorites,
            previousBooked = store.bookedBaseline(),
            now = LocalDateTime.now(clock)
        )
        store.setBookedBaseline(decision.bookedBaseline)
        for (push in decision.pushes) {
            runCatching {
                presenter.present(
                    kind = push.kind,
                    title = push.title,
                    body = push.body,
                    refKey = push.refKey,
                    systemId = systemIdFor(push.refKey)
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
