package de.transio.hiuni.feature.notifications.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AssignmentLate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.design.HiUniSemanticColors
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.core.notifications.data.NotificationLogEntity
import de.transio.hiuni.feature.notifications.NotificationsViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class TimeBucket(val label: String) {
    Today("Heute"),
    Yesterday("Gestern"),
    Older("Älter")
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(
                    bottomStart = HiUniRadii.big,
                    bottomEnd = HiUniRadii.big
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Zurück",
                            tint = colors.onSurface
                        )
                    }
                    Text(
                        text = "Mitteilungen",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.unreadCount > 0) {
                        Text(
                            text = "Alle gelesen",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                            modifier = Modifier.clickable { viewModel.markAllRead() }
                        )
                    }
                }
            }

            if (state.items.isEmpty()) {
                EmptyState()
                return@Column
            }

            val grouped = remember(state.items) { groupByBucket(state.items) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (bucket, items) ->
                    item(key = "header-${bucket.name}") {
                        Text(
                            text = bucket.label.uppercase(Locale.GERMAN),
                            style = MaterialTheme.typography.labelMedium,
                            color = semantics.onSurfaceMuted,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = if (bucket == TimeBucket.Today) 0.dp else 10.dp, bottom = 2.dp)
                        )
                    }
                    items.forEach { entry ->
                        item(key = "entry-${entry.id}") {
                            NotificationRow(
                                entry = entry,
                                semantics = semantics,
                                onClick = { viewModel.markRead(entry.id) },
                                onDismiss = { viewModel.delete(entry.id) }
                            )
                        }
                    }
                }
                item(key = "spacer-bottom") { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.NotificationsNone,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Keine Mitteilungen",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Erinnerungen aus Kalender, Klausuren und Co. erscheinen hier.",
            style = MaterialTheme.typography.bodyMedium,
            color = semantics.onSurfaceMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun NotificationRow(
    entry: NotificationLogEntity,
    semantics: HiUniSemanticColors,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val (icon, accent, surface) = kindStyling(entry.kind, colors, semantics)
    val rowBg = if (entry.isRead) colors.surface else surface
    Surface(
        color = rowBg,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        fontWeight = if (entry.isRead) FontWeight.Medium else FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (!entry.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                    }
                }
                entry.body?.takeIf { it.isNotBlank() }?.let { body ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted,
                        maxLines = 2
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatRelative(entry.firedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = semantics.onSurfaceMuted
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Entfernen",
                    tint = semantics.onSurfaceMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private data class KindStyling(
    val icon: ImageVector,
    val accent: Color,
    val surface: Color
)

@Composable
private fun kindStyling(
    kind: NotificationKind,
    colors: androidx.compose.material3.ColorScheme,
    semantics: HiUniSemanticColors
): KindStyling = when (kind) {
    NotificationKind.EVENT -> KindStyling(
        icon = Icons.Outlined.Schedule,
        accent = colors.primary,
        surface = colors.primaryContainer
    )
    NotificationKind.EXAM -> KindStyling(
        icon = Icons.Outlined.AssignmentLate,
        accent = semantics.red,
        surface = semantics.redSurface
    )
    NotificationKind.GRADE -> KindStyling(
        icon = Icons.Outlined.Grade,
        accent = semantics.green,
        surface = semantics.greenSurface
    )
    NotificationKind.MAIL -> KindStyling(
        icon = Icons.Outlined.Email,
        accent = colors.primary,
        surface = colors.primaryContainer
    )
    NotificationKind.MENSA -> KindStyling(
        icon = Icons.Outlined.LocalDining,
        accent = semantics.amber,
        surface = semantics.amberSurface
    )
    NotificationKind.MOVIE -> KindStyling(
        icon = Icons.Outlined.Movie,
        accent = semantics.red,
        surface = semantics.redSurface
    )
    NotificationKind.SPORT -> KindStyling(
        icon = Icons.Outlined.FitnessCenter,
        accent = semantics.green,
        surface = semantics.greenSurface
    )
    NotificationKind.BIB -> KindStyling(
        icon = Icons.Outlined.LocalLibrary,
        accent = semantics.purple,
        surface = semantics.purpleSurface
    )
    NotificationKind.SYSTEM -> KindStyling(
        icon = Icons.Outlined.Info,
        accent = semantics.onSurfaceMuted,
        surface = colors.surface
    )
}

private fun bucketFor(firedAt: Instant, today: LocalDate): TimeBucket {
    val date = firedAt.atZone(ZoneId.systemDefault()).toLocalDate()
    return when (date) {
        today -> TimeBucket.Today
        today.minusDays(1) -> TimeBucket.Yesterday
        else -> TimeBucket.Older
    }
}

private fun groupByBucket(
    items: List<NotificationLogEntity>
): List<Pair<TimeBucket, List<NotificationLogEntity>>> {
    val today = LocalDate.now()
    return items
        .groupBy { bucketFor(it.firedAt, today) }
        .toList()
        .sortedBy { it.first.ordinal }
}

private fun formatRelative(instant: Instant): String {
    val now = Instant.now()
    val diff = Duration.between(instant, now)
    return when {
        diff.toMinutes() < 1L -> "Gerade eben"
        diff.toMinutes() < 60L -> "Vor ${diff.toMinutes()} Min"
        diff.toHours() < 24L -> "Vor ${diff.toHours()} Std"
        diff.toDays() < 7L -> "Vor ${diff.toDays()} Tagen"
        else -> instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d. MMM yyyy · HH:mm", Locale.GERMAN))
    }
}

