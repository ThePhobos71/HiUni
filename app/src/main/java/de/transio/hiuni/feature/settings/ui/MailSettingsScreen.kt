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
import androidx.compose.material.icons.outlined.SwipeLeft
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import de.transio.hiuni.feature.settings.SettingsViewModel

/**
 * Mail-Sektion: Wisch-Gesten, Biometrie-Schutz und „nur lokal löschen".
 */
@Composable
fun MailSettingsScreen(
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
                title = "Mail",
                onBack = onBack,
                subtitle = "Gesten, Schutz und Lösch-Verhalten"
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SectionCard(
                        icon = Icons.Outlined.SwipeLeft,
                        title = "Mail-Wisch-Gesten",
                        subtitle = "Was beim Wischen einer Mail passieren soll"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Nach rechts wischen",
                                style = MaterialTheme.typography.labelMedium,
                                color = HiUniColors.semantics.onSurfaceMuted
                            )
                            SwipeActionRow(
                                selected = state.mailSwipeRightAction,
                                onSelect = { viewModel.setMailSwipeRight(it) }
                            )
                            Text(
                                text = "Nach links wischen",
                                style = MaterialTheme.typography.labelMedium,
                                color = HiUniColors.semantics.onSurfaceMuted
                            )
                            SwipeActionRow(
                                selected = state.mailSwipeLeftAction,
                                onSelect = { viewModel.setMailSwipeLeft(it) }
                            )
                        }
                    }
                }
                item {
                    MailBiometricCard(
                        enabled = state.mailRequiresBiometric,
                        onToggle = { viewModel.setMailRequiresBiometric(it) }
                    )
                }
                item {
                    MailLocalDeleteCard(
                        enabled = state.mailDeleteLocalOnly,
                        onToggle = { viewModel.setMailDeleteLocalOnly(it) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
