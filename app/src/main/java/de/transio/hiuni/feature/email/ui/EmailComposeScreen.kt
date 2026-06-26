package de.transio.hiuni.feature.email.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.email.EmailComposeViewModel
import de.transio.hiuni.feature.email.data.EmailContact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailComposeScreen(
    onBack: () -> Unit,
    viewModel: EmailComposeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Subscribe explizit auf knownContacts — sonst läuft die DAO-Query nie an
    // (WhileSubscribed-StateFlow). Wir reichen die Liste in die Felder durch.
    val contacts by viewModel.knownContacts.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    // Snackbar für Erfolg & Fehler. Bei Erfolg schließen wir den Screen — der
    // ViewModel-State ist dann eh leer, der User soll wieder in der Inbox landen.
    LaunchedEffect(state.sentMessage) {
        val msg = state.sentMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeMessage()
        onBack()
    }
    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeMessage()
    }

    val attemptBack: () -> Unit = {
        if (state.isDirty && !state.isSending) showDiscardDialog = true else onBack()
    }
    BackHandler(enabled = !state.isSending) { attemptBack() }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Entwurf verwerfen?") },
            text = { Text("Dieser Entwurf wird nicht gespeichert.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) { Text("Verwerfen") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Weiter bearbeiten") }
            }
        )
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = attemptBack, enabled = !state.isSending) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Zurück",
                            tint = colors.onSurface
                        )
                    }
                    Text(
                        text = "Neue Mail",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.onSurface
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    if (state.isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = colors.primary
                        )
                    } else {
                        IconButton(onClick = viewModel::send, enabled = state.canSend) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = "Senden",
                                tint = if (state.canSend) colors.primary
                                else semantics.onSurfaceMuted
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))

            if (!state.hasCredentials) {
                Surface(
                    color = semantics.surfaceAlt,
                    shape = RoundedCornerShape(HiUniRadii.card),
                    modifier = Modifier.fillMaxWidth().padding(22.dp)
                ) {
                    Text(
                        text = "Keine Zugangsdaten — bitte in Settings einloggen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        modifier = Modifier.padding(18.dp)
                    )
                }
                return@Column
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChipsEmailField(
                    label = "An",
                    placeholder = "max@uni-hildesheim.de …",
                    chips = state.toChips,
                    draft = state.toDraft,
                    enabled = !state.isSending,
                    suggestions = filterContacts(contacts, state.toDraft),
                    onDraftChange = viewModel::updateToDraft,
                    onCommit = viewModel::commitToChip,
                    onRemoveChip = viewModel::removeToChip,
                    onBackspaceEmpty = viewModel::popToChip,
                    onApplySuggestion = viewModel::applyToSuggestion
                )

                if (state.showCcBcc) {
                    ChipsEmailField(
                        label = "CC",
                        placeholder = null,
                        chips = state.ccChips,
                        draft = state.ccDraft,
                        enabled = !state.isSending,
                        suggestions = filterContacts(contacts, state.ccDraft),
                        onDraftChange = viewModel::updateCcDraft,
                        onCommit = viewModel::commitCcChip,
                        onRemoveChip = viewModel::removeCcChip,
                        onBackspaceEmpty = viewModel::popCcChip,
                        onApplySuggestion = viewModel::applyCcSuggestion
                    )
                    ChipsEmailField(
                        label = "BCC",
                        placeholder = null,
                        chips = state.bccChips,
                        draft = state.bccDraft,
                        enabled = !state.isSending,
                        suggestions = filterContacts(contacts, state.bccDraft),
                        onDraftChange = viewModel::updateBccDraft,
                        onCommit = viewModel::commitBccChip,
                        onRemoveChip = viewModel::removeBccChip,
                        onBackspaceEmpty = viewModel::popBccChip,
                        onApplySuggestion = viewModel::applyBccSuggestion
                    )
                } else {
                    TextButton(
                        onClick = viewModel::toggleCcBcc,
                        enabled = !state.isSending,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = "CC / BCC anzeigen",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary
                        )
                    }
                }

                OutlinedTextField(
                    value = state.subject,
                    onValueChange = viewModel::updateSubject,
                    label = { Text("Betreff") },
                    singleLine = true,
                    enabled = !state.isSending,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.body,
                    onValueChange = viewModel::updateBody,
                    label = { Text("Nachricht") },
                    enabled = !state.isSending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp)
                )

            }
        }
    }
}

/**
 * Filtert die Kontaktliste anhand des Drafts (was der User grad tippt).
 * Match auf address ODER name, case-insensitive, max 6 Treffer. Bei leerem
 * Draft keine Suggestions.
 *
 * Bot-Absender (noreply / no-reply / donotreply / mailer-daemon) werden
 * grundsätzlich ausgefiltert — denen will man nie eine Mail zurückschreiben.
 */
