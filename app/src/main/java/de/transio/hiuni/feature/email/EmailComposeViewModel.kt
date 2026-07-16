package de.transio.hiuni.feature.email

import androidx.lifecycle.SavedStateHandle
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
    val hasCredentials: Boolean = true,
    /** RFC 5322 In-Reply-To Header beim Reply. Null bei neuer Mail / Forward. */
    val inReplyTo: String? = null,
    /** RFC 5322 References Header (joined IDs) beim Reply. Null bei neuer Mail / Forward. */
    val references: String? = null
) {
    val canSend: Boolean
        get() = !isSending && (toChips.isNotEmpty() || LOOKS_LIKE_EMAIL.containsMatchIn(toDraft.trim())) && hasCredentials
    val isDirty: Boolean
        get() = toChips.isNotEmpty() || toDraft.isNotBlank() ||
            ccChips.isNotEmpty() || ccDraft.isNotBlank() ||
            bccChips.isNotEmpty() || bccDraft.isNotBlank() ||
            subject.isNotBlank() || body.isNotBlank()
}

/**
 * Persistiert den kompletten Entwurf (Empfänger/Betreff/Body/…) über [SavedStateHandle],
 * damit eine angefangene Mail einen Prozess-Tod (App im Hintergrund gekillt) überlebt.
 *
 * Der eigentliche `_state` bleibt die einzige Quelle der Wahrheit für die UI; nach jedem
 * Update spiegeln wir die *persistierbaren* Felder in den Handle. Transiente Felder
 * (isSending / sentMessage / errorMessage) landen bewusst NICHT im Handle — nach Prozess-
 * Tod soll der User seinen Entwurf sehen, keinen alten Sende-/Fehler-Zustand.
 *
 * Prefill (Reply/Forward): Der [EmailComposePrefillHolder] bleibt der (config-change-sichere)
 * Übergabekanal Detail → Compose, WIRD ABER nur einmal — bei Erst-Erzeugung des VM — in den
 * Handle gedrained. Danach ist der Handle die Quelle der Wahrheit und übersteht Prozess-Tod;
 * der Holder (nur `@Volatile`) tut das nicht. Wir bleiben bei Handle statt Nav-Args, weil ein
 * zitierter Reply-Body groß werden kann: der Handle-Bundle-Cap (~1MB) ist für Text-Entwürfe
 * bequem, wir kappen den Body zusätzlich defensiv auf [MAX_BODY_CHARS].
 */
