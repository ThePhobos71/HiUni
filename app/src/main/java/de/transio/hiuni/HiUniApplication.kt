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
import de.transio.hiuni.core.sync.LoginSyncOrchestrator
import de.transio.hiuni.core.sync.LsfSyncScheduler
import de.transio.hiuni.core.sync.SportSyncScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class HiUniApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var lsfSyncScheduler: LsfSyncScheduler
    @Inject lateinit var sportSyncScheduler: SportSyncScheduler
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var loginSyncOrchestrator: LoginSyncOrchestrator

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
        // Sport-Sync läuft mit festem 6h-Intervall (kein User-Setting).
        sportSyncScheduler.ensureScheduled(SPORT_SYNC_INTERVAL_HOURS)
        // App-scoped Auth-State-Listener — feuert LSF + Email-Sync bei jeder
        // not-auth → auth Transition. Muss hier laufen (nicht im ViewModel),
        // damit der allererste CAS-Login im Onboarding-Flow den Sync auslöst.
        loginSyncOrchestrator.start()
        initFirstSemester()
    }

    /**
     * Einmal-Initialisierung des „erstes Semester"-Markers für die Icon-Unlock-
     * Logik. Idempotent — überschreibt einen bereits gesetzten Wert nicht.
     */
    private fun initFirstSemester() {
        runCatching {
            runBlocking {
                val current = de.transio.hiuni.core.common.Semester
                    .fromDate(java.time.LocalDate.now())
                settingsDataStore.initFirstSemesterIfMissing(current.storageKey())
            }
        }.onFailure { Timber.w(it, "First-Semester-Init fehlgeschlagen") }
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

    private companion object {
        const val SPORT_SYNC_INTERVAL_HOURS = 6
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
