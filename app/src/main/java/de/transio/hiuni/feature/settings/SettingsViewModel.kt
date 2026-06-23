package de.transio.hiuni.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.security.CredentialsManager
import de.transio.hiuni.core.sync.LsfSyncScheduler
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
    private val lsfSyncScheduler: LsfSyncScheduler
) : ViewModel() {

    private val _draft = MutableStateFlow(CredentialsDraft())
    private val _message = MutableStateFlow<String?>(null)
    private val _credentialsBump = MutableStateFlow(0)

    /**
     * LSF-Settings als Triple zusammengefasst, damit wir mit dem 5-Slot-`combine`
     * unter dem Arity-Limit bleiben.
     */
    private data class LsfBundle(val intervalHours: Int, val lastSyncEpoch: Long)

    private val lsfBundle = combine(
        settings.lsfSyncIntervalHours,
        settings.lastLsfSyncEpoch
    ) { hours, epoch -> LsfBundle(hours, epoch) }

    val state: StateFlow<SettingsUiState> = combine(
        settings.mensaLocationId,
        settings.notificationMinutesBefore,
        settings.emailSyncIntervalMinutes,
        combine(_draft, _credentialsBump, _message) { d, _, msg -> d to msg },
        lsfBundle
    ) { locationId, reminderMinutes, syncInterval, draftAndMessage, lsf ->
        val (draft, message) = draftAndMessage
        SettingsUiState(
            selectedLocationId = locationId,
            notificationMinutesBefore = reminderMinutes,
            emailSyncIntervalMinutes = syncInterval,
            emailUsername = credentials.getUsername().orEmpty(),
            hasStoredCredentials = credentials.hasCredentials(),
            credentialsDraft = draft,
            lsfSyncIntervalHours = lsf.intervalHours,
            lastLsfSyncEpoch = lsf.lastSyncEpoch,
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
