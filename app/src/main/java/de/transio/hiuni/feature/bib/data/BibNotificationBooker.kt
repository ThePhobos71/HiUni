package de.transio.hiuni.feature.bib.data

import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.AuthRequiredException
import de.transio.hiuni.core.notifications.NotificationActionOutcome
import de.transio.hiuni.core.notifications.NotificationActionSpec
import timber.log.Timber
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Führt „Buchen" aus der Frei-Meldung eines Lieblings-Raums aus.
 *
 * Zwischen Meldung und Tap kann viel Zeit liegen. Deshalb werden die Regeln vor
 * dem Buchen gegen einen frischen Snapshot geprüft (bei Bedarf neu geladen):
 * angemeldet, Zeitraum noch nicht begonnen, heute noch keine eigene Buchung, alle
 * Slots des Zeitraums frei. Erst dann [BibRepository.book]; das legt die Buchung
 * über den normalen Refresh auch im Kalender an.
 *
 * Jeder Fehlerpfad endet als [NotificationActionOutcome.Failed] mit eigener
 * deutscher Meldung; Exceptions fängt der aufrufende Handler zusätzlich ab.
 */
@Singleton
class BibNotificationBooker @Inject constructor(
    private val repository: BibRepository,
    private val casSession: CasSession,
    private val clock: Clock
) {

    suspend fun book(spec: NotificationActionSpec.BookBibRoom): NotificationActionOutcome {
        if (casSession.state.value !is CasState.Authenticated) {
            Timber.i("Bib-Buchen aus Mitteilung: keine CAS-Session")
            return NotificationActionOutcome.Failed(MSG_AUTH)
        }
        if (!spec.date.atTime(spec.start).isAfter(LocalDateTime.now(clock))) {
            return NotificationActionOutcome.Failed(MSG_STARTED)
        }
        val snapshot = when (val fresh = freshSnapshot()) {
            is AppResult.Success -> fresh.data
            is AppResult.Failure -> {
                Timber.w(fresh.error, "Bib-Buchen aus Mitteilung: Snapshot nicht ladbar")
                return NotificationActionOutcome.Failed(failureMessageFor(fresh.error))
            }
        }
        if (snapshot.myBookings.any { it.date == spec.date }) {
            return NotificationActionOutcome.Failed(MSG_ALREADY_BOOKED)
        }
        if (!allSlotsFree(snapshot, spec)) {
            return NotificationActionOutcome.Failed(MSG_TAKEN)
        }
        return when (val result = repository.book(spec.date, spec.start, spec.end, spec.roomId)) {
            is AppResult.Success -> {
                Timber.i("Bib-Buchen aus Mitteilung: Raum %d gebucht", spec.roomId)
                NotificationActionOutcome.Confirmed(confirmation(spec))
            }
            is AppResult.Failure -> {
                Timber.w(result.error, "Bib-Buchen aus Mitteilung fehlgeschlagen")
                NotificationActionOutcome.Failed(failureMessageFor(result.error))
            }
        }
    }

    /**
     * Gecachter Snapshot, wenn er jünger als [MAX_SNAPSHOT_AGE] ist und eingeloggt
     * geladen wurde (nur dann kennt er die eigenen Buchungen), sonst ein Refresh.
     */
    private suspend fun freshSnapshot(): AppResult<BibSnapshot> {
        val data = repository.state.value
        val cached = data.snapshot
        if (cached != null && !data.needsLogin &&
            Duration.between(cached.fetchedAt, clock.instant()) < MAX_SNAPSHOT_AGE
        ) {
            return AppResult.Success(cached)
        }
        return repository.refresh()
    }

    private fun allSlotsFree(snapshot: BibSnapshot, spec: NotificationActionSpec.BookBibRoom): Boolean {
        val slots = snapshot.forRoomDay(spec.date, spec.roomId)?.slots.orEmpty().associateBy { it.startTime }
        var t: LocalTime = spec.start
        while (t.isBefore(spec.end)) {
            if (slots[t]?.status != SlotStatus.FREE) return false
            t = t.plusMinutes(BibConfig.SLOT_MINUTES.toLong())
        }
        return true
    }

    companion object {
        /** Ab diesem Alter wird der Snapshot vor dem Buchen neu geladen. */
        val MAX_SNAPSHOT_AGE: Duration = Duration.ofMinutes(2)

        const val MSG_AUTH = "Bitte in der App neu anmelden"
        const val MSG_ALREADY_BOOKED = "Du hast heute schon eine Buchung"
        const val MSG_TAKEN = "Raum ist inzwischen belegt"
        const val MSG_STARTED = "Der Zeitraum hat schon begonnen"
        const val MSG_GENERIC = "Buchung fehlgeschlagen"

        /** Bestätigung, z.B. „F102 gebucht 14:00–16:00". */
        fun confirmation(spec: NotificationActionSpec.BookBibRoom): String {
            val label = BibConfig.ROOM_META[spec.roomId]?.label ?: "F${spec.roomId}"
            return "$label gebucht ${clock(spec.start)}–${clock(spec.end)}"
        }

        /**
         * Ordnet einen Fehler einer der deutschen Meldungen zu. Die Bib antwortet
         * immer mit HTTP 200 und einer Klartext-Meldung im Body, die
         * [BibRepository.book] als Exception-Text weiterreicht.
         */
        fun failureMessageFor(error: Throwable): String {
            if (generateSequence(error) { it.cause }.any { it is AuthRequiredException }) return MSG_AUTH
            val text = error.message.orEmpty().lowercase()
            return when {
                "bib-login" in text || "cas-login" in text || "login erforderlich" in text -> MSG_AUTH
                "pro tag" in text || "1 buchung" in text || "eine buchung" in text -> MSG_ALREADY_BOOKED
                "belegt" in text || "vergeben" in text || "nicht frei" in text -> MSG_TAKEN
                else -> MSG_GENERIC
            }
        }

        private fun clock(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)
    }
}
