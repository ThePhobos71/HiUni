package de.transio.hiuni.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.NotificationPresenter
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.core.security.CredentialsManager
import de.transio.hiuni.core.sync.LsfSyncScheduler
import de.transio.hiuni.core.sync.SportSyncScheduler
import de.transio.hiuni.feature.email.data.EmailRepository
import de.transio.hiuni.feature.mensa.data.MensaRepository
import de.transio.hiuni.feature.movies.data.MoviesRepository
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
    private val notificationPresenter: NotificationPresenter
) : ViewModel() {

    private val _draft = MutableStateFlow(CredentialsDraft())
    private val _message = MutableStateFlow<String?>(null)
    private val _credentialsBump = MutableStateFlow(0)

    /**
     * Alle Sync-Job-Timestamps + LSF-Intervall in einem Bundle. Sonst sprengen
     * wir das 5-Slot-Arity-Limit von [combine].
     */
    private data class SyncBundle(
        val lsfIntervalHours: Int,
        val lastLsf: Long,
        val lastMensa: Long,
        val lastMovies: Long,
        val lastSport: Long,
        val lastEmail: Long
    )

    private val syncBundle = combine(
        combine(settings.lsfSyncIntervalHours, settings.lastLsfSyncEpoch) { i, e -> i to e },
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
            lastMensa = others[0],
            lastMovies = others[1],
            lastSport = others[2],
            lastEmail = others[3]
        )
    }

    val state: StateFlow<SettingsUiState> = combine(
        settings.mensaLocationId,
        settings.notificationMinutesBefore,
        settings.emailSyncIntervalMinutes,
        combine(_draft, _credentialsBump, _message) { d, _, msg -> d to msg },
        syncBundle
    ) { locationId, reminderMinutes, syncInterval, draftAndMessage, sync ->
        val (draft, message) = draftAndMessage
        SettingsUiState(
            selectedLocationId = locationId,
            notificationMinutesBefore = reminderMinutes,
            emailSyncIntervalMinutes = syncInterval,
            emailUsername = credentials.getUsername().orEmpty(),
            hasStoredCredentials = credentials.hasCredentials(),
            credentialsDraft = draft,
            lsfSyncIntervalHours = sync.lsfIntervalHours,
            lastLsfSyncEpoch = sync.lastLsf,
            lastMensaRefreshEpoch = sync.lastMensa,
            lastMoviesRefreshEpoch = sync.lastMovies,
            lastSportRefreshEpoch = sync.lastSport,
            lastEmailSyncEpoch = sync.lastEmail,
            message = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun selectLocation(id: Int) = viewModelScope.launch {
        settings.setMensaLocationId(id)
    }

    fun setReminderMinutes(minutes: Int) = viewModelScope.launch {
        settings.setNotificationMinutesBefore(minutes)
    }

    fun setSyncInterval(minutes: Int) = viewModelScope.launch {
        settings.setEmailSyncIntervalMinutes(minutes)
    }

    fun setLsfInterval(hours: Int) = viewModelScope.launch {
        settings.setLsfSyncIntervalHours(hours)
        lsfSyncScheduler.ensureScheduled(hours)
    }

    fun syncLsfNow() {
        lsfSyncScheduler.triggerNow()
        _message.value = "LSF-Sync gestartet."
    }

    fun syncMensaNow() = viewModelScope.launch {
        mensaRepository.refresh(force = true)
        _message.value = "Mensa-Plan aktualisiert."
    }

    fun syncMoviesNow() = viewModelScope.launch {
        moviesRepository.refresh(force = true)
        _message.value = "Uni-Kino-Programm aktualisiert."
    }

    fun syncSportNow() {
        sportSyncScheduler.triggerNow()
        _message.value = "Sport-Sync gestartet."
    }

    fun syncEmailNow() = viewModelScope.launch {
        emailRepository.refresh(force = true)
        _message.value = "Posteingang aktualisiert."
    }

    /**
     * Feuert eine echte OS-Notification UND schreibt einen Eintrag ins
     * Push-Center — zum Validieren von beiden Pfaden ohne auf einen echten
     * Kalender-Reminder warten zu müssen.
     */
    fun sendTestNotification() = viewModelScope.launch {
        notificationPresenter.present(
            kind = NotificationKind.SYSTEM,
            title = "Test-Mitteilung",
            body = "Wenn du das hier siehst, funktioniert dein Push-Center.",
            refKey = "settings_test",
            systemId = NotificationPresenter.TEST_NOTIFICATION_ID
        )
        _message.value = "Test-Mitteilung gesendet."
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
