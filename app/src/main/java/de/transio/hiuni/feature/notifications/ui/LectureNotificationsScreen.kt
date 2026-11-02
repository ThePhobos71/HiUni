package de.transio.hiuni.feature.notifications.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.design.components.HiUniTopBar
import de.transio.hiuni.feature.notifications.LectureNotificationRow
import de.transio.hiuni.feature.notifications.LectureNotificationsViewModel

/**
 * Übersichtszeile „Vorlesungen" im Erinnerungen-&-Push-Screen: „Live-Anzeige für 4
 * von 6 Kursen, Erinnerung für 2". Öffnet die Kurs-Liste ([LectureNotificationsScreen]).
 */
@Composable
fun LectureNotificationsOverviewCard(
    onOpen: () -> Unit,
    viewModel: LectureNotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Vorlesungs-Mitteilungen öffnen", role = Role.Button, onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(color = colors.primaryContainer, shape = RoundedCornerShape(HiUniRadii.tile)) {
                Icon(
                    imageVector = Icons.Outlined.School,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Vorlesungen",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Text(
                    text = if (state.isLoaded) state.summary else "Lädt …",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = semantics.onSurfaceMuted
            )
        }
    }
}

/**
 * Vorlesungs-Mitteilungen je Kurs des aktuellen Semesters: Live-Anzeige an/aus und
 * Erinnerungs-Vorlauf, plus „Alle an" / „Alle aus" für die Live-Anzeige.
 */
@Composable
fun LectureNotificationsScreen(
    onBack: () -> Unit,
    viewModel: LectureNotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            HiUniTopBar(
                title = "Vorlesungen",
                onBack = onBack,
                subtitle = state.semester ?: "Mitteilungen je Kurs"
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Live-Anzeige: fortlaufende Mitteilung mit Restzeit, solange die " +
                            "Vorlesung läuft. Erinnerung: Termin-Erinnerung vor Beginn.",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                if (!state.liveCategoryEnabled) {
                    item { LiveCategoryOffHint() }
                }
                if (state.isLoaded && state.rows.isEmpty()) {
                    item {
                        Text(
                            text = "Noch keine Kurse im aktuellen Semester. Kurse kommen über den LSF-Import.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = semantics.onSurfaceMuted,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                }
                if (state.rows.isNotEmpty()) {
                    item {
                        BulkLiveRow(
                            summary = "${state.liveCount} von ${state.total} an",
                            onAllOn = { viewModel.setAllLive(true) },
                            onAllOff = { viewModel.setAllLive(false) }
                        )
                    }
                    item {
                        Surface(
                            color = colors.surface,
                            shape = RoundedCornerShape(HiUniRadii.card),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                state.rows.forEachIndexed { index, row ->
                                    if (index > 0) HorizontalDivider(color = colors.outline.copy(alpha = 0.25f))
                                    CourseRow(
                                        row = row,
                                        onLiveChange = { viewModel.setLiveEnabled(row.key, it) },
                                        onReminderChange = { viewModel.setReminderMinutes(row.key, it) }
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun LiveCategoryOffHint() {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.primaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "„Laufende Vorlesung“ ist unter Kategorien ausgeschaltet, die Live-Anzeige " +
                "erscheint gerade für keinen Kurs. Erinnerungen kommen trotzdem.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun BulkLiveRow(summary: String, onAllOn: () -> Unit, onAllOff: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "LIVE-ANZEIGE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = semantics.onSurfaceMuted
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = semantics.onSurfaceMuted
            )
        }
        BulkPill(label = "Alle an", onClick = onAllOn, background = colors.primaryContainer, content = colors.primary)
        BulkPill(label = "Alle aus", onClick = onAllOff, background = semantics.surfaceAlt, content = semantics.onSurfaceMuted)
    }
}

@Composable
private fun BulkPill(
    label: String,
    onClick: () -> Unit,
    background: Color,
    content: Color
) {
    Surface(color = background, shape = RoundedCornerShape(HiUniRadii.pill), onClick = onClick) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun CourseRow(
    row: LectureNotificationRow,
    onLiveChange: (Boolean) -> Unit,
    onReminderChange: (Int?) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                row.detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "Live",
                style = MaterialTheme.typography.labelMedium,
                color = semantics.onSurfaceMuted
            )
            Switch(checked = row.liveEnabled, onCheckedChange = onLiveChange)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Erinnerung",
                style = MaterialTheme.typography.labelMedium,
                color = semantics.onSurfaceMuted
            )
            LectureReminderChips(
                selected = row.reminderMinutes,
                onSelect = onReminderChange,
                compact = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
