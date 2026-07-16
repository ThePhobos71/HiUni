package de.transio.hiuni.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.components.HiUniTopBar
import de.transio.hiuni.feature.notifications.ui.NotificationCategoriesCard
import de.transio.hiuni.feature.settings.ReminderOptions
import de.transio.hiuni.feature.settings.SettingsViewModel
import de.transio.hiuni.feature.settings.SyncJob

/**
 * Erinnerungen und Push: Standard-Vorlauf für Calendar-Reminder und das
 * Push-Center inkl. POST_NOTIFICATIONS-Permission-Flow.
 */
@Composable
fun RemindersSettingsScreen(
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
                title = "Erinnerungen & Push",
                onBack = onBack,
                subtitle = "Reminder-Vorlauf und Mitteilungen"
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                    PushCenterCard(
                        onTestNotification = { viewModel.sendTestNotification() },
                        isTestRunning = SyncJob.TEST_NOTIFY in state.runningSyncs
                    )
                }
                item { NotificationCategoriesCard() }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
