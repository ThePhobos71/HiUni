package de.transio.hiuni

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.data.NotificationCategory
import de.transio.hiuni.core.push.PushRegistrationScheduler
import de.transio.hiuni.core.sync.LoginSyncOrchestrator
import de.transio.hiuni.core.sync.LsfSyncScheduler
import de.transio.hiuni.core.sync.PrefetchOrchestrator
import de.transio.hiuni.core.sync.RecurringReminderRescheduler
import de.transio.hiuni.core.sync.SportSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    @Inject lateinit var pushRegistrationScheduler: PushRegistrationScheduler
    @Inject lateinit var recurringReminderRescheduler: RecurringReminderRescheduler
    @Inject lateinit var prefetchOrchestrator: PrefetchOrchestrator

    /** App-scoped Scope für Fire-and-forget-Startup-Jobs (kein UI-Lifecycle). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        rescheduleRecurringReminders()
        ensurePushRegistration()
        // Gestaffelter Hintergrund-Warmup der Feature-Caches beim App-Start, damit
        // Screens beim Öffnen sofort frische Room-Daten zeigen statt einzeln
        // nachzuladen. TTL-gated + online-/auth-gegatet, fire-and-forget im
        // ApplicationScope (blockiert onCreate() nicht).
        prefetchOrchestrator.prefetch()
    }

    /**
     * Wenn Mail-Push aktiv ist, beim Start eine (idempotente) Re-Registrierung
     * anstoßen — fängt den Fall ab, dass Firebase den Token rotiert hat, während
     * die App offline/tot war und `onNewToken` daher nie durchkam. Der
     * [PushRegistrationScheduler] holt den aktuellen Token; der dahinterliegende
     * Worker/Manager no-op-t, wenn sich der Token nicht geändert hat. Ohne
     * aktives Feature passiert nichts (kein Token-Fetch) — die App läuft dann
     * exakt wie bisher.
     */
    private fun ensurePushRegistration() {
        val enabled = runCatching {
            runBlocking { settingsDataStore.mailPushEnabled.first() }
        }.getOrDefault(false)
        if (!enabled) return
        appScope.launch {
            runCatching { pushRegistrationScheduler.enableAndRegister() }
                .onFailure { Timber.w(it, "Push-Re-Registrierung beim Start fehlgeschlagen") }
        }
    }

    /**
     * Sicherheitsnetz: Reboot / Force-Stop / Doze können exakte AlarmManager-Alarme
     * verwerfen. Beim App-Start planen wir für jeden wiederkehrenden Event mit
     * Reminder die nächste fällige Occurrence erneut ein. Läuft im Hintergrund
     * (DB-IO), blockiert onCreate() also nicht.
     */
    private fun rescheduleRecurringReminders() {
        appScope.launch {
            runCatching { recurringReminderRescheduler.rescheduleAll() }
                .onFailure { Timber.w(it, "Recurring-Reminder-Reschedule beim Start fehlgeschlagen") }
        }
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

    /**
     * Legt für jede [NotificationCategory] genau einen Android-Notification-Channel
     * an. Idempotent — `createNotificationChannel` überschreibt Name/Beschreibung,
     * respektiert aber die vom Nutzer in den Systemeinstellungen gewählte
     * Stumm-/Wichtigkeit. So kann der Nutzer je Kategorie (Noten, Klausuren, …)
     * getrennt stummschalten.
     */
    private fun registerNotificationChannels() {
        val manager = getSystemService<NotificationManager>() ?: return
        for (category in NotificationCategory.entries) {
            val (nameRes, descRes) = channelStringsFor(category)
            val channel = NotificationChannel(
                category.channelId,
                getString(nameRes),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(descRes)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun channelStringsFor(category: NotificationCategory): Pair<Int, Int> = when (category) {
        NotificationCategory.EVENTS ->
            R.string.notification_channel_events to R.string.notification_channel_events_description
        NotificationCategory.EXAMS ->
            R.string.notification_channel_exams to R.string.notification_channel_exams_description
        NotificationCategory.GRADES ->
            R.string.notification_channel_grades to R.string.notification_channel_grades_description
        NotificationCategory.COURSES ->
            R.string.notification_channel_courses to R.string.notification_channel_courses_description
        NotificationCategory.LEARNWEB ->
            R.string.notification_channel_learnweb to R.string.notification_channel_learnweb_description
        NotificationCategory.MAIL ->
            R.string.notification_channel_mail to R.string.notification_channel_mail_description
        NotificationCategory.SYSTEM ->
            R.string.notification_channel_system to R.string.notification_channel_system_description
    }
}
