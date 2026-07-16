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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentLate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.design.HiUniSemanticColors
import de.transio.hiuni.core.design.components.HiUniTopBar
import de.transio.hiuni.core.notifications.data.NotificationCategory
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.core.notifications.data.NotificationLogEntity
import de.transio.hiuni.feature.notifications.NotificationsViewModel
import de.transio.hiuni.navigation.Destination
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
    onOpenRef: (Destination) -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val haptics = LocalHapticFeedback.current

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            HiUniTopBar(
                title = "Mitteilungen",
                onBack = onBack,
                trailing = if (state.unreadCount > 0) {
                    {
                        Text(
                            text = "Alle gelesen",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                            modifier = Modifier.clickable(
                                onClickLabel = "Alle Mitteilungen als gelesen markieren",
                                role = Role.Button
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.markAllRead()
                            }
                        )
                    }
                } else null
            )

            // Kategorie-Filter-Pills — nur wenn es überhaupt etwas zu filtern gibt
            // (mind. zwei Kategorien vorhanden). Hand-gestylt (Surface+Text-Pills),
            // kein Stock-M3-FilterChip, konsistent mit dem restlichen Repo.
            if (state.availableCategories.size > 1) {
                CategoryFilterRow(
                    available = state.availableCategories,
                    selected = state.selectedCategory,
                    onSelect = { viewModel.selectCategory(it) }
                )
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (state.items.isEmpty()) {
                    EmptyState()
                } else {
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
                                    SwipeableNotificationRow(
                                        entry = entry,
                                        semantics = semantics,
                                        onClick = {
                                            // Tap = "habe ich gesehen" + (falls verlinkt)
                                            // Sprung ins zugehörige Feature. Kein Destination
                                            // → still bleiben, nur read-Marker setzen.
                                            if (!entry.isRead) {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            viewModel.markRead(entry.id)
                                            deepLinkDestinationFor(entry.kind)?.let(onOpenRef)
                                        },
                                        onDismiss = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.delete(entry.id)
                                        }
                                    )
                                }
                            }
                        }
                        item(key = "spacer-bottom") { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    available: List<NotificationCategory>,
    selected: NotificationCategory?,
    onSelect: (NotificationCategory?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "filter-all") {
            FilterPill(
                label = "Alle",
                selected = selected == null,
                onClick = { onSelect(null) }
            )
        }
        available.forEach { category ->
            item(key = "filter-${category.name}") {
                FilterPill(
                    label = categoryLabel(category),
                    selected = selected == category,
                    onClick = { onSelect(category) }
                )
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val bg = if (selected) colors.primary else colors.surface
    val fg = if (selected) colors.onPrimary else semantics.onSurfaceMuted
    Surface(
        color = bg,
        shape = RoundedCornerShape(HiUniRadii.pill),
        modifier = Modifier.clickable(
            onClickLabel = if (selected) "Filter $label aktiv" else "Nach $label filtern",
            role = Role.Button,
            onClick = onClick
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

private fun categoryLabel(category: NotificationCategory): String = when (category) {
    NotificationCategory.EVENTS -> "Termine"
    NotificationCategory.EXAMS -> "Klausuren"
    NotificationCategory.GRADES -> "Noten"
    NotificationCategory.COURSES -> "Kurse"
    NotificationCategory.LEARNWEB -> "Learnweb"
    NotificationCategory.MAIL -> "E-Mail"
    NotificationCategory.SYSTEM -> "System"
}

@Composable
private fun EmptyState() {
    val colors = MaterialTheme.colorScheme
    de.transio.hiuni.core.design.components.EmptyState(
        icon = Icons.Outlined.NotificationsNone,
        iconAccent = colors.primary,
        iconSurface = colors.primaryContainer,
        title = "Noch keine Mitteilungen",
        body = "Sobald ein Kalender-Reminder ausgelöst wird, landet er hier — auch wenn du die System-Benachrichtigung verpasst hast.",
        secondaryBody = "Tipp: In den Einstellungen → Push-Center kannst du eine Test-Mitteilung senden."
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNotificationRow(
    entry: NotificationLogEntity,
    semantics: HiUniSemanticColors,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        // Confirm bei „Settled" → false (Reset-Gesture, nicht löschen).
        // Bei StartToEnd/EndToStart → true und onDismiss aufrufen.
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd ||
                value == SwipeToDismissBoxValue.EndToStart
            ) {
                onDismiss()
                true
            } else false
        },
        // Trigger erst bei 40% Wischweite — kürzer als Default und matched das
        // Erwartungsverhalten von Gmail/Inbox-ähnlichen Listen.
        positionalThreshold = { totalDistance -> totalDistance * 0.4f }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeDeleteBackground(semantics) },
        modifier = Modifier.fillMaxWidth()
    ) {
        NotificationRow(
            entry = entry,
            semantics = semantics,
            onClick = onClick
        )
    }
}

@Composable
private fun SwipeDeleteBackground(semantics: HiUniSemanticColors) {
    // fillMaxSize statt fillMaxWidth — sonst wrappt die Surface auf die
    // Höhe der eigenen Icon-Row (~44dp) und die rote Fläche steht kleiner
    // als die Notification-Card.
    Surface(
        color = semantics.red,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = semantics.onRed,
                modifier = Modifier.size(20.dp)
            )
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = semantics.onRed,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun NotificationRow(
    entry: NotificationLogEntity,
    semantics: HiUniSemanticColors,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val (icon, accent, surface) = kindStyling(entry.kind, colors, semantics)
    val rowBg = if (entry.isRead) colors.surface else surface
    val hasTarget = deepLinkDestinationFor(entry.kind) != null
    val clickLabel = when {
        !entry.isRead && hasTarget -> "Als gelesen markieren und öffnen"
        !entry.isRead -> "Als gelesen markieren"
        hasTarget -> "Öffnen"
        else -> null
    }
    Surface(
        color = rowBg,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = clickLabel,
                role = Role.Button,
                onClick = onClick
            )
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
    NotificationKind.COURSE -> KindStyling(
        icon = Icons.Outlined.MenuBook,
        accent = colors.primary,
        surface = colors.primaryContainer
    )
    NotificationKind.LEARNWEB -> KindStyling(
        icon = Icons.Outlined.School,
        accent = semantics.amber,
        surface = semantics.amberSurface
    )
    NotificationKind.SYSTEM -> KindStyling(
        icon = Icons.Outlined.Info,
        accent = semantics.onSurfaceMuted,
        surface = colors.surface
    )
}

/**
 * Mapping von [NotificationKind] auf das Ziel, das beim Tap geöffnet wird.
 * `null` = noch kein passendes Feature vorhanden (z.B. Klausurplan), Tap
 * markiert dann nur als gelesen.
 *
 * Bewusst grob nach Kind statt feingranular pro `refKey` — Calendar/Bib/Email
 * haben (noch) keine Detail-Routen für einzelne Items, und der Hub-Sprung
 * zum Feature-Screen reicht im Alltag. Falls später z.B. ein bestimmter
 * Mail-UID anspringen soll, hier den refKey-Switch ergänzen.
 */
private fun deepLinkDestinationFor(kind: NotificationKind): Destination? = when (kind) {
    NotificationKind.EVENT -> Destination.Calendar
    NotificationKind.MAIL -> Destination.Email
    NotificationKind.BIB -> Destination.Bib
    NotificationKind.SPORT -> Destination.Sport
    NotificationKind.EXAM -> Destination.Exams
    NotificationKind.COURSE -> Destination.Courses
    NotificationKind.LEARNWEB -> Destination.Learnweb
    NotificationKind.GRADE -> Destination.Grades
    NotificationKind.SYSTEM -> Destination.Settings
    NotificationKind.MENSA,
    NotificationKind.MOVIE -> null
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

