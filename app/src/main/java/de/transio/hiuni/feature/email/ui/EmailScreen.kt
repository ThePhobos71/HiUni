package de.transio.hiuni.feature.email.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.email.EmailDetailActionsViewModel
import de.transio.hiuni.feature.email.EmailFolder
import de.transio.hiuni.feature.email.EmailUiState
import de.transio.hiuni.feature.email.EmailViewModel
import de.transio.hiuni.feature.email.MailSwipeAction
import de.transio.hiuni.feature.email.data.EmailEntity
import de.transio.hiuni.ui.responsive.FullWidthContent
import de.transio.hiuni.ui.responsive.LocalWindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
private val dateFmt = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailScreen(
    onCompose: () -> Unit = {},
    viewModel: EmailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }
    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeInfo()
        }
    }

    val selected = state.selectedEmail
    val isExpanded = LocalWindowSizeClass.current?.widthSizeClass == WindowWidthSizeClass.Expanded

    // Lock-Wall greift bevor Detail/Inbox überhaupt rendern — wenn der User
    // Biometric-Schutz aktiviert hat und noch nicht in dieser App-Session
    // entsperrt hat, zeigen wir nur die Unlock-CTA.
    if (state.isLocked) {
        MailLockScreen(
            onUnlocked = viewModel::unlockMail,
            onUnlockError = { msg -> /* Snackbar via VM */ }
        )
        return
    }

    // Auf Phone/Foldable-Medium: Mail-Detail ersetzt die Inbox vollständig (Push-Nav).
    // Auf Tablet-Expanded: kein Early-Return — Detail rendert als rechter Pane neben
    // der Liste, beide Panes bleiben sichtbar.
    if (selected != null && !isExpanded) {
        // Eigener VM nur für Reply/Forward-Side-Effects — verhindert dass der
        // EmailViewModel (Inbox + Detail kombiniert) eine Prefill-Holder-Dependency
        // schleppt. stageReply/stageForward sind one-shots, kein State nötig.
        val actionsVm: EmailDetailActionsViewModel = hiltViewModel()
        BackHandler { viewModel.closeEmail() }
        EmailDetail(
            email = selected,
            bodyPlain = state.selectedBodyPlain,
            bodyHtml = state.selectedBodyHtml,
            attachments = state.selectedAttachments,
            invite = state.selectedInvite,
            isLoadingBody = state.isLoadingBody,
            downloadingPartIndex = state.downloadingPartIndex,
            onBack = viewModel::closeEmail,
            onToggleStar = { viewModel.toggleStar(selected) },
            onOpenAttachment = viewModel::openAttachment,
            onAddInviteToCalendar = viewModel::addInviteToCalendar,
            onReply = {
                actionsVm.stageReply(selected, state.selectedBodyPlain)
                onCompose()
            },
            onForward = {
                actionsVm.stageForward(selected, state.selectedBodyPlain)
                onCompose()
            },
            snackbarHostState = snackbarHostState
        )
        return
    }

    // System-Back schließt die Suche bevorzugt — wie bei Mensa, damit der User die
    // Suche bewusst zumacht statt versehentlich den Tab zu verlassen.
    // Auf Expanded zusätzlich: Back schließt zuerst ggf. die Mail-Auswahl im rechten
    // Pane (statt den ganzen Tab zu verlassen).
    BackHandler(enabled = state.isSearchOpen) { viewModel.closeSearch() }
    BackHandler(enabled = isExpanded && !state.isSearchOpen && selected != null) {
        viewModel.closeEmail()
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // FAB ausblenden während die Suche offen ist — der Such-Workflow ist
            // explorativ, "Verfassen" steht da nur im Weg und kollidiert visuell mit
            // der Keyboard-Höhe.
            if (state.hasCredentials && !state.isSearchOpen) {
                ExtendedFloatingActionButton(
                    onClick = onCompose,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null
                        )
                    },
                    text = { Text("Verfassen") },
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                )
            }
        }
    ) { padding ->
        if (isExpanded) {
            // Multi-Pane (Tablet-Landscape): Liste links, Detail/Empty rechts.
            // FullWidthContent hebt den globalen 1100dp-Cap auf, sonst klebt das
            // Multi-Pane in der Mitte und verschwendet Rand-Pixel.
            FullWidthContent {
                Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Box(
                        modifier = Modifier
                            .weight(0.4f)
                            .widthIn(min = 380.dp)
                            .fillMaxHeight()
                    ) {
                        EmailInboxPane(
                            state = state,
                            viewModel = viewModel,
                            colors = colors
                        )
                    }
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        color = colors.outlineVariant
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .widthIn(min = 480.dp)
                            .fillMaxHeight()
                    ) {
                        if (selected != null) {
                            // Eigener VM nur für Reply/Forward-Side-Effects (siehe Begründung
                            // im Compact-Branch).
                            val actionsVm: EmailDetailActionsViewModel = hiltViewModel()
                            EmailDetail(
                                email = selected,
                                bodyPlain = state.selectedBodyPlain,
                                bodyHtml = state.selectedBodyHtml,
                                attachments = state.selectedAttachments,
                                invite = state.selectedInvite,
                                isLoadingBody = state.isLoadingBody,
                                downloadingPartIndex = state.downloadingPartIndex,
                                onBack = viewModel::closeEmail,
                                onToggleStar = { viewModel.toggleStar(selected) },
                                onOpenAttachment = viewModel::openAttachment,
                                onAddInviteToCalendar = viewModel::addInviteToCalendar,
                                onReply = {
                                    actionsVm.stageReply(selected, state.selectedBodyPlain)
                                    onCompose()
                                },
                                onForward = {
                                    actionsVm.stageForward(selected, state.selectedBodyPlain)
                                    onCompose()
                                },
                                snackbarHostState = snackbarHostState
                            )
                        } else {
                            EmailDetailEmptyPane()
                        }
                    }
                }
            }
        } else {
            // Single-Pane (Phone/Foldable-Medium): Inbox füllt den ganzen Body.
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmailInboxPane(
                    state = state,
                    viewModel = viewModel,
                    colors = colors
                )
            }
        }
    }
}

