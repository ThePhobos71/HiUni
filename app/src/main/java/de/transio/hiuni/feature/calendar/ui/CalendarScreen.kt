package de.transio.hiuni.feature.calendar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.calendar.CalendarViewMode
import de.transio.hiuni.feature.calendar.CalendarViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var defaultReminder by remember { mutableIntStateOf(15) }

    LaunchedEffect(Unit) {
        defaultReminder = viewModel.defaultReminderMinutes()
    }

    Scaffold(
        containerColor = colors.background,
        // outer AdaptiveScaffold already strips system bars — avoid double-inset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAdd() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Event") },
                containerColor = colors.primary,
                contentColor = colors.onPrimary
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            CalendarHeader(
                viewMode = state.viewMode,
                onSelectMode = viewModel::selectViewMode
            )
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = state.viewMode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "calendar-view-mode"
                ) { mode ->
                    when (mode) {
                        CalendarViewMode.LIST -> CalendarListView(
                            events = state.events,
                            onClickEvent = viewModel::openEdit
                        )
                        CalendarViewMode.DAY -> CalendarDayView(
                            date = state.selectedDate,
                            events = state.events,
                            onClickEvent = viewModel::openEdit
                        )
                        CalendarViewMode.WEEK -> CalendarWeekView(
                            selectedDate = state.selectedDate,
                            events = state.events,
                            onSelectDay = viewModel::selectDate,
                            onClickEvent = viewModel::openEdit
                        )
                    }
                }
            }
        }
    }

    if (state.isAddSheetOpen) {
        AddEditEventSheet(
            initial = state.editing,
            defaultReminderMinutes = defaultReminder,
            onDismiss = viewModel::closeAddOrEdit,
            onSave = { id, title, description, location, start, end, reminder ->
                scope.launch {
                    viewModel.save(id, title, description, location, start, end, reminder)
                }
            },
            onDelete = { event ->
                scope.launch { viewModel.delete(event) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarHeader(
    viewMode: CalendarViewMode,
    onSelectMode: (CalendarViewMode) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 16.dp)
    ) {
        Text(
            text = "Kalender",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onSurface
        )
        Row(
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val segmentedColors = SegmentedButtonDefaults.colors(
                activeContainerColor = colors.primaryContainer,
                activeContentColor = colors.primary,
                activeBorderColor = colors.primary,
                inactiveContainerColor = Color.Transparent,
                inactiveContentColor = colors.onSurfaceVariant,
                inactiveBorderColor = colors.outline
            )
            SingleChoiceSegmentedButtonRow {
                CalendarViewMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = viewMode == mode,
                        onClick = { onSelectMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = CalendarViewMode.entries.size
                        ),
                        colors = segmentedColors,
                        label = {
                            Text(
                                text = when (mode) {
                                    CalendarViewMode.LIST -> "Liste"
                                    CalendarViewMode.DAY -> "Tag"
                                    CalendarViewMode.WEEK -> "Woche"
                                }
                            )
                        }
                    )
                }
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") HiUniRadii
}
