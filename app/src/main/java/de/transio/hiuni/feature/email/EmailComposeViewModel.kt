package de.transio.hiuni.feature.email

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.security.CredentialsManager
import de.transio.hiuni.feature.email.data.EmailContact
import de.transio.hiuni.feature.email.data.EmailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Adress-Form als Regex: irgendwas@irgendwas.irgendwas, ohne Whitespace im Local-/Domain-Part.
 * Public, damit der UiState (und das UI) sich selbst gegen den noch-im-Draft tippenden
 * User absichern kann ohne den ViewModel zu fragen. KEINE display-name-Parsing-Logik.
 */
internal val LOOKS_LIKE_EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

data class EmailComposeUiState(
    val toChips: List<String> = emptyList(),
    val toDraft: String = "",
    val ccChips: List<String> = emptyList(),
    val ccDraft: String = "",
    val bccChips: List<String> = emptyList(),
    val bccDraft: String = "",
    val subject: String = "",
    val body: String = "",
    val showCcBcc: Boolean = false,
    val isSending: Boolean = false,
    val sentMessage: String? = null,
    val errorMessage: String? = null,
    val hasCredentials: Boolean = true
) {
    val canSend: Boolean
        get() = !isSending && (toChips.isNotEmpty() || LOOKS_LIKE_EMAIL.containsMatchIn(toDraft.trim())) && hasCredentials
    val isDirty: Boolean
        get() = toChips.isNotEmpty() || toDraft.isNotBlank() ||
            ccChips.isNotEmpty() || ccDraft.isNotBlank() ||
            bccChips.isNotEmpty() || bccDraft.isNotBlank() ||
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

    /** Bekannte Kontakte aus dem Inbox-Verlauf (max 500 Mails) für Autocomplete. */
    val knownContacts: StateFlow<List<EmailContact>> = repository.observeKnownContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Draft-Updates ──────────────────────────────────────────────────────
    fun updateToDraft(value: String) = _state.update { it.copy(toDraft = value) }
    fun updateCcDraft(value: String) = _state.update { it.copy(ccDraft = value) }
    fun updateBccDraft(value: String) = _state.update { it.copy(bccDraft = value) }

    // ── Commit Draft → Chip ────────────────────────────────────────────────
    fun commitToChip() = commitDraft(
        getDraft = { it.toDraft },
        getChips = { it.toChips },
        setChipsAndDraft = { st, chips, draft -> st.copy(toChips = chips, toDraft = draft) }
    )

    fun commitCcChip() = commitDraft(
        getDraft = { it.ccDraft },
        getChips = { it.ccChips },
        setChipsAndDraft = { st, chips, draft -> st.copy(ccChips = chips, ccDraft = draft) }
    )

    fun commitBccChip() = commitDraft(
        getDraft = { it.bccDraft },
        getChips = { it.bccChips },
        setChipsAndDraft = { st, chips, draft -> st.copy(bccChips = chips, bccDraft = draft) }
    )

    private inline fun commitDraft(
        crossinline getDraft: (EmailComposeUiState) -> String,
        crossinline getChips: (EmailComposeUiState) -> List<String>,
        crossinline setChipsAndDraft: (EmailComposeUiState, List<String>, String) -> EmailComposeUiState
    ) {
        _state.update { st ->
            val raw = getDraft(st).trim().trimEnd(',', ';')
            val candidate = raw.trim()
            if (candidate.isEmpty()) {
                // Nichts zu committen — Draft komplett leeren (Whitespace-only).
                setChipsAndDraft(st, getChips(st), "")
            } else if (!LOOKS_LIKE_EMAIL.containsMatchIn(candidate)) {
                // Ungültig: Draft bleibt stehen, Fehler-Snackbar zeigen.
                st.copy(errorMessage = "Ungültige Adresse: $candidate")
            } else if (getChips(st).contains(candidate)) {
                // Duplikat → nicht erneut anhängen, aber Draft trotzdem clearen.
                setChipsAndDraft(st, getChips(st), "")
            } else {
                setChipsAndDraft(st, getChips(st) + candidate, "")
            }
        }
    }

    // ── Chip-Entfernung ────────────────────────────────────────────────────
    fun removeToChip(index: Int) = _state.update {
        it.copy(toChips = it.toChips.removeAt(index))
    }

    fun removeCcChip(index: Int) = _state.update {
        it.copy(ccChips = it.ccChips.removeAt(index))
    }

    fun removeBccChip(index: Int) = _state.update {
        it.copy(bccChips = it.bccChips.removeAt(index))
    }

    fun popToChip() = _state.update {
        if (it.toChips.isEmpty()) it else it.copy(toChips = it.toChips.dropLast(1))
    }

    fun popCcChip() = _state.update {
        if (it.ccChips.isEmpty()) it else it.copy(ccChips = it.ccChips.dropLast(1))
    }

    fun popBccChip() = _state.update {
        if (it.bccChips.isEmpty()) it else it.copy(bccChips = it.bccChips.dropLast(1))
    }

    // ── Suggestion-Tap → direkt Chip anhängen ──────────────────────────────
    fun applyToSuggestion(contact: EmailContact) = _state.update {
        val chips = if (it.toChips.contains(contact.address)) it.toChips
        else it.toChips + contact.address
        it.copy(toChips = chips, toDraft = "")
    }

    fun applyCcSuggestion(contact: EmailContact) = _state.update {
        val chips = if (it.ccChips.contains(contact.address)) it.ccChips
        else it.ccChips + contact.address
        it.copy(ccChips = chips, ccDraft = "")
    }

    fun applyBccSuggestion(contact: EmailContact) = _state.update {
        val chips = if (it.bccChips.contains(contact.address)) it.bccChips
        else it.bccChips + contact.address
        it.copy(bccChips = chips, bccDraft = "")
    }

    // ── Sonstiges ──────────────────────────────────────────────────────────
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

        // Drafts wie Pseudo-Chips behandeln: wenn non-blank, müssen sie gültig
        // sein — sonst Abort. So muss der User nicht zwingend „Enter" drücken,
        // bevor er „Senden" klickt.
        val toFinal = finalize(current.toChips, current.toDraft) ?: run {
            _state.update { it.copy(errorMessage = "Ungültige Adresse: ${current.toDraft.trim()}") }
            return@launch
        }
        if (toFinal.isEmpty()) {
            _state.update { it.copy(errorMessage = "Mindestens ein Empfänger nötig.") }
            return@launch
        }
        val ccFinal = finalize(current.ccChips, current.ccDraft) ?: run {
            _state.update { it.copy(errorMessage = "Ungültige Adresse: ${current.ccDraft.trim()}") }
            return@launch
        }
        val bccFinal = finalize(current.bccChips, current.bccDraft) ?: run {
            _state.update { it.copy(errorMessage = "Ungültige Adresse: ${current.bccDraft.trim()}") }
            return@launch
        }

        _state.update { it.copy(isSending = true, errorMessage = null) }
        Timber.i("Compose send to=${toFinal.size} cc=${ccFinal.size} bcc=${bccFinal.size}")
        when (val result = repository.sendMail(
            to = toFinal,
            cc = ccFinal,
            bcc = bccFinal,
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

    /**
     * Liefert `chips + (draft falls non-blank & gültig)`. Wenn der Draft non-blank
     * aber ungültig ist → null (Caller meldet Fehler).
     */
    private fun finalize(chips: List<String>, draft: String): List<String>? {
        val trimmed = draft.trim().trimEnd(',', ';').trim()
        if (trimmed.isEmpty()) return chips
        if (!LOOKS_LIKE_EMAIL.containsMatchIn(trimmed)) return null
        if (chips.contains(trimmed)) return chips
        return chips + trimmed
    }

    private fun <T> List<T>.removeAt(index: Int): List<T> =
        if (index !in indices) this else toMutableList().also { it.removeAt(index) }
}