/**
 * Inbox-Body (Header + Folder-Pillen + Liste/Empty/PullToRefresh). Extrahiert,
 * damit derselbe Code im Single-Pane (Phone) und im linken Multi-Pane (Tablet)
 * verwendet werden kann.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailInboxPane(
    state: EmailUiState,
    viewModel: EmailViewModel,
    colors: androidx.compose.material3.ColorScheme
) {
    Column(modifier = Modifier.fillMaxSize()) {
        EmailHeader(
            state = state,
            onSelectFolder = viewModel::selectFolder,
            onOpenSearch = viewModel::openSearch,
            onCloseSearch = viewModel::closeSearch,
            onQueryChange = viewModel::setSearchQuery
        )
        HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(force = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            if (!state.hasCredentials) {
                EmptyAuthState()
            } else if (state.emails.isEmpty()) {
                if (state.isSearchActive) {
                    EmptySearchState(query = state.searchQuery)
                } else {
                    EmptyInboxState(folder = state.folder)
                }
            } else {
                var pendingDelete by remember { mutableStateOf<EmailEntity?>(null) }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.emails, key = { it.rowId }) { email ->
                        SwipeableEmailRow(
                            email = email,
                            rightAction = state.swipeRightAction,
                            leftAction = state.swipeLeftAction,
                            onClick = { viewModel.openEmail(email) },
                            onArchive = { viewModel.archiveEmail(email) },
                            onRequestDelete = { pendingDelete = email },
                            onToggleStar = { viewModel.toggleStar(email) },
                            onMarkRead = { viewModel.markEmailRead(email) },
                            onMarkUnread = { viewModel.markEmailUnread(email) }
                        )
                        HorizontalDivider(color = colors.outline.copy(alpha = 0.15f))
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
                pendingDelete?.let { target ->
                    AlertDialog(
                        onDismissRequest = { pendingDelete = null },
                        title = { Text("Mail löschen?") },
                        text = {
                            Text(
                                "„${target.subject?.ifBlank { "(ohne Betreff)" } ?: "(ohne Betreff)"}\" " +
                                    "wird unwiderruflich vom Server gelöscht."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.deleteEmail(target)
                                pendingDelete = null
                            }) { Text("Löschen") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingDelete = null }) {
                                Text("Abbrechen")
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Placeholder im rechten Pane wenn (noch) keine Mail ausgewählt ist —
 * Tablet-Landscape-only. Outline-Mail-Icon + Hinweis, dass der User links
 * eine Mail anwählen soll. Wir bauen den State bewusst sparsam: kein Card-
 * Container (containerColor=null) damit der Pane nicht wie ein Empty-Inbox-
 * Block wirkt sondern wie ein neutraler, leerer Detail-Bereich.
 */