@HiltViewModel
class EmailComposeViewModel @Inject constructor(
    private val repository: EmailRepository,
    private val credentialsManager: CredentialsManager,
    private val savedStateHandle: SavedStateHandle,
    prefillHolder: EmailComposePrefillHolder
) : ViewModel() {

    private val _state: MutableStateFlow<EmailComposeUiState>

    init {
        // Reihenfolge: erst geretteter Handle-Stand (Prozess-Tod), sonst frischer Prefill
        // aus dem Holder (Reply/Forward), sonst leere Mail.
        val restored = restoreFromHandle()
        val initial = when {
            restored != null -> restored
            else -> {
                // consume() ist destructive — beim nächsten „Verfassen“-Tap ist der
                // Holder wieder leer. Existiert kein Prefill → leere Mail.
                prefillHolder.consume().let { prefill ->
                    if (prefill == null) {
                        EmailComposeUiState(hasCredentials = credentialsManager.hasCredentials())
                    } else {
                        EmailComposeUiState(
                            toChips = prefill.to,
                            ccChips = prefill.cc,
                            bccChips = prefill.bcc,
                            showCcBcc = prefill.cc.isNotEmpty() || prefill.bcc.isNotEmpty(),
                            subject = prefill.subject,
                            body = prefill.body.take(MAX_BODY_CHARS),
                            inReplyTo = prefill.inReplyTo,
                            references = prefill.references,
                            hasCredentials = credentialsManager.hasCredentials()
                        )
                    }
                }
            }
        }
        _state = MutableStateFlow(initial)
        persist(initial)
    }

    val state: StateFlow<EmailComposeUiState> get() = _state.asStateFlow()

    /** Bekannte Kontakte aus dem Inbox-Verlauf (max 500 Mails) für Autocomplete. */
    val knownContacts: StateFlow<List<EmailContact>> = repository.observeKnownContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())

    /**
     * Alle State-Mutationen laufen hierüber, damit nach jedem Update die persistierbaren
     * Felder in den Handle gespiegelt werden. `hasCredentials` wird bei jeder Änderung frisch
     * nachgezogen (kann sich hinter dem Screen ändern, z.B. Login in Settings).
     */
    private inline fun mutate(transform: (EmailComposeUiState) -> EmailComposeUiState) {
        _state.update { current ->
            val next = transform(current)
            persist(next)
            next
        }
    }

    // ── Draft-Updates ──────────────────────────────────────────────────────
    fun updateToDraft(value: String) = mutate { it.copy(toDraft = value) }
    fun updateCcDraft(value: String) = mutate { it.copy(ccDraft = value) }
    fun updateBccDraft(value: String) = mutate { it.copy(bccDraft = value) }

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
        mutate { st ->
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
    fun removeToChip(index: Int) = mutate {
        it.copy(toChips = it.toChips.removeAt(index))
    }

    fun removeCcChip(index: Int) = mutate {
        it.copy(ccChips = it.ccChips.removeAt(index))
    }

    fun removeBccChip(index: Int) = mutate {
        it.copy(bccChips = it.bccChips.removeAt(index))
    }

    fun popToChip() = mutate {
        if (it.toChips.isEmpty()) it else it.copy(toChips = it.toChips.dropLast(1))
    }

    fun popCcChip() = mutate {
        if (it.ccChips.isEmpty()) it else it.copy(ccChips = it.ccChips.dropLast(1))
    }

    fun popBccChip() = mutate {
        if (it.bccChips.isEmpty()) it else it.copy(bccChips = it.bccChips.dropLast(1))
    }

    // ── Suggestion-Tap → direkt Chip anhängen ──────────────────────────────
    fun applyToSuggestion(contact: EmailContact) = mutate {
        val chips = if (it.toChips.contains(contact.address)) it.toChips
        else it.toChips + contact.address
        it.copy(toChips = chips, toDraft = "")
    }

    fun applyCcSuggestion(contact: EmailContact) = mutate {
        val chips = if (it.ccChips.contains(contact.address)) it.ccChips
        else it.ccChips + contact.address
        it.copy(ccChips = chips, ccDraft = "")
    }

    fun applyBccSuggestion(contact: EmailContact) = mutate {
        val chips = if (it.bccChips.contains(contact.address)) it.bccChips
        else it.bccChips + contact.address
        it.copy(bccChips = chips, bccDraft = "")
    }

    // ── Sonstiges ──────────────────────────────────────────────────────────
    fun updateSubject(value: String) = mutate { it.copy(subject = value) }
    fun updateBody(value: String) = mutate { it.copy(body = value.take(MAX_BODY_CHARS)) }
    fun toggleCcBcc() = mutate { it.copy(showCcBcc = !it.showCcBcc) }

    fun consumeMessage() = mutate {
        it.copy(sentMessage = null, errorMessage = null)
    }

    /**
     * Bewusstes Verwerfen des Entwurfs (z.B. „Verwerfen“ im Discard-Dialog). Räumt den
     * gesicherten State, damit ein späterer Prozess-Tod keine verworfene Mail wiederbelebt.
     */
    fun discardDraft() {
        clearHandle()
        val cleared = EmailComposeUiState(hasCredentials = credentialsManager.hasCredentials())
        _state.value = cleared
        persist(cleared)
    }

    fun send() = viewModelScope.launch {
        val current = _state.value
        if (!credentialsManager.hasCredentials()) {
            mutate {
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
            mutate { it.copy(errorMessage = "Ungültige Adresse: ${current.toDraft.trim()}") }
            return@launch
        }
        if (toFinal.isEmpty()) {
            mutate { it.copy(errorMessage = "Mindestens ein Empfänger nötig.") }
            return@launch
        }
        val ccFinal = finalize(current.ccChips, current.ccDraft) ?: run {
            mutate { it.copy(errorMessage = "Ungültige Adresse: ${current.ccDraft.trim()}") }
            return@launch
        }
        val bccFinal = finalize(current.bccChips, current.bccDraft) ?: run {
            mutate { it.copy(errorMessage = "Ungültige Adresse: ${current.bccDraft.trim()}") }
            return@launch
        }

        mutate { it.copy(isSending = true, errorMessage = null) }
        Timber.i(
            "Compose send to=${toFinal.size} cc=${ccFinal.size} bcc=${bccFinal.size} " +
                "reply=${current.inReplyTo != null}"
        )
        when (val result = repository.sendMail(
            to = toFinal,
            cc = ccFinal,
            bcc = bccFinal,
            subject = current.subject,
            body = current.body,
            inReplyTo = current.inReplyTo,
            references = current.references
        )) {
            is AppResult.Success -> {
                // Erfolgreich versendet → gesicherten Entwurf räumen, damit ein
                // späterer Prozess-Tod die schon-gesendete Mail nicht wiederbelebt.
                clearHandle()
                val sent = EmailComposeUiState(
                    sentMessage = "Mail gesendet.",
                    hasCredentials = current.hasCredentials
                )
                _state.value = sent
                persist(sent)
            }
            is AppResult.Failure -> mutate {
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

    // ── SavedStateHandle-Persistenz ─────────────────────────────────────────

    /**
     * Spiegelt die persistierbaren Felder in den Handle. Transiente Sende-/Meldungs-Felder
     * bleiben absichtlich draußen — nach Prozess-Tod soll der Entwurf zurückkommen, nicht ein
     * alter „wird gesendet“/„Fehler“-Zustand. `hasCredentials` wird beim Restore frisch geprüft.
     */
    private fun persist(s: EmailComposeUiState) {
        savedStateHandle[KEY_HAS_DRAFT] = true
        savedStateHandle[KEY_TO_CHIPS] = ArrayList(s.toChips)
        savedStateHandle[KEY_TO_DRAFT] = s.toDraft
        savedStateHandle[KEY_CC_CHIPS] = ArrayList(s.ccChips)
        savedStateHandle[KEY_CC_DRAFT] = s.ccDraft
        savedStateHandle[KEY_BCC_CHIPS] = ArrayList(s.bccChips)
        savedStateHandle[KEY_BCC_DRAFT] = s.bccDraft
        savedStateHandle[KEY_SUBJECT] = s.subject
        savedStateHandle[KEY_BODY] = s.body
        savedStateHandle[KEY_SHOW_CC_BCC] = s.showCcBcc
        savedStateHandle[KEY_IN_REPLY_TO] = s.inReplyTo
        savedStateHandle[KEY_REFERENCES] = s.references
    }

    /**
     * Rekonstruiert den Entwurf aus dem Handle nach Prozess-Tod. Gibt null zurück, wenn noch
     * nie etwas persistiert wurde (Erst-Erzeugung ohne Prozess-Tod) → dann greift der Prefill.
     */
    private fun restoreFromHandle(): EmailComposeUiState? {
        if (savedStateHandle.get<Boolean>(KEY_HAS_DRAFT) != true) return null
        return EmailComposeUiState(
            toChips = savedStateHandle.get<ArrayList<String>>(KEY_TO_CHIPS).orEmpty(),
            toDraft = savedStateHandle.get<String>(KEY_TO_DRAFT).orEmpty(),
            ccChips = savedStateHandle.get<ArrayList<String>>(KEY_CC_CHIPS).orEmpty(),
            ccDraft = savedStateHandle.get<String>(KEY_CC_DRAFT).orEmpty(),
            bccChips = savedStateHandle.get<ArrayList<String>>(KEY_BCC_CHIPS).orEmpty(),
            bccDraft = savedStateHandle.get<String>(KEY_BCC_DRAFT).orEmpty(),
            subject = savedStateHandle.get<String>(KEY_SUBJECT).orEmpty(),
            body = savedStateHandle.get<String>(KEY_BODY).orEmpty(),
            showCcBcc = savedStateHandle.get<Boolean>(KEY_SHOW_CC_BCC) ?: false,
            inReplyTo = savedStateHandle.get<String>(KEY_IN_REPLY_TO),
            references = savedStateHandle.get<String>(KEY_REFERENCES),
            hasCredentials = credentialsManager.hasCredentials()
        )
    }

    private fun clearHandle() {
        savedStateHandle[KEY_HAS_DRAFT] = false
        savedStateHandle.remove<ArrayList<String>>(KEY_TO_CHIPS)
        savedStateHandle.remove<String>(KEY_TO_DRAFT)
        savedStateHandle.remove<ArrayList<String>>(KEY_CC_CHIPS)
        savedStateHandle.remove<String>(KEY_CC_DRAFT)
        savedStateHandle.remove<ArrayList<String>>(KEY_BCC_CHIPS)
        savedStateHandle.remove<String>(KEY_BCC_DRAFT)
        savedStateHandle.remove<String>(KEY_SUBJECT)
        savedStateHandle.remove<String>(KEY_BODY)
        savedStateHandle.remove<Boolean>(KEY_SHOW_CC_BCC)
        savedStateHandle.remove<String>(KEY_IN_REPLY_TO)
        savedStateHandle.remove<String>(KEY_REFERENCES)
    }

    private companion object {
        /**
         * Defensiver Cap für den Body. SavedState landet in einem Bundle mit ~1MB Prozess-Cap;
         * ein zitierter Reply-Body bleibt normalerweise weit darunter, aber wir kappen, um selbst
         * bei extrem langen Zitaten nicht die TransactionTooLargeException zu riskieren.
         */
        const val MAX_BODY_CHARS = 100_000

        const val KEY_HAS_DRAFT = "compose_has_draft"
        const val KEY_TO_CHIPS = "compose_to_chips"
        const val KEY_TO_DRAFT = "compose_to_draft"
        const val KEY_CC_CHIPS = "compose_cc_chips"
        const val KEY_CC_DRAFT = "compose_cc_draft"
        const val KEY_BCC_CHIPS = "compose_bcc_chips"
        const val KEY_BCC_DRAFT = "compose_bcc_draft"
        const val KEY_SUBJECT = "compose_subject"
        const val KEY_BODY = "compose_body"
        const val KEY_SHOW_CC_BCC = "compose_show_cc_bcc"
        const val KEY_IN_REPLY_TO = "compose_in_reply_to"
        const val KEY_REFERENCES = "compose_references"
    }
}
