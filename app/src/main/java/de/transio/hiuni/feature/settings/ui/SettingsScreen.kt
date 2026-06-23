package de.transio.hiuni.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.settings.LsfSyncIntervalOptions
import de.transio.hiuni.feature.settings.ReminderOptions
import de.transio.hiuni.feature.settings.SettingsViewModel
import de.transio.hiuni.feature.settings.SyncIntervalOptions
import de.transio.hiuni.feature.settings.data.MensaLocation
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenNavSettings: () -> Unit = {},
    onOpenHomeSettings: () -> Unit = {},
    onOpenQuickAccessSettings: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SettingsHeader()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { CasLoginCard() }
                item { LsfStundenplanCard() }
                item { LsfMyCoursesCard() }
                item {
                    SectionCard(
                        icon = Icons.Outlined.Sync,
                        title = "LSF-Auto-Sync",
                        subtitle = "Kurse und Stundenplan automatisch aktualisieren"
                    ) {
                        ChipRow(
                            options = LsfSyncIntervalOptions,
                            selected = state.lsfSyncIntervalHours,
                            label = { if (it == 0) "Aus" else "$it Std" },
                            onSelect = { viewModel.setLsfInterval(it) }
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Zuletzt: ${formatRelativeAgo(state.lastLsfSyncEpoch)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = HiUniColors.semantics.onSurfaceMuted
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { viewModel.syncLsfNow() }) {
                                Text("Jetzt synchronisieren")
                            }
                        }
                    }
                }
                item { DisplayNameCard() }
                item {
                    SectionCard(
                        icon = Icons.Outlined.LocalDining,
                        title = "Mensa-Standort",
                        subtitle = state.selectedLocation?.name ?: "Standort wählen"
                    ) {
                        state.locations.forEach { location ->
                            LocationRow(
                                location = location,
                                isSelected = location.id == state.selectedLocationId,
                                onClick = { viewModel.selectLocation(location.id) }
                            )
                        }
                    }
                }
                item {
                    SectionCard(
                        icon = Icons.Outlined.Notifications,
                        title = "Termin-Erinnerungen",
                        subtitle = "Standardvorlauf für Calendar-Reminder"
                    ) {
                        ChipRow(
                            options = ReminderOptions,
                            selected = state.notificationMinutesBefore,
                            label = { formatReminderLabel(it) },
                            onSelect = { viewModel.setReminderMinutes(it) }
                        )
                    }
                }
                item {
                    SectionCard(
                        icon = Icons.Outlined.NotificationsActive,
                        title = "Push-Center",
                        subtitle = "Erinnerungen und Mitteilungen aus der App"
                    ) {
                        Text(
                            text = "Schreibt eine Probe-Mitteilung ins Center, ohne auf einen echten Reminder zu warten.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HiUniColors.semantics.onSurfaceMuted
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { viewModel.sendTestNotification() }) {
                                Text("Test-Mitteilung senden")
                            }
                        }
                    }
                }
                item {
                    SectionCard(
                        icon = Icons.Outlined.Sync,
                        title = "E-Mail-Sync",
                        subtitle = "Wie oft soll im Hintergrund nach neuen Mails gesucht werden?"
                    ) {
                        ChipRow(
                            options = SyncIntervalOptions,
                            selected = state.emailSyncIntervalMinutes,
                            label = { "$it Min" },
                            onSelect = { viewModel.setSyncInterval(it) }
                        )
                    }
                }
                item {
                    NavSettingsRow(onClick = onOpenNavSettings)
                }
                item {
                    HomeSettingsRow(onClick = onOpenHomeSettings)
                }
                item {
                    QuickAccessSettingsRow(onClick = onOpenQuickAccessSettings)
                }
                item {
                    CredentialsCard(
                        username = state.credentialsDraft.username.ifEmpty { state.emailUsername },
                        passwordDraft = state.credentialsDraft.password,
                        canSave = state.credentialsDraft.canSave,
                        hasStored = state.hasStoredCredentials,
                        onUsername = viewModel::updateUsername,
                        onPassword = viewModel::updatePassword,
                        onSave = viewModel::saveCredentials,
                        onClear = viewModel::clearCredentials
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun SettingsHeader() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp)
    ) {
        Text(
            text = "Einstellungen",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onSurface
        )
        Text(
            text = "Standort, Erinnerungen und Zugangsdaten",
            style = MaterialTheme.typography.bodyMedium,
            color = HiUniColors.semantics.onSurfaceMuted,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = colors.primaryContainer,
                    shape = RoundedCornerShape(HiUniRadii.tile),
                    modifier = Modifier
                        .clip(RoundedCornerShape(HiUniRadii.tile))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun LocationRow(
    location: MensaLocation,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val background = if (isSelected) colors.primaryContainer else colors.surface
    Surface(
        color = background,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioDot(isSelected = isSelected)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = location.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) colors.primary else colors.onSurface,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = location.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun RadioDot(isSelected: Boolean) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = if (isSelected) colors.primary else colors.surface,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
    ) {
        Surface(
            color = if (isSelected) colors.primary else semantics.surfaceAlt,
            shape = RoundedCornerShape(50)
        ) {
            Spacer(modifier = Modifier.padding(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    options: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.forEach { option ->
            val isActive = option == selected
            Surface(
                color = if (isActive) colors.primary else semantics.surfaceAlt,
                shape = RoundedCornerShape(20.dp),
                onClick = { onSelect(option) }
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isActive) colors.onPrimary else semantics.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CredentialsCard(
    username: String,
    passwordDraft: String,
    canSave: Boolean,
    hasStored: Boolean,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Uni-Hildesheim E-Mail",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface
            )
            Text(
                text = if (hasStored) {
                    "Zugangsdaten verschlüsselt gespeichert. Passwort-Feld leerlassen, um nicht zu überschreiben."
                } else {
                    "RZ-Kennung wird AES-256-GCM verschlüsselt lokal gespeichert."
                },
                style = MaterialTheme.typography.bodySmall,
                color = semantics.onSurfaceMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = username,
                onValueChange = onUsername,
                label = { Text("RZ-Kennung / Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = passwordDraft,
                onValueChange = onPassword,
                label = { Text("Passwort") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (hasStored) {
                    TextButton(onClick = onClear) {
                        Text("Löschen", color = semantics.red)
                    }
                }
                TextButton(onClick = onSave, enabled = canSave) {
                    Text(if (hasStored) "Aktualisieren" else "Speichern")
                }
            }
        }
    }
}

@Composable
private fun QuickAccessSettingsRow(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = colors.primaryContainer,
                shape = RoundedCornerShape(HiUniRadii.tile)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Apps,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Schnellzugriff anpassen",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface
                )
                Text(
                    text = "Kacheln auf der Startseite wählen und sortieren",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun HomeSettingsRow(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = colors.primaryContainer,
                shape = RoundedCornerShape(HiUniRadii.tile)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Home,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Startseite anpassen",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface
                )
                Text(
                    text = "Sektionen sichtbar machen, ausblenden und sortieren",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun NavSettingsRow(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = colors.primaryContainer,
                shape = RoundedCornerShape(HiUniRadii.tile)
            ) {
                Icon(
                    imageVector = Icons.Outlined.GridView,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tab-Leiste anpassen",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface
                )
                Text(
                    text = "Reihenfolge und sichtbare Tabs ändern",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}

private fun formatReminderLabel(minutes: Int): String = when (minutes) {
    0 -> "Aus"
    in 1..59 -> "$minutes Min"
    60 -> "1 Std"
    else -> "${minutes / 60} Std"
}

/**
 * Kurz-Format für "wie lange ist das her", z.B. "vor 5 Min", "vor 3 Std",
 * "vor 2 Tg". Epoch `0` heißt: noch nie synchronisiert.
 */
private fun formatRelativeAgo(epochMillis: Long): String {
    if (epochMillis <= 0L) return "nie"
    val now = Instant.now().toEpochMilli()
    val diff = Duration.ofMillis(now - epochMillis)
    if (diff.isNegative || diff.toMinutes() < 1) return "gerade eben"
    val minutes = diff.toMinutes()
    if (minutes < 60) return "vor $minutes Min"
    val hours = diff.toHours()
    if (hours < 24) return "vor $hours Std"
    val days = diff.toDays()
    return "vor $days Tg"
}
