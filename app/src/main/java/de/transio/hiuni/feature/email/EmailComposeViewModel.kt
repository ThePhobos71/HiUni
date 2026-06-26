package de.transio.hiuni.feature.email

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.security.CredentialsManager
import de.transio.hiuni.feature.email.data.EmailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class EmailComposeUiState(
    val to: String = "",
    val cc: String = "",
    val bcc: String = "",
    val subject: String = "",
    val body: String = "",
    val showCcBcc: Boolean = false,
    val isSending: Boolean = false,
    val sentMessage: String? = null,
    val errorMessage: String? = null,
    val hasCredentials: Boolean = true
) {
    val canSend: Boolean get() = !isSending && to.isNotBlank() && hasCredentials
    val isDirty: Boolean
        get() = to.isNotBlank() || cc.isNotBlank() || bcc.isNotBlank() ||
            subject.isNotBlank() || body.isNotBlank()
}

@HiltViewModel
class EmailComposeViewModel @Inject constructor(
    private val repository: EmailRepository,
    private val credentialsManager: CredentialsManager
) : ViewModel() {

    private val _state = MutableStateFlow(
        EmailComposeUiState(hasCredentials = credentialsManager.hasCredentials())
    )
    val state: StateFlow<EmailComposeUiState> = _state.asStateFlow()

    fun updateTo(value: String) = _state.update { it.copy(to = value) }
    fun updateCc(value: String) = _state.update { it.copy(cc = value) }
    fun updateBcc(value: String) = _state.update { it.copy(bcc = value) }
    fun updateSubject(value: String) = _state.update { it.copy(subject = value) }
    fun updateBody(value: String) = _state.update { it.copy(body = value) }
    fun toggleCcBcc() = _state.update { it.copy(showCcBcc = !it.showCcBcc) }

    fun consumeMessage() = _state.update {
        it.copy(sentMessage = null, errorMessage = null)
    }

    fun send() = viewModelScope.launch {
        val current = _state.value
        if (!credentialsManager.hasCredentials()) {
            _state.update {
                it.copy(
                    hasCredentials = false,
                    errorMessage = "Keine Zugangsdaten — bitte in Settings einloggen"
                )
            }
            return@launch
        }
        val toList = splitAddresses(current.to)
        if (toList.isEmpty()) {
            _state.update { it.copy(errorMessage = "Mindestens ein Empfänger nötig.") }
            return@launch
        }
        val invalidTo = toList.firstOrNull { !LOOKS_LIKE_EMAIL.containsMatchIn(it) }
        if (invalidTo != null) {
            _state.update { it.copy(errorMessage = "Ungültige Adresse: $invalidTo") }
            return@launch
        }
        val ccList = splitAddresses(current.cc)
        val bccList = splitAddresses(current.bcc)
        val firstInvalidExtra = (ccList + bccList).firstOrNull { !LOOKS_LIKE_EMAIL.containsMatchIn(it) }
        if (firstInvalidExtra != null) {
            _state.update { it.copy(errorMessage = "Ungültige Adresse: $firstInvalidExtra") }
            return@launch
        }

        _state.update { it.copy(isSending = true, errorMessage = null) }
        Timber.i("Compose send to=${toList.size} cc=${ccList.size} bcc=${bccList.size}")
        when (val result = repository.sendMail(
            to = toList,
            cc = ccList,
            bcc = bccList,
            subject = current.subject,
            body = current.body
        )) {
            is AppResult.Success -> _state.update {
                EmailComposeUiState(
                    sentMessage = "Mail gesendet.",
                    hasCredentials = it.hasCredentials
                )
            }
            is AppResult.Failure -> _state.update {
                it.copy(
                    isSending = false,
                    errorMessage = "Fehlgeschlagen: ${result.error.message ?: "Unbekannter Fehler"}"
                )
            }
        }
    }

    companion object {
        // Reichhaltige Adressliste-Eingabe-Parsing: User tippt "kjell@..., max@..."
        // oder semicolon-getrennt, mit Leerzeichen, mit Doppel-Kommas — alles wird
        // robust normalisiert. KEINE Display-Name-Parsing-Logik in v1 ("Max <max@…>"
        // gibt's nicht), das ist Feature-Creep.
        private val ADDRESS_SEPARATOR = Regex("[,;]")
        private val LOOKS_LIKE_EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

        internal fun splitAddresses(raw: String): List<String> =
            raw.split(ADDRESS_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
    }
}
