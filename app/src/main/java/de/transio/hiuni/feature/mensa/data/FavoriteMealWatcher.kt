package de.transio.hiuni.feature.mensa.data

import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationPresenter
import de.transio.hiuni.core.notifications.data.NotificationKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lieblingsgericht-Alarm: gleicht nach einem Speiseplan-Refresh die gemerkten
 * Stichwörter mit dem 14-Tage-Fenster der gewählten Mensa ab und meldet neue
 * Treffer einmal. Die Entscheidung trifft der reine [FavoriteMealNotifier],
 * Stichwörter und bereits Gemeldetes liegen im [FavoriteMealStore].
 *
 * Es gibt keinen eigenen Hintergrund-Abruf: geprüft wird nur, wenn der Speiseplan
 * ohnehin frisch geladen wurde (App-Start, Push-Sync, Pull-to-Refresh).
 */
@Singleton
class FavoriteMealWatcher @Inject constructor(
    private val store: FavoriteMealStore,
    private val dao: MealDao,
    private val settings: SettingsDataStore,
    private val presenter: NotificationPresenter,
    private val clock: Clock
) {

    /** Serialisiert parallele Checks, damit nicht zwei denselben Treffer melden. */
    private val mutex = Mutex()

    /**
     * @param notify `false` schreibt nur fort, was schon im Plan steht, ohne zu
     *   melden (frisch hinzugefügtes Stichwort, der Nutzer sitzt in der App).
     */
    suspend fun check(notify: Boolean = true) = mutex.withLock {
        val keywords = store.currentKeywords()
        if (keywords.isEmpty()) {
            store.setNotifiedKeys(emptySet())
            return@withLock
        }
        val today = LocalDate.now(clock)
        val locationId = settings.mensaLocationId.first()
        val meals = dao.observeRange(today, today.plusDays(WINDOW_DAYS), locationId).first()
        val decision = FavoriteMealNotifier.decide(
            keywords = keywords,
            meals = meals,
            notified = store.notifiedKeys(),
            today = today,
            notify = notify
        )
        store.setNotifiedKeys(decision.notified)
        val push = decision.push ?: return@withLock
        presenter.present(
            kind = NotificationKind.MENSA,
            title = push.title,
            body = push.body,
            refKey = push.refKey,
            systemId = NOTIFICATION_ID
        )
    }

    companion object {
        /** Negative IDs sind für synthetische Quellen reserviert (siehe [NotificationPresenter]). */
        const val NOTIFICATION_ID = -7301

        /** Heute plus 13 Tage, dasselbe Fenster wie [MensaRepository.refresh]. */
        const val WINDOW_DAYS = 13L
    }
}