@Composable
private fun EmailDetailEmptyPane() {
    val semantics = HiUniColors.semantics
    de.transio.hiuni.core.design.components.EmptyState(
        icon = Icons.Outlined.Mail,
        iconAccent = semantics.onSurfaceMuted,
        title = "Keine Mail ausgewählt.",
        secondaryBody = "Wähle eine Mail aus der Liste links, um sie hier zu lesen."
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailHeader(
    state: EmailUiState,
    onSelectFolder: (EmailFolder) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onQueryChange: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 14.dp)
    ) {
        if (state.isSearchOpen) {
            // Search-Modus: Titel/Counter weichen der Suchleiste. Folder-Pillen bleiben
            // darunter sichtbar, damit der Query über Posteingang/Gesendet/Markiert
            // hinweg geteilt wird (siehe Spec: "Such-Input ist per Folder geteilt").
            EmailSearchBar(
                query = state.searchQuery,
                onQueryChange = onQueryChange,
                onClose = onCloseSearch
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (state.folder) {
                            EmailFolder.INBOX -> "Posteingang"
                            EmailFolder.SENT -> "Gesendet"
                            EmailFolder.ARCHIVE -> "Archiv"
                            EmailFolder.STARRED -> "Markiert"
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.onSurface
                    )
                    if (state.hasCredentials) {
                        // In Sent/Archiv ist "ungelesen" semantisch leer (eigene/abgelegte Mails) — zeige nur Anzahl.
                        Text(
                            text = when (state.folder) {
                                EmailFolder.SENT -> "${state.emails.size} gesendet"
                                EmailFolder.ARCHIVE -> "${state.emails.size} archiviert"
                                else -> "${state.unreadCount} ungelesen · ${state.emails.size} insgesamt"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = semantics.onSurfaceMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                if (state.hasCredentials) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenSearch),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Mails durchsuchen",
                            tint = colors.onSurface
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // 4 Pillen können auf schmalen Phones (z.B. ≤360dp) knapp werden — daher
        // horizontalScroll als Safety-Net. Auf normalbreiten Geräten passen sie
        // ohne Scroll, der State bleibt dann einfach inaktiv.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FolderPill(
                label = "Posteingang",
                active = state.folder == EmailFolder.INBOX,
                onClick = { onSelectFolder(EmailFolder.INBOX) }
            )
            FolderPill(
                label = "Gesendet",
                active = state.folder == EmailFolder.SENT,
                onClick = { onSelectFolder(EmailFolder.SENT) }
            )
            FolderPill(
                label = "Archiv",
                active = state.folder == EmailFolder.ARCHIVE,
                onClick = { onSelectFolder(EmailFolder.ARCHIVE) }
            )
            FolderPill(
                label = "Markiert",
                active = state.folder == EmailFolder.STARRED,
                onClick = { onSelectFolder(EmailFolder.STARRED) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Suche schließen",
                tint = colors.onSurface
            )
        }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = "Betreff, Absender, Text…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted
                )
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = semantics.surfaceAlt,
                unfocusedContainerColor = semantics.surfaceAlt,
                disabledContainerColor = semantics.surfaceAlt,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(HiUniRadii.pill),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { onQueryChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Eingabe löschen",
                            tint = semantics.onSurfaceMuted
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun FolderPill(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = if (active) colors.primaryContainer else semantics.surfaceAlt,
        shape = RoundedCornerShape(HiUniRadii.tile),
        onClick = onClick
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) colors.primary else semantics.onSurfaceMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun EmptyAuthState() {
    val semantics = HiUniColors.semantics
    de.transio.hiuni.core.design.components.EmptyState(
        icon = Icons.Outlined.MarkEmailRead,
        iconAccent = semantics.onSurfaceMuted,
        containerColor = semantics.surfaceAlt,
        title = "Kein Uni-Mail-Zugang hinterlegt.",
        body = "Trage RZ-Kennung + Passwort in den Einstellungen ein, " +
            "dann werden Mails verschlüsselt lokal gecached."
    )
}

@Composable
private fun EmptyInboxState(folder: EmailFolder) {
    val semantics = HiUniColors.semantics
    // Im Archive ist die Hint-Text relevanter als die Pull-to-Refresh-Phrase —
    // archivierte Mails werden lokal per Swipe abgelegt, nicht durch Server-Pull.
    if (folder == EmailFolder.ARCHIVE) {
        de.transio.hiuni.core.design.components.EmptyState(
            icon = Icons.Outlined.Archive,
            iconAccent = semantics.onSurfaceMuted,
            containerColor = semantics.surfaceAlt,
            title = "Keine archivierten Mails.",
            secondaryBody = "Wische rechts auf eine Mail im Posteingang, um sie hierher zu legen."
        )
        return
    }
    de.transio.hiuni.core.design.components.EmptyState(
        containerColor = semantics.surfaceAlt,
        title = when (folder) {
            EmailFolder.STARRED -> "Keine markierten Mails."
            EmailFolder.SENT -> "Keine gesendeten Mails."
            EmailFolder.ARCHIVE -> "Keine archivierten Mails."
            EmailFolder.INBOX -> "Posteingang ist leer."
        },
        secondaryBody = "Pull-to-Refresh holt aktuelle Mails vom Server."
    )
}

@Composable
private fun EmptySearchState(query: String) {
    val semantics = HiUniColors.semantics
    de.transio.hiuni.core.design.components.EmptyState(
        icon = Icons.Outlined.Search,
        iconAccent = semantics.onSurfaceMuted,
        containerColor = semantics.surfaceAlt,
        title = "Keine Treffer für „$query“.",
        secondaryBody = "Probier ein anderes Stichwort — gesucht wird in Betreff, " +
            "Absender und Mailtext."
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableEmailRow(
    email: EmailEntity,
    rightAction: MailSwipeAction,
    leftAction: MailSwipeAction,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onRequestDelete: () -> Unit,
    onToggleStar: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit
) {
    val semantics = HiUniColors.semantics
    val dispatch: (MailSwipeAction) -> Unit = { action ->
        when (action) {
            MailSwipeAction.ARCHIVE -> onArchive()
            MailSwipeAction.DELETE -> onRequestDelete()
            MailSwipeAction.TOGGLE_STAR -> onToggleStar()
            MailSwipeAction.MARK_READ -> onMarkRead()
            MailSwipeAction.MARK_UNREAD -> onMarkUnread()
            MailSwipeAction.NONE -> Unit
        }
    }
    // confirmValueChange triggert die Action, returnt aber `false` damit der
    // Row visuell zurückschnappt. Bei Archive/Delete/Read ändert der Repository-
    // Trigger die Row aus der Liste raus; bei Star/None bleibt sie sichtbar —
    // visueller Rebound ist in beiden Fällen erwünscht.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { dispatch(rightAction); false }
                SwipeToDismissBoxValue.EndToStart -> { dispatch(leftAction); false }
                else -> false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        // Wischen in eine NONE-Richtung deaktivieren — nicht via enableDismissFrom*
        // weil dann die Wisch-Geste den Klick blockieren kann; stattdessen rendern
        // wir keinen Background und confirmValueChange returnt false → kein Effekt.
        enableDismissFromStartToEnd = rightAction != MailSwipeAction.NONE,
        enableDismissFromEndToStart = leftAction != MailSwipeAction.NONE,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val action = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> rightAction
                SwipeToDismissBoxValue.EndToStart -> leftAction
                else -> MailSwipeAction.NONE
            }
            val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                Alignment.CenterStart
            } else {
                Alignment.CenterEnd
            }
            val style = styleFor(action, semantics)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(style.background)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                if (style.icon != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (alignment == Alignment.CenterStart) {
                            Icon(style.icon, contentDescription = null, tint = style.tint)
                            Text(action.displayLabel, color = style.tint, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text(action.displayLabel, color = style.tint, fontWeight = FontWeight.SemiBold)
                            Icon(style.icon, contentDescription = null, tint = style.tint)
                        }
                    }
                }
            }
        }
    ) {
        EmailRow(email = email, onClick = onClick)
    }
}

private data class SwipeStyle(
    val background: Color,
    val tint: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?
)

private fun styleFor(
    action: MailSwipeAction,
    semantics: de.transio.hiuni.core.design.HiUniSemanticColors
): SwipeStyle = when (action) {
    MailSwipeAction.ARCHIVE -> SwipeStyle(semantics.green, semantics.onGreen, Icons.Outlined.Archive)
    MailSwipeAction.DELETE -> SwipeStyle(semantics.red, semantics.onRed, Icons.Outlined.Delete)
    MailSwipeAction.TOGGLE_STAR -> SwipeStyle(semantics.amber, semantics.onAmber, Icons.Outlined.Star)
    MailSwipeAction.MARK_READ -> SwipeStyle(semantics.purple, semantics.onPurple, Icons.Outlined.MarkEmailRead)
    MailSwipeAction.MARK_UNREAD -> SwipeStyle(semantics.purple, semantics.onPurple, Icons.Outlined.MarkEmailUnread)
    MailSwipeAction.NONE -> SwipeStyle(Color.Transparent, Color.Transparent, null)
}

@Composable
private fun EmailRow(email: EmailEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.Top
    ) {
        AvatarBox(email = email)
        Column(modifier = Modifier
            .weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = email.displayFrom,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (email.isRead) FontWeight.SemiBold else FontWeight.ExtraBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (email.hasCalendarInvite) {
                        Icon(
                            imageVector = Icons.Outlined.Event,
                            contentDescription = "Termineinladung",
                            tint = colors.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (email.hasAttachments && !email.hasCalendarInvite) {
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = "Anhang",
                            tint = semantics.onSurfaceMuted,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (email.isStarred) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = null,
                            tint = semantics.amber,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = formatRelativeTime(email.receivedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (email.isRead) semantics.onSurfaceMuted else colors.primary,
                        fontWeight = if (email.isRead) FontWeight.Medium else FontWeight.Bold
                    )
                }
            }
            Text(
                text = email.subject.ifBlank { "(Kein Betreff)" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (email.isRead) FontWeight.Medium else FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (email.snippet.isNotBlank()) {
                Text(
                    text = email.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
    // Row is clickable through Surface modifier above — wrap with clickable around the Row
}

@Composable
private fun AvatarBox(email: EmailEntity) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(colors.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = email.initials,
            style = MaterialTheme.typography.titleSmall,
            color = colors.primary,
            fontWeight = FontWeight.ExtraBold
        )
        if (!email.isRead) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailDetail(
    email: EmailEntity,
    bodyPlain: String?,
    bodyHtml: String?,
    attachments: List<de.transio.hiuni.feature.email.data.EmailAttachment>,
    invite: de.transio.hiuni.feature.email.data.IcsInvite?,
    isLoadingBody: Boolean,
    downloadingPartIndex: Int?,
    onBack: () -> Unit,
    onToggleStar: () -> Unit,
    onOpenAttachment: (de.transio.hiuni.feature.email.data.EmailAttachment) -> Unit,
    onAddInviteToCalendar: (de.transio.hiuni.feature.email.data.IcsInvite) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val htmlBody = bodyHtml?.takeIf { it.isNotBlank() }
    val hasHtml = htmlBody != null
    val plainFallback = bodyPlain?.takeIf { it.isNotBlank() } ?: email.snippet
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
                    .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Zurück",
                        tint = colors.onSurface
                    )
                }
                // Action-Cluster rechts: Antworten / Weiterleiten / Markieren.
                // Anordnung wie Gmail Detail-Toolbar — Reply ist die häufigste
                // Aktion, daher links innerhalb der Aktionen.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onReply) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Reply,
                            contentDescription = "Antworten",
                            tint = colors.onSurface
                        )
                    }
                    IconButton(onClick = onForward) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Forward,
                            contentDescription = "Weiterleiten",
                            tint = colors.onSurface
                        )
                    }
                    IconButton(onClick = onToggleStar) {
                        Icon(
                            imageVector = if (email.isStarred) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Markieren",
                            tint = if (email.isStarred) semantics.amber else colors.onSurface
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Text(
                    text = email.subject.ifBlank { "(Kein Betreff)" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AvatarBox(email = email.copy(isRead = true))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = email.displayFrom,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = formatAbsoluteTime(email.receivedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = semantics.onSurfaceMuted
                        )
                        RecipientLine(label = "An", value = email.toAddresses ?: "mich")
                        if (!email.ccAddresses.isNullOrBlank()) {
                            RecipientLine(label = "CC", value = email.ccAddresses)
                        }
                        if (!email.bccAddresses.isNullOrBlank()) {
                            RecipientLine(label = "BCC", value = email.bccAddresses)
                        }
                    }
                }
                if (invite != null) {
                    Spacer(Modifier.height(16.dp))
                    InviteCard(invite = invite, onAdd = { onAddInviteToCalendar(invite) })
                }
                Spacer(Modifier.height(20.dp))
                when {
                    isLoadingBody && !hasHtml && plainFallback.isBlank() -> {
                        Surface(
                            color = colors.surface,
                            shape = RoundedCornerShape(HiUniRadii.card),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Lade Nachricht …",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = semantics.onSurfaceMuted
                                )
                            }
                        }
                    }
                    htmlBody != null -> HtmlBody(html = htmlBody)
                    else -> Surface(
                        color = colors.surface,
                        shape = RoundedCornerShape(HiUniRadii.card),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = plainFallback.ifBlank { "(Kein Text)" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
                if (attachments.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "ANHÄNGE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = semantics.onSurfaceMuted,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    attachments.forEach { attachment ->
                        AttachmentRow(
                            attachment = attachment,
                            downloading = attachment.partIndex == downloadingPartIndex,
                            onClick = { onOpenAttachment(attachment) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun HtmlBody(html: String) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HiUniRadii.tile)),
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.loadsImagesAutomatically = true
                settings.blockNetworkImage = false
                settings.defaultTextEncodingName = "UTF-8"
                setBackgroundColor(android.graphics.Color.WHITE)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, wrapHtml(html), "text/html", "UTF-8", null)
        }
    )
}

private fun wrapHtml(raw: String): String {
    // Mail-Content rendert IMMER auf weißem Substrat — wie Gmail/Apple Mail das im
    // Dark Mode auch handhaben. Mails sind universell für hellen Hintergrund gebaut;
    // ein Theme-Override hier zerschießt halb-gestylte Mails (weiße Tabellen-Backgrounds
    // mit transparenter Schrift, partial inline-color, etc.).
    return """
        <!doctype html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          :root { color-scheme: light; }
          html, body {
            background: #ffffff; color: #111111;
            font-family: -apple-system, Roboto, sans-serif;
            font-size: 14px; line-height: 1.55;
            margin: 0; padding: 0;
            word-wrap: break-word; overflow-wrap: break-word;
          }
          /* Innen-Padding damit Text nicht an die abgerundeten Ecken stößt */
          body { padding: 18px 16px; box-sizing: border-box; }
          a { color: #1565c0; }
          img { max-width: 100%; height: auto; }
          /* Inline-Logos (cid:...) verstecken — WebView kann sie nicht auflösen */
          img[src^="cid:"], img[src=""], img:not([src]) { display: none; }
          blockquote {
            border-left: 3px solid #bbbbbb;
            margin: 0; padding-left: 12px;
            color: #555555;
          }
          pre, code { white-space: pre-wrap; word-break: break-word; }
          table { max-width: 100%; }
        </style>
        </head>
        <body>$raw</body>
        </html>
    """.trimIndent()
}

@Composable
private fun AttachmentRow(
    attachment: de.transio.hiuni.feature.email.data.EmailAttachment,
    downloading: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.tile),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile))
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (downloading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.AttachFile,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.filename,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${attachment.mimeType} · ${formatBytes(attachment.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

private fun formatRelativeTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val zdt = instant.atZone(zone)
    val date = zdt.toLocalDate()
    val today = LocalDate.now(zone)
    val daysAgo = Duration.between(date.atStartOfDay(zone), today.atStartOfDay(zone)).toDays()
    val time = zdt.format(timeFmt)
    return when {
        date == today -> time
        daysAgo in 1..6 -> "${zdt.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.GERMAN)} $time"
        else -> "${zdt.format(dateFmt)} $time"
    }
}

private fun formatAbsoluteTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val zdt = instant.atZone(zone)
    return "${zdt.format(dateFmt)} · ${zdt.format(timeFmt)} Uhr"
}

@Composable
private fun RecipientLine(label: String, value: String) {
    val semantics = HiUniColors.semantics
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = semantics.onSurfaceMuted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun InviteCard(invite: de.transio.hiuni.feature.email.data.IcsInvite, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, d. MMM · HH:mm", Locale.GERMAN)
    Surface(
        color = colors.primaryContainer,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = colors.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TERMINEINLADUNG",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = invite.summary ?: "(Kein Titel)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                invite.start?.let { start ->
                    Text(
                        text = start.atZone(ZoneId.systemDefault()).format(dateFormatter) + " Uhr",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                invite.location?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted
                    )
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = colors.primary,
                    shape = RoundedCornerShape(10.dp),
                    onClick = onAdd
                ) {
                    Text(
                        text = "Im Kalender speichern",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MailLockScreen(
    onUnlocked: () -> Unit,
    onUnlockError: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val triggerUnlock = de.transio.hiuni.core.security.rememberMailUnlockPrompt(
        onSuccess = onUnlocked,
        onError = onUnlockError
    )
    // Auto-Trigger beim Erst-Anzeigen: dann muss der User nicht erst einen
    // separaten „Entsperren"-Button antippen — Prompt poppt direkt auf.
    LaunchedEffect(Unit) { triggerUnlock() }
    Box(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mail,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Text(
                text = "Mail ist gesperrt",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
            Text(
                text = "Bestätige mit Fingerabdruck, um deine Mails zu sehen.",
                style = MaterialTheme.typography.bodyMedium,
                color = semantics.onSurfaceMuted,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            TextButton(onClick = triggerUnlock) {
                Text("Entsperren")
            }
        }
    }
}
