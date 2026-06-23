package de.transio.hiuni.feature.settings.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.home.HomeSection
import de.transio.hiuni.feature.home.HomeSectionsViewModel

@Composable
fun HomeSettingsScreen(
    onBack: () -> Unit,
    viewModel: HomeSectionsViewModel = hiltViewModel()
) {
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Zurück",
                            tint = colors.onSurface
                        )
                    }
                    Column {
                        Text(
                            text = "Startseite anpassen",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = "${visible.size} Sektionen aktiv",
                            style = MaterialTheme.typography.bodySmall,
                            color = semantics.onSurfaceMuted
                        )
                    }
                }
                TextButton(onClick = { viewModel.reset() }) { Text("Zurücksetzen") }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { InfoNote() }
                item {
                    Text(
                        text = "AKTIV (${visible.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = semantics.onSurfaceMuted,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                    )
                }
                items(visible, key = { it.id }) { section ->
                    val idx = visible.indexOf(section)
                    SectionInListRow(
                        section = section,
                        isFirst = idx == 0,
                        isLast = idx == visible.size - 1,
                        canRemove = true,
                        onUp = { viewModel.move(section.id, -1) },
                        onDown = { viewModel.move(section.id, +1) },
                        onRemove = { viewModel.remove(section.id) }
                    )
                }
                if (hidden.isNotEmpty()) {
                    item {
                        Text(
                            text = "AUSGEBLENDET (${hidden.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = semantics.onSurfaceMuted,
                            modifier = Modifier.padding(start = 4.dp, top = 14.dp)
                        )
                    }
                    items(hidden, key = { it.id }) { section ->
                        AvailableSectionRow(
                            section = section,
                            canAdd = true,
                            onAdd = { viewModel.add(section.id) }
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun InfoNote() {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Der Begrüßungs-Header mit der nächsten Vorlesung bleibt immer sichtbar. " +
                "Hier wählst du, welche weiteren Sektionen darunter erscheinen.",
            style = MaterialTheme.typography.bodySmall,
            color = semantics.onSurfaceMuted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun SectionInListRow(
    section: HomeSection,
    isFirst: Boolean,
    isLast: Boolean,
    canRemove: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile))
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    tint = colors.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Text(
                    text = section.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
            IconButton(onClick = onUp, enabled = !isFirst) {
                Icon(
                    Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "Hoch",
                    tint = if (isFirst) semantics.onSurfaceMuted else colors.onSurface
                )
            }
            IconButton(onClick = onDown, enabled = !isLast) {
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Runter",
                    tint = if (isLast) semantics.onSurfaceMuted else colors.onSurface
                )
            }
            IconButton(onClick = onRemove, enabled = canRemove) {
                Icon(
                    Icons.Outlined.Remove,
                    contentDescription = "Entfernen",
                    tint = if (canRemove) semantics.red else semantics.onSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun AvailableSectionRow(
    section: HomeSection,
    canAdd: Boolean,
    onAdd: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile))
                    .background(semantics.surfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    tint = semantics.onSurfaceMuted
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Text(
                    text = section.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
            TextButton(onClick = onAdd, enabled = canAdd) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    tint = if (canAdd) colors.primary else semantics.onSurfaceMuted
                )
                Spacer(Modifier.size(4.dp))
                Text("Hinzufügen")
            }
        }
    }
}
