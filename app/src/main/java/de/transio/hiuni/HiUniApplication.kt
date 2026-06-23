package de.transio.hiuni

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationScheduler
import de.transio.hiuni.core.sync.LsfSyncScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class HiUniApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var lsfSyncScheduler: LsfSyncScheduler
    @Inject lateinit var settingsDataStore: SettingsDataStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        registerNotificationChannels()
        scheduleLsfSync()
    }

    private fun scheduleLsfSync() {
        // Periodic-Worker bei jedem App-Start re-registrieren — billig dank
        // UPDATE-Policy, und garantiert dass das aktuelle User-Intervall greift,
        // wenn es z.B. nach einer Re-Install neu aus DataStore kommt. Blocking
        // ist hier OK: DataStore.first() ist günstig und Application.onCreate
        // läuft eh auf dem Main-Thread vor erster Activity.
        val intervalHours = runCatching {
            runBlocking { settingsDataStore.lsfSyncIntervalHours.first() }
        }.getOrElse { SettingsDataStore.DEFAULT_LSF_SYNC_INTERVAL_HOURS }
        lsfSyncScheduler.ensureScheduled(intervalHours)
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
