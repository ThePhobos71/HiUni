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
import androidx.compose.material.icons.outlined.DarkMode
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
import de.transio.hiuni.core.design.ThemeMode
import de.transio.hiuni.core.design.components.HiUniTopBar
import de.transio.hiuni.feature.settings.SettingsViewModel

/**
 * Erscheinungsbild: Theme-Mode und Anzeigename.
 */
@Composable
fun AppearanceSettingsScreen(
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
                title = "Erscheinungsbild",
                onBack = onBack,
                subtitle = "Theme und Anzeigename"
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SectionCard(
                        icon = Icons.Outlined.DarkMode,
                        title = "Erscheinungsbild",
                        subtitle = when (state.themeMode) {
                            ThemeMode.SYSTEM -> "Folgt dem System"
                            ThemeMode.LIGHT -> "Immer hell"
                            ThemeMode.DARK -> "Immer dunkel"
                        }
                    ) {
                        ThemeModeRow(
                            selected = state.themeMode,
                            onSelect = { viewModel.setThemeMode(it) }
                        )
                    }
                }
                item {
                    AppIconCard(
                        selectedVariant = state.appIconVariant,
                        firstSemester = state.firstSemester,
                        currentSemester = state.currentSemester,
                        onSelect = { viewModel.setAppIcon(it) }
                    )
                }
                item { DisplayNameCard() }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
