package de.transio.hiuni.core.notifications.lecture

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Der eine Weck-Alarm der Laufende-Vorlesung-Mitteilung. Fester PendingIntent-Slot
 * ([LiveLectureReceiver.wakeIntent]), ein neuer Alarm ersetzt also immer den alten.
 *
 * Vorlesungsbeginn: exakt (`setExactAndAllowWhileIdle`), sofern die App exakte Alarme
 * stellen darf, sonst `setAndAllowWhileIdle`; ein paar Minuten Verspätung schaden
 * nicht, der Countdown rechnet ab dem tatsächlichen Ende. Nachplanung am
 * Horizont-Ende: bewusst ungenau.
 */
@Singleton
class AndroidLiveLectureAlarms @Inject constructor(
    @ApplicationContext private val context: Context
) : LiveLectureAlarms {

    override fun scheduleWake(at: Instant, exact: Boolean) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val pending = LiveLectureReceiver.wakeIntent(context)
        val triggerMillis = at.toEpochMilli()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        when {
            exact && canExact ->
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            exact -> alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            else -> alarmManager.set(AlarmManager.RTC, triggerMillis, pending)
        }
    }

    override fun cancelWake() {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val pending: PendingIntent = LiveLectureReceiver.wakeIntent(context)
        alarmManager.cancel(pending)
    }
}