private val NOREPLY_BLOCKLIST = Regex(
    pattern = "(noreply|no-reply|donotreply|do-not-reply|mailer-daemon|postmaster)",
    option = RegexOption.IGNORE_CASE
)

private fun filterContacts(contacts: List<EmailContact>, draft: String): List<EmailContact> {
    val needle = draft.trim().lowercase()
    if (needle.isEmpty()) return emptyList()
    return contacts.asSequence()
        .filterNot { c ->
            NOREPLY_BLOCKLIST.containsMatchIn(c.address) ||
                (c.name != null && NOREPLY_BLOCKLIST.containsMatchIn(c.name))
        }
        .filter { c ->
            c.address.lowercase().contains(needle) ||
                (c.name?.lowercase()?.contains(needle) == true)
        }
        .take(6)
        .toList()
}

/**
 * Chip-basiertes Adressfeld: bereits committete Adressen werden als Pills
 * gerendert, der noch-im-Tippen-State sitzt als inline-BasicTextField in
 * derselben FlowRow. Commit-Trigger sind die Separator-Zeichen `,`, `;`,
 * Whitespace und ImeAction.Done. Backspace auf leerem Draft entfernt den
 * letzten Chip — wie Gmail/Apple Mail.
 *
 * Suggestions hängen UNTER der Surface, damit das Dropdown den Layout-Flow
 * nicht stört. Nur das fokussierte Feld zeigt seine Liste.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsEmailField(
    label: String,
    placeholder: String?,
    chips: List<String>,
    draft: String,
    enabled: Boolean,
    suggestions: List<EmailContact>,
    onDraftChange: (String) -> Unit,
    onCommit: () -> Unit,
    onRemoveChip: (Int) -> Unit,
    onBackspaceEmpty: () -> Unit,
    onApplySuggestion: (EmailContact) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    var isFocused by remember { mutableStateOf(false) }
    val showSuggestions = isFocused && suggestions.isNotEmpty() && enabled

    Column {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(HiUniRadii.tile),
            border = BorderStroke(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.primary else colors.outline.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFocused) colors.primary else semantics.onSurfaceMuted
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    chips.forEachIndexed { index, addr ->
                        AddressChip(
                            text = addr,
                            enabled = enabled,
                            onRemove = { onRemoveChip(index) }
                        )
                    }

                    // Inline-Draft-TextField. Wir kapseln die Commit-Trigger-Logik
                    // direkt im onValueChange — sobald das letzte Zeichen ein
                    // Separator ist, schneiden wir es ab (das VM trimmt selbst
                    // nochmal) und feuern onCommit.
                    BasicTextField(
                        value = draft,
                        onValueChange = { new ->
                            val last = new.lastOrNull()
                            if (last != null && (last == ',' || last == ';' || last == ' ' || last == '\n' || last == '\t')) {
                                // Den Separator selbst NICHT in den Draft schreiben
                                // — wir setzen den Draft auf alles davor und
                                // committen. Das VM macht den Trim/Validate.
                                onDraftChange(new.dropLast(1))
                                onCommit()
                            } else {
                                onDraftChange(new)
                            }
                        },
                        enabled = enabled,
                        singleLine = true,
                        textStyle = LocalTextStyle.current.merge(
                            TextStyle(color = colors.onSurface)
                        ).merge(MaterialTheme.typography.bodyMedium),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { onCommit() }),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (draft.isEmpty() && chips.isEmpty() && placeholder != null) {
                                    Text(
                                        text = placeholder,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = semantics.onSurfaceMuted
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 80.dp)
                            .padding(vertical = 6.dp)
                            .onFocusChanged { isFocused = it.isFocused }
                            .onPreviewKeyEvent { ev ->
                                // Backspace auf leerem Draft → letzten Chip
                                // entfernen. PreviewKeyEvent damit es feuert,
                                // BEVOR der TextField das KeyEvent selbst frisst.
                                if (ev.type == KeyEventType.KeyDown &&
                                    (ev.key == Key.Backspace || ev.key == Key.Delete) &&
                                    draft.isEmpty() && chips.isNotEmpty()
                                ) {
                                    onBackspaceEmpty()
                                    true
                                } else {
                                    false
                                }
                            }
                    )
                }
            }
        }

        if (showSuggestions) {
            Spacer(Modifier.height(4.dp))
            Surface(
                color = semantics.surfaceAlt,
                shape = RoundedCornerShape(HiUniRadii.tile),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    suggestions.forEachIndexed { index, contact ->
                        if (index > 0) {
                            HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onApplySuggestion(contact) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                contact.name?.takeIf { it.isNotBlank() }?.let { name ->
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurface,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = contact.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semantics.onSurfaceMuted,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressChip(
    text: String,
    enabled: Boolean,
    onRemove: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.primaryContainer,
        shape = RoundedCornerShape(HiUniRadii.pill)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onPrimaryContainer,
                maxLines = 1
            )
            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Entfernen",
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
