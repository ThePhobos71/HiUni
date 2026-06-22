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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.email.EmailFolder
import de.transio.hiuni.feature.email.EmailUiState
import de.transio.hiuni.feature.email.EmailViewModel
import de.transio.hiuni.feature.email.data.EmailEntity
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
fun EmailScreen(viewModel: EmailViewModel = hiltViewModel()) {
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
    if (selected != null) {
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
            snackbarHostState = snackbarHostState
        )
        return
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            EmailHeader(state = state, onSelectFolder = viewModel::selectFolder)
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh(force = true) },
                modifier = Modifier.fillMaxSize()
            ) {
                if (!state.hasCredentials) {
                    EmptyAuthState()
                } else if (state.emails.isEmpty()) {
                    EmptyInboxState(folder = state.folder)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.emails, key = { it.rowId }) { email ->
                            EmailRow(email = email, onClick = { viewModel.openEmail(email) })
                            HorizontalDivider(color = colors.outline.copy(alpha = 0.15f))
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailHeader(state: EmailUiState, onSelectFolder: (EmailFolder) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 14.dp)
    ) {
        Text(
            text = "Posteingang",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onSurface
        )
        if (state.hasCredentials) {
            Text(
                text = "${state.unreadCount} ungelesen · ${state.emails.size} insgesamt",
                style = MaterialTheme.typography.bodyMedium,
                color = semantics.onSurfaceMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FolderPill(
                label = "Posteingang",
                active = state.folder == EmailFolder.INBOX,
                onClick = { onSelectFolder(EmailFolder.INBOX) }
            )
            FolderPill(
                label = "Markiert",
                active = state.folder == EmailFolder.STARRED,
                onClick = { onSelectFolder(EmailFolder.STARRED) }
            )
        }
    }
}

@Composable
private fun FolderPill(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = if (active) colors.primaryContainer else semantics.surfaceAlt,
        shape = RoundedCornerShape(14.dp),
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
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = semantics.surfaceAlt,
            shape = RoundedCornerShape(HiUniRadii.card),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.MarkEmailRead,
                    contentDescription = null,
                    tint = semantics.onSurfaceMuted
                )
                Text(
                    text = "Kein Uni-Mail-Zugang hinterlegt.",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface
                )
                Text(
                    text = "Trage RZ-Kennung + Passwort in den Einstellungen ein, " +
                        "dann werden Mails verschlüsselt lokal gecached.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun EmptyInboxState(folder: EmailFolder) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = semantics.surfaceAlt,
            shape = RoundedCornerShape(HiUniRadii.card),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (folder == EmailFolder.STARRED) "Keine markierten Mails."
                    else "Posteingang ist leer.",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface
                )
                Text(
                    text = "Pull-to-Refresh holt aktuelle Mails vom Server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
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
    snackbarHostState: SnackbarHostState
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val hasHtml = !bodyHtml.isNullOrBlank()
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
                IconButton(onClick = onToggleStar) {
                    Icon(
                        imageVector = if (email.isStarred) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Markieren",
                        tint = if (email.isStarred) semantics.amber else colors.onSurface
                    )
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
                    hasHtml -> HtmlBody(html = bodyHtml!!)
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
