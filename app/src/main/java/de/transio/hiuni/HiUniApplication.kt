package de.transio.hiuni

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService
import dagger.hilt.android.HiltAndroidApp
import de.transio.hiuni.core.notifications.NotificationScheduler
import timber.log.Timber

@HiltAndroidApp
class HiUniApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        registerNotificationChannels()
    }

    private fun registerNotificationChannels() {
        val manager = getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            NotificationScheduler.CHANNEL_ID_EVENTS,
            getString(R.string.notification_channel_events),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_events_description)
        }
        manager.createNotificationChannel(channel)
    }
}
