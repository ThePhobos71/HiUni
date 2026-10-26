package de.transio.hiuni.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.transio.hiuni.core.sync.ReminderRestorer
import de.transio.hiuni.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * Plant nach System-Ereignissen, bei denen AlarmManager-Alarme verloren gehen oder
 * falsch liegen, alle lokalen Reminder neu ([ReminderRestorer]).
 *
 *  - `BOOT_COMPLETED`: Reboot verwirft ALLE Alarme. Kommt erst nach dem ersten
 *    Entsperren, dann ist die SQLCipher-DB samt Keystore-Schlüssel lesbar.
 *  - `MY_PACKAGE_REPLACED`: nach einem App-Update sind die Alarme der App weg.
 *  - `TIME_SET` / `TIMEZONE_CHANGED`: Alarme sind RTC-basiert (Wall-Clock), viele
 *    Trigger werden aus lokaler Uhrzeit gerechnet (Klausur 12:00/18:00, Aufgabe
 *    09:00, Learnweb 18:00, Serientermine). Nach einem Zonenwechsel lägen sie sonst
 *    um den Zonen-Versatz daneben.
 *  - `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (API 31+): wurde die
 *    Exakt-Alarm-Berechtigung (wieder) erteilt, werden zuvor nur ungenau geplante
 *    Alarme als exakte Alarme neu gesetzt.
 *
 * Bewusst NICHT `LOCKED_BOOT_COMPLETED`: Vor dem ersten Entsperren ist der
 * Credential-Encrypted-Speicher gesperrt. Dort liegen die SQLCipher-DB und der
 * per Keystore geschützte DB-Schlüssel (EncryptedSharedPreferences), der Restore
 * könnte also nichts lesen. Der Receiver ist deshalb auch nicht `directBootAware`.
 *
 * `exported=false`: die System-Broadcasts kommen trotzdem an, fremde Apps können
 * den Restore aber nicht auslösen.
 *
 * Arbeit läuft per `goAsync()` im App-Scope mit hartem Zeitlimit. Beim Boot startet
 * der Prozess für diesen Broadcast und `HiUniApplication.onCreate` stößt ebenfalls
 * einen Restore an; der zweite Lauf ist dank Idempotenz und Mutex harmlos, hält
 * aber den Prozess am Leben, bis die Alarme sicher stehen.
 */
@AndroidEntryPoint
class ReminderRestoreReceiver : BroadcastReceiver() {

    @Inject lateinit var restorer: ReminderRestorer
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (!handles(action)) {
            Timber.w("ReminderRestoreReceiver: unerwartete Aktion %s ignoriert", action)
            return
        }
        Timber.i("ReminderRestoreReceiver: %s, plane Reminder neu", action)
        val pending = goAsync()
        appScope.launch {
            try {
                val done = withTimeoutOrNull(TIMEOUT_MS) { restorer.restoreAll() }
                if (done == null) {
                    Timber.w("ReminderRestoreReceiver: Restore nach %d ms abgebrochen", TIMEOUT_MS)
                }
            } catch (t: Throwable) {
                Timber.w(t, "ReminderRestoreReceiver: Restore fehlgeschlagen")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /**
         * Wert von `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`
         * (API 31). Als Literal, damit minSdk 28 keine InlinedApi-Warnung wirft.
         */
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        /** Muss mit dem Intent-Filter im AndroidManifest übereinstimmen. */
        val HANDLED_ACTIONS: Set<String> = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED
        )

        fun handles(action: String?): Boolean = action in HANDLED_ACTIONS

        /**
         * Obergrenze für die Arbeit nach `goAsync()`. Knapp unter dem 10-s-Fenster,
         * das Android Broadcast-Receivern zugesteht. Ein abgebrochener Lauf ist
         * unkritisch: der Restore beim Prozessstart läuft unabhängig weiter.
         */
        private const val TIMEOUT_MS = 9_000L
    }
}
