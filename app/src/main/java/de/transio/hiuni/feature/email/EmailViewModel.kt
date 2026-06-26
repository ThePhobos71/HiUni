package de.transio.hiuni.feature.email

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.security.CredentialsManager
import de.transio.hiuni.feature.calendar.data.CalendarRepository
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.email.data.EmailAttachment
import de.transio.hiuni.feature.email.data.EmailAttachments
import de.transio.hiuni.feature.email.data.EmailEntity
import de.transio.hiuni.feature.email.data.EmailRepository
import de.transio.hiuni.feature.email.data.IcsInvite
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class EmailViewModel @Inject constructor(
    private val repository: EmailRepository,
    private val credentialsManager: CredentialsManager,
    private val calendarRepository: CalendarRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _folder = MutableStateFlow(EmailFolder.INBOX)
    private val _isSearchOpen = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedId = MutableStateFlow<Long?>(null)
    private val _bodyPlain = MutableStateFlow<String?>(null)
    private val _bodyHtml = MutableStateFlow<String?>(null)
    private val _attachments = MutableStateFlow<List<EmailAttachment>>(emptyList())
    private val _invite = MutableStateFlow<IcsInvite?>(null)
    private val _downloadingPart = MutableStateFlow<Int?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _isLoadingBody = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _info = MutableStateFlow<String?>(null)
    private val _hasCredentials = MutableStateFlow(credentialsManager.hasCredentials())

    // Debounce nur den Query-Stream — Folder-Wechsel sollen sofort durchschlagen,
    // damit die Liste nicht "hängt" wenn man zwischen Posteingang/Gesendet/Markiert
    // wechselt während noch Text im Suchfeld steht.
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val debouncedQuery = _searchQuery
        .debounce(200)
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val emailsFlow = combine(_folder, debouncedQuery) { folder, query ->
        folder to query
    }.flatMapLatest { (folder, query) ->
        repository.observeSearch(folder, query)
    }

    val state: StateFlow<EmailUiState> = combine(
        combine(_folder, emailsFlow, _selectedId) { f, list, id -> Triple(f, list, id) },
        combine(_bodyPlain, _bodyHtml, _attachments, _invite) { p, h, a, inv -> BodyBundle(p, h, a, inv) },
        combine(_isRefreshing, _isLoadingBody, _downloadingPart) { r, lb, d -> Triple(r, lb, d) },
        combine(_error, _info, _hasCredentials) { e, i, hc -> Triple(e, i, hc) },
        combine(_isSearchOpen, _searchQuery) { open, q -> open to q }
    ) { folderAndList, body, flagsTriple, errInfoCreds, search ->
        val (folder, emails, selectedId) = folderAndList
        val (refreshing, loadingBody, downloading) = flagsTriple
        val (error, info, hasCreds) = errInfoCreds
        val (searchOpen, searchQuery) = search
        val selectedEmail = selectedId?.let { id -> emails.firstOrNull { it.rowId == id } }
        EmailUiState(
            folder = folder,
            emails = emails,
            selectedEmail = selectedEmail,
            selectedBodyPlain = if (selectedId != null) body.plain else null,
            selectedBodyHtml = if (selectedId != null) body.html else null,
            selectedAttachments = if (selectedId != null) body.attachments else emptyList(),
            selectedInvite = if (selectedId != null) body.invite else null,
            isRefreshing = refreshing,
            isLoadingBody = loadingBody,
            downloadingPartIndex = downloading,
            errorMessage = error,
            infoMessage = info,
            hasCredentials = hasCreds,
            isSearchOpen = searchOpen,
            searchQuery = searchQuery
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmailUiState())

    init {
        if (_hasCredentials.value) refresh(force = false)
    }

    fun selectFolder(folder: EmailFolder) { _folder.update { folder } }

    fun openSearch() { _isSearchOpen.value = true }

    fun closeSearch() {
        _isSearchOpen.value = false
        _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun openEmail(email: EmailEntity) = viewModelScope.launch {
        _selectedId.update { email.rowId }
        _bodyPlain.update { email.bodyPlain }
        _bodyHtml.update { email.bodyHtml }
        _attachments.update { EmailAttachments.decode(email.attachmentsJson) }
        _invite.update { null }
        if (!email.isRead) repository.markRead(email.rowId, true)
        if (email.bodyPlain.isNullOrBlank() && email.bodyHtml.isNullOrBlank()) {
            _isLoadingBody.update { true }
            val result = runCatching { repository.loadBody(email.rowId) }.getOrNull()
            _bodyPlain.update { result?.plain }
            _bodyHtml.update { result?.html }
            _attachments.update { result?.attachments.orEmpty() }
            _isLoadingBody.update { false }
        }
        // ICS-Invite parsen, falls Anhang vom Typ text/calendar dabei ist.
        val icsAttachment = _attachments.value.firstOrNull {
            it.mimeType.contains("calendar", ignoreCase = true) ||
                it.filename.endsWith(".ics", ignoreCase = true)
        }
        Timber.i(
            "openEmail uid=${email.uid} attachments=${_attachments.value.size} " +
                "icsCandidate=${icsAttachment?.filename}"
        )
        if (icsAttachment != null) {
            val parsed = runCatching { repository.loadIcsInvite(email, icsAttachment) }
                .onFailure { Timber.w(it, "ICS-Invite parsing failed for uid=${email.uid}") }
                .getOrNull()
            Timber.i("ICS-Invite for uid=${email.uid}: $parsed")
            _invite.update { parsed }
        }
    }

    fun closeEmail() {
        _selectedId.update { null }
        _bodyPlain.update { null }
        _bodyHtml.update { null }
        _attachments.update { emptyList() }
        _invite.update { null }
    }

    fun toggleStar(email: EmailEntity) = viewModelScope.launch {
        repository.toggleStar(email)
    }

    fun deleteEmail(email: EmailEntity) = viewModelScope.launch {
        when (val result = repository.deleteEmail(email)) {
            is AppResult.Success -> _info.value = "Mail gelöscht"
            is AppResult.Failure -> _error.value =
                result.error.message ?: "Mail konnte nicht gelöscht werden"
        }
    }

    fun archiveEmail(email: EmailEntity) = viewModelScope.launch {
        when (val result = repository.archiveEmail(email)) {
            is AppResult.Success -> _info.value = "Mail archiviert"
            is AppResult.Failure -> _error.value =
                result.error.message ?: "Mail konnte nicht archiviert werden"
        }
    }

    fun openAttachment(attachment: EmailAttachment) = viewModelScope.launch {
        val email = _selectedId.value?.let { id -> state.value.emails.firstOrNull { it.rowId == id } }
            ?: return@launch
        _downloadingPart.update { attachment.partIndex }
        try {
            val file = repository.downloadAttachment(email, attachment)
            val uri = repository.shareableUri(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, attachment.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ContextCompat.startActivity(context, intent, null)
        } catch (t: Throwable) {
            Timber.w(t, "Anhang öffnen fehlgeschlagen")
            _error.update { "Anhang konnte nicht geöffnet werden: ${t.message}" }
        } finally {
            _downloadingPart.update { null }
        }
    }

    fun addInviteToCalendar(invite: IcsInvite) = viewModelScope.launch {
        val title = invite.summary?.takeIf { it.isNotBlank() } ?: "Termineinladung"
        val start = invite.start ?: run {
            _error.update { "Termin ohne Startzeit — kann nicht gespeichert werden" }
            return@launch
        }
        val end = invite.end ?: start.plus(Duration.ofHours(1))
        calendarRepository.upsert(
            CustomEventEntity(
                title = title,
                description = invite.description,
                location = invite.location,
                startTime = start,
                endTime = end,
                sourceKind = CustomEventEntity.SOURCE_USER,
                sourceReference = invite.organizer,
                reminderMinutesBefore = 15
            )
        )
        _info.update { "Termin in Kalender gespeichert" }
    }

    fun refresh(force: Boolean = true) = viewModelScope.launch {
        if (!credentialsManager.hasCredentials()) {
            _error.value = "Bitte Uni-Mail-Zugang in den Einstellungen hinterlegen"
            _hasCredentials.value = false
            return@launch
        }
        _hasCredentials.value = true
        _isRefreshing.value = true
        _error.value = null
        when (val result = repository.refresh(force = force)) {
            is AppResult.Success -> Unit
            is AppResult.Failure -> _error.value =
                result.error.message ?: "Mailbox nicht erreichbar"
        }
        _isRefreshing.value = false
    }

    fun consumeError() { _error.update { null } }
    fun consumeInfo() { _info.update { null } }

    private data class BodyBundle(
        val plain: String?,
        val html: String?,
        val attachments: List<EmailAttachment>,
        val invite: IcsInvite?
    )
}
