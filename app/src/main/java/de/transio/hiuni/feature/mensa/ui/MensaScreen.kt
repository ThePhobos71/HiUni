package de.transio.hiuni.feature.mensa.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.feature.mensa.MensaViewModel
import de.transio.hiuni.feature.mensacard.ui.MensaCardSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MensaScreen(viewModel: MensaViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MensaHeader(
                state = state,
                onSelectMealtime = viewModel::selectMealtime,
                onSelectCategory = viewModel::toggleCategory,
                onSelectDate = viewModel::selectDate
            )
            MensaCardSection(
                modifier = Modifier.padding(
                    start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp
                )
            )
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                MealList(
                    announcements = state.announcements,
                    meals = state.visibleMeals,
                    selectedDate = state.selectedDate,
                    onPin = viewModel::pinToCalendar
                )
            }
        }
    }
}
