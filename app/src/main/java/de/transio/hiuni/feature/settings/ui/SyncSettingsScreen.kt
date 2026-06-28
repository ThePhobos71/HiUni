package de.transio.hiuni.feature.settings.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.components.HiUniTopBar
import de.transio.hiuni.feature.settings.LsfSyncIntervalOptions
import de.transio.hiuni.feature.settings.SettingsViewModel
import de.transio.hiuni.feature.settings.SyncIntervalOptions
import de.transio.hiuni.feature.settings.SyncJob

/**
 * Synchronisation: LSF-Auto-Sync-Intervall, E-Mail-Sync-Intervall und
 * Live-Status aller Hintergrund-Jobs inkl. „Jetzt synchronisieren"-Aktion.
 */
@Composable
fun SyncSettingsScreen(
    onBack: () -> Unit,
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
            HiUniTopBar(
                title = "Synchronisation",
                onBack = onBack,
                subtitle = "Hintergrund-Sync-Intervalle und Status"
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                            val running = SyncJob.LSF in state.runningSyncs
                            TextButton(
                                onClick = { viewModel.syncLsfNow() },
                                enabled = !running
                            ) {
                                Text(if (running) "Synchronisiere…" else "Jetzt synchronisieren")
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
                    SectionCard(
                        icon = Icons.Outlined.CloudSync,
                        title = "Sync-Status",
                        subtitle = "Letzte Aktualisierung pro Hintergrund-Job"
                    ) {
                        SyncStatusRow(
                            icon = Icons.Outlined.School,
                            label = "LSF (Kurse + Plan)",
                            lastEpoch = state.lastLsfSyncEpoch,
                            isRunning = SyncJob.LSF in state.runningSyncs,
                            onSync = { viewModel.syncLsfNow() }
                        )
                        SyncStatusDivider()
                        SyncStatusRow(
                            icon = Icons.Outlined.EventAvailable,
                            label = "Klausuren",
                            lastEpoch = state.lastLsfExamsRefreshEpoch,
                            isRunning = SyncJob.LSF in state.runningSyncs,
                            onSync = { viewModel.syncLsfNow() }
                        )
                        SyncStatusDivider()
                        SyncStatusRow(
                            icon = Icons.Outlined.LocalDining,
                            label = "Mensa",
                            lastEpoch = state.lastMensaRefreshEpoch,
                            isRunning = SyncJob.MENSA in state.runningSyncs,
                            onSync = { viewModel.syncMensaNow() }
                        )
                        SyncStatusDivider()
                        SyncStatusRow(
                            icon = Icons.Outlined.SportsBasketball,
                            label = "Hochschulsport",
                            lastEpoch = state.lastSportRefreshEpoch,
                            isRunning = SyncJob.SPORT in state.runningSyncs,
                            onSync = { viewModel.syncSportNow() }
                        )
                        SyncStatusDivider()
                        SyncStatusRow(
                            icon = Icons.Outlined.Mail,
                            label = "Uni-Mails",
                            lastEpoch = state.lastEmailSyncEpoch,
                            isRunning = SyncJob.EMAIL in state.runningSyncs,
                            onSync = { viewModel.syncEmailNow() }
                        )
                        SyncStatusDivider()
                        SyncStatusRow(
                            icon = Icons.Outlined.Movie,
                            label = "Uni-Kino",
                            lastEpoch = state.lastMoviesRefreshEpoch,
                            isRunning = SyncJob.MOVIES in state.runningSyncs,
                            onSync = { viewModel.syncMoviesNow() }
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
