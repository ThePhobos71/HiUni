package de.transio.hiuni.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.icon.AppIconManager
import de.transio.hiuni.core.notifications.NotificationPresenter
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.core.security.CredentialsManager
import de.transio.hiuni.core.design.ThemeMode
import de.transio.hiuni.core.sync.LsfSyncScheduler
import de.transio.hiuni.core.sync.SportSyncScheduler
import de.transio.hiuni.feature.email.MailSwipeAction
import de.transio.hiuni.feature.email.data.EmailRepository
import de.transio.hiuni.feature.mensa.data.MensaRepository
import de.transio.hiuni.feature.movies.data.MoviesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val credentials: CredentialsManager,
    private val lsfSyncScheduler: LsfSyncScheduler,
    private val sportSyncScheduler: SportSyncScheduler,
    private val mensaRepository: MensaRepository,
    private val moviesRepository: MoviesRepository,
    private val emailRepository: EmailRepository,
    private val notificationPresenter: NotificationPresenter,
    private val appIconManager: AppIconManager
) : ViewModel() {

    private val _draft = MutableStateFlow(CredentialsDraft())
    private val _message = MutableStateFlow<String?>(null)
    private val _credentialsBump = MutableStateFlow(0)
    private val _runningSyncs = MutableStateFlow<Set<SyncJob>>(emptySet())

    private companion object {
        // Worker-basierte Syncs (LSF, Sport) liefern kein synchrones Completion-
        // Signal; deshalb halten wir den Lock für ~3 s, bis WorkManager garantiert
        // eingequeut hat. Repo-basierte Syncs lösen den Lock direkt nach
        // .refresh()-Return + kurzem Cooldown gegen Reflex-Doppelklick.
        const val WORKER_COOLDOWN_MS = 3000L
        const val REPO_COOLDOWN_MS = 800L
        const val TEST_NOTIFY_COOLDOWN_MS = 2000L
    }

    private fun markRunning(job: SyncJob, running: Boolean) {
        _runningSyncs.update { if (running) it + job else it - job }
    }

    /**
     * Alle Sync-Job-Timestamps + LSF-Intervall in einem Bundle. Sonst sprengen
     * wir das 5-Slot-Arity-Limit von [combine].
     */
    private data class SyncBundle(
        val lsfIntervalHours: Int,
        val lastLsf: Long,
        val lastLsfExams: Long,
        val lastMensa: Long,
        val lastMovies: Long,
        val lastSport: Long,
        val lastEmail: Long
    )

    private val syncBundle = combine(
        combine(
            settings.lsfSyncIntervalHours,
            settings.lastLsfSyncEpoch,
            settings.lastLsfExamsRefreshEpoch
        ) { i, lsf, ex -> Triple(i, lsf, ex) },
        combine(
            settings.lastMensaRefreshEpoch,
            settings.lastMoviesRefreshEpoch,
            settings.lastSportRefreshEpoch,
            settings.lastEmailSyncEpoch
        ) { m, mo, s, e -> listOf(m, mo, s, e) }
    ) { lsf, others ->
        SyncBundle(
            lsfIntervalHours = lsf.first,
            lastLsf = lsf.second,
            lastLsfExams = lsf.third,
            lastMensa = others[0],
            lastMovies = others[1],
            lastSport = others[2],
            lastEmail = others[3]
        )
    }

    private val appearanceBundle = combine(
        combine(
            settings.mailSwipeRightAction,
            settings.mailSwipeLeftAction,
            settings.themeMode,
            settings.mailRequiresBiometric,
            settings.mailDeleteLocalOnly
        ) { right, left, theme, requiresBio, localOnly ->
            AppearanceBundle(
                swipeRight = MailSwipeAction.fromKey(right),
                swipeLeft = MailSwipeAction.fromKey(left),
                theme = ThemeMode.fromKey(theme),
                mailRequiresBiometric = requiresBio,
                mailDeleteLocalOnly = localOnly,
                appIconVariant = SettingsDataStore.DEFAULT_APP_ICON_VARIANT
            )
        },
        // App-Icon-Variante in zweitem combine, weil das 5-Arg-Limit oben
        // schon erschöpft war. Merger setzt das Feld auf den frischen Wert.
        settings.appIconVariant
    ) { bundle, iconVariant ->
        bundle.copy(appIconVariant = iconVariant)
    }

    val state: StateFlow<SettingsUiState> = combine(
        combine(
            settings.mensaLocationId,
            settings.notificationMinutesBefore,
            settings.emailSyncIntervalMinutes
        ) { loc, rem, sync -> Triple(loc, rem, sync) },
        combine(_draft, _credentialsBump, _message, _runningSyncs) { d, _, msg, running ->
            Triple(d, msg, running)
        },
        syncBundle,
        appearanceBundle
    ) { locRemSync, draftMessageRunning, sync, appearance ->
        val (locationId, reminderMinutes, syncInterval) = locRemSync
        val (draft, message, running) = draftMessageRunning
        SettingsUiState(
            selectedLocationId = locationId,
            notificationMinutesBefore = reminderMinutes,
            emailSyncIntervalMinutes = syncInterval,
            emailUsername = credentials.getUsername().orEmpty(),
            hasStoredCredentials = credentials.hasCredentials(),
            credentialsDraft = draft,
            lsfSyncIntervalHours = sync.lsfIntervalHours,
            lastLsfSyncEpoch = sync.lastLsf,
            lastLsfExamsRefreshEpoch = sync.lastLsfExams,
            lastMensaRefreshEpoch = sync.lastMensa,
            lastMoviesRefreshEpoch = sync.lastMovies,
            lastSportRefreshEpoch = sync.lastSport,
            lastEmailSyncEpoch = sync.lastEmail,
            runningSyncs = running,
            message = message,
            mailSwipeRightAction = appearance.swipeRight,
            mailSwipeLeftAction = appearance.swipeLeft,
            themeMode = appearance.theme,
            mailRequiresBiometric = appearance.mailRequiresBiometric,
            mailDeleteLocalOnly = appearance.mailDeleteLocalOnly,
            appIconVariant = appearance.appIconVariant
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private data class AppearanceBundle(
        val swipeRight: MailSwipeAction,
        val swipeLeft: MailSwipeAction,
        val theme: ThemeMode,
        val mailRequiresBiometric: Boolean,
        val mailDeleteLocalOnly: Boolean,
        val appIconVariant: String
    )

    fun selectLocation(id: Int) = viewModelScope.launch {
        settings.setMensaLocationId(id)
    }

    fun setReminderMinutes(minutes: Int) = viewModelScope.launch {
        settings.setNotificationMinutesBefore(minutes)
    }

    fun setSyncInterval(minutes: Int) = viewModelScope.launch {
        settings.setEmailSyncIntervalMinutes(minutes)
    }

    fun setMailSwipeRight(action: MailSwipeAction) = viewModelScope.launch {
        settings.setMailSwipeRightAction(action.storageKey)
    }

    fun setMailSwipeLeft(action: MailSwipeAction) = viewModelScope.launch {
        settings.setMailSwipeLeftAction(action.storageKey)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settings.setThemeMode(mode.storageKey)
    }

    /**
     * Switcht das Launcher-Icon. [AppIconManager.setVariant] persistiert die
     * Variante auch im DataStore, sodass der State-Flow oben automatisch das
     * Highlight in der Settings-UI nachzieht. DONT_KILL_APP-Flag sorgt
     * darunter dafür, dass die App nicht vom PackageManager gekillt wird.
     */
    fun setAppIcon(variant: String) = viewModelScope.launch {
        appIconManager.setVariant(variant)
        _message.value = "App-Icon geändert. Launcher braucht ggf. einen Moment, bis das neue Symbol auftaucht."
    }

    fun setMailRequiresBiometric(enabled: Boolean) = viewModelScope.launch {
        settings.setMailRequiresBiometric(enabled)
    }

    fun setMailDeleteLocalOnly(enabled: Boolean) = viewModelScope.launch {
        settings.setMailDeleteLocalOnly(enabled)
    }

    fun setLsfInterval(hours: Int) = viewModelScope.launch {
        settings.setLsfSyncIntervalHours(hours)
        lsfSyncScheduler.ensureScheduled(hours)
    }

    fun syncLsfNow() {
        if (SyncJob.LSF in _runningSyncs.value) return
        markRunning(SyncJob.LSF, true)
        lsfSyncScheduler.triggerNow()
        _message.value = "LSF-Sync gestartet."
        viewModelScope.launch {
            delay(WORKER_COOLDOWN_MS)
            markRunning(SyncJob.LSF, false)
        }
    }

    fun syncMensaNow() {
        if (SyncJob.MENSA in _runningSyncs.value) return
        markRunning(SyncJob.MENSA, true)
        viewModelScope.launch {
            try {
                mensaRepository.refresh(force = true)
                _message.value = "Mensa-Plan aktualisiert."
            } finally {
                delay(REPO_COOLDOWN_MS)
                markRunning(SyncJob.MENSA, false)
            }
        }
    }

    fun syncMoviesNow() {
        if (SyncJob.MOVIES in _runningSyncs.value) return
        markRunning(SyncJob.MOVIES, true)
        viewModelScope.launch {
            try {
                moviesRepository.refresh(force = true)
                _message.value = "Uni-Kino-Programm aktualisiert."
            } finally {
                delay(REPO_COOLDOWN_MS)
                markRunning(SyncJob.MOVIES, false)
            }
        }
    }

    fun syncSportNow() {
        if (SyncJob.SPORT in _runningSyncs.value) return
        markRunning(SyncJob.SPORT, true)
        sportSyncScheduler.triggerNow()
        _message.value = "Sport-Sync gestartet."
        viewModelScope.launch {
            delay(WORKER_COOLDOWN_MS)
            markRunning(SyncJob.SPORT, false)
        }
    }

    fun syncEmailNow() {
        if (SyncJob.EMAIL in _runningSyncs.value) return
        markRunning(SyncJob.EMAIL, true)
        viewModelScope.launch {
            try {
                emailRepository.refresh(force = true)
                _message.value = "Posteingang aktualisiert."
            } finally {
                delay(REPO_COOLDOWN_MS)
                markRunning(SyncJob.EMAIL, false)
            }
        }
    }

    /**
     * Feuert eine echte OS-Notification UND schreibt einen Eintrag ins
     * Push-Center — zum Validieren von beiden Pfaden ohne auf einen echten
     * Kalender-Reminder warten zu müssen.
     */
    fun sendTestNotification() {
        if (SyncJob.TEST_NOTIFY in _runningSyncs.value) return
        markRunning(SyncJob.TEST_NOTIFY, true)
        viewModelScope.launch {
            notificationPresenter.present(
                kind = NotificationKind.SYSTEM,
                title = "Test-Mitteilung",
                body = "Wenn du das hier siehst, funktioniert dein Push-Center.",
                refKey = "settings_test",
                systemId = NotificationPresenter.TEST_NOTIFICATION_ID
            )
            _message.value = "Test-Mitteilung gesendet."
            delay(TEST_NOTIFY_COOLDOWN_MS)
            markRunning(SyncJob.TEST_NOTIFY, false)
        }
    }

    fun updateUsername(value: String) {
        _draft.update { it.copy(username = value) }
    }

    fun updatePassword(value: String) {
        _draft.update { it.copy(password = value) }
    }

    fun saveCredentials() {
        val draft = _draft.value
        if (!draft.canSave) {
            _message.value = "Username und Passwort dürfen nicht leer sein."
            return
        }
        val ok = credentials.saveCredentials(draft.username, draft.password)
        if (ok) {
            _draft.value = CredentialsDraft(username = draft.username, password = "")
            _credentialsBump.value++
            _message.value = "Zugangsdaten gespeichert."
        } else {
            _message.value = "Speichern fehlgeschlagen. Diagnose: ${credentials.diagnose()}"
        }
    }

    fun clearCredentials() {
        if (credentials.clear()) {
            _draft.value = CredentialsDraft()
            _credentialsBump.value++
            _message.value = "Zugangsdaten gelöscht."
        } else {
            _message.value = "Konnte Zugangsdaten nicht löschen."
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
