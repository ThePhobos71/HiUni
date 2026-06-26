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
import androidx.compose.material3.HorizontalDivider
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
import de.transio.hiuni.feature.settings.MAX_NAV_TABS
import de.transio.hiuni.feature.settings.MIN_NAV_TABS
import de.transio.hiuni.feature.settings.NavTabsViewModel
import de.transio.hiuni.navigation.Destination

@Composable
fun NavSettingsScreen(
    onBack: () -> Unit,
    viewModel: NavTabsViewModel = hiltViewModel()
) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val available by viewModel.availableForAdd.collectAsStateWithLifecycle()
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
                            text = "Tab-Leiste anpassen",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.onSurface
                        )
                        Text(
                            text = "${tabs.size} von max. $MAX_NAV_TABS Tabs gewählt",
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
                item { PreviewBar(tabs = tabs) }
                item {
                    Text(
                        text = "IN DER LEISTE (${tabs.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = semantics.onSurfaceMuted,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                    )
                }
                items(tabs, key = { it.route }) { dest ->
                    val idx = tabs.indexOf(dest)
                    TabInListRow(
                        destination = dest,
                        isFirst = idx == 0,
                        isLast = idx == tabs.size - 1,
                        canRemove = tabs.size > MIN_NAV_TABS && dest != Destination.Home,
                        onUp = { viewModel.move(dest.route, -1) },
                        onDown = { viewModel.move(dest.route, +1) },
                        onRemove = { viewModel.remove(dest.route) }
                    )
                }
                if (available.isNotEmpty()) {
                    item {
                        Text(
                            text = "AUSGEBLENDET (${available.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = semantics.onSurfaceMuted,
                            modifier = Modifier.padding(start = 4.dp, top = 14.dp)
                        )
                    }
                    items(available, key = { it.route }) { dest ->
                        AvailableTabRow(
                            destination = dest,
                            canAdd = tabs.size < MAX_NAV_TABS,
                            onAdd = { viewModel.add(dest.route) }
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun PreviewBar(tabs: List<Destination>) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp)) {
            Text(
                text = "VORSCHAU",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = semantics.onSurfaceMuted,
                modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                tabs.forEachIndexed { i, dest ->
                    val active = i == 0
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 34.dp, height = 30.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) colors.primaryContainer else androidx.compose.ui.graphics.Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = null,
                                tint = if (active) colors.primary else semantics.onSurfaceMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = dest.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) colors.primary else semantics.onSurfaceMuted,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
                repeat(MAX_NAV_TABS - tabs.size) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(semantics.surfaceAlt)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = semantics.onSurfaceMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabInListRow(
    destination: Destination,
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
                    imageVector = destination.icon,
                    contentDescription = null,
                    tint = colors.primary
                )
            }
            Text(
                text = destination.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
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
private fun AvailableTabRow(
    destination: Destination,
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
                    imageVector = destination.icon,
                    contentDescription = null,
                    tint = semantics.onSurfaceMuted
                )
            }
            Text(
                text = destination.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
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
