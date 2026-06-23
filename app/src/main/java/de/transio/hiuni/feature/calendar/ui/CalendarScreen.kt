package de.transio.hiuni.feature.calendar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.calendar.CalendarViewMode
import de.transio.hiuni.feature.calendar.CalendarViewModel
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onOpenCourse: (String) -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var defaultReminder by remember { mutableIntStateOf(15) }

    LaunchedEffect(Unit) {
        defaultReminder = viewModel.defaultReminderMinutes()
    }

    // LSF-Stundenplan-Events bekommen statt "3204 Logistik und Produktion 1" das
    // kompakte Modulkürzel ("IT-EINF1") als Titel — die volle Bezeichnung ist im
    // Day-Block ohnehin abgeschnitten. Click greift weiterhin auf courseLsfId zu.
    val displayedEvents = remember(state.events, state.courseShortNameByLsfId) {
        state.events.map { event ->
            val short = event.courseLsfId?.let { state.courseShortNameByLsfId[it] }
            if (short != null && short != event.title) event.copy(title = short) else event
        }
    }

    // Click-Handler für Events: LSF-Stundenplan mit verknüpftem Kurs → springt
    // direkt zur Kurs-Detail-Seite; alles andere öffnet das Edit-Sheet.
    val onClickEvent: (CustomEventEntity) -> Unit = { event ->
        val lsfId = event.courseLsfId
        if (event.sourceKind == CustomEventEntity.SOURCE_LSF_STUNDENPLAN && lsfId != null) {
            onOpenCourse(lsfId)
        } else {
            viewModel.openEdit(event)
        }
    }

    Scaffold(
        containerColor = colors.background,
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
                selectedDate = state.selectedDate,
                onSelectMode = viewModel::selectViewMode,
                onStep = { delta ->
                    viewModel.selectDate(stepDate(state.viewMode, state.selectedDate, delta))
                },
                onToday = { viewModel.selectDate(LocalDate.now()) }
            )
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = state.viewMode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "calendar-view-mode"
                ) { mode ->
                    when (mode) {
                        CalendarViewMode.DAY -> CalendarDayView(
                            selectedDate = state.selectedDate,
                            events = displayedEvents,
                            onSelectDay = viewModel::selectDate,
                            onLongPressDay = viewModel::openAddOnDate,
                            onClickEvent = onClickEvent
                        )
                        CalendarViewMode.WEEK -> CalendarWeekView(
                            selectedDate = state.selectedDate,
                            events = displayedEvents,
                            onSelectDay = { date ->
                                viewModel.selectDate(date)
                                viewModel.selectViewMode(CalendarViewMode.DAY)
                            },
                            onLongPressDay = viewModel::openAddOnDate,
                            onClickEvent = onClickEvent
                        )
                        CalendarViewMode.MONTH -> CalendarMonthView(
                            selectedDate = state.selectedDate,
                            events = displayedEvents,
                            onSelectDay = viewModel::selectDate,
                            onLongPressDay = viewModel::openAddOnDate,
                            onClickEvent = onClickEvent
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
            },
            initialDate = state.initialDateForAdd
        )
    }
}

@Composable
private fun CalendarHeader(
    viewMode: CalendarViewMode,
    selectedDate: LocalDate,
    onSelectMode: (CalendarViewMode) -> Unit,
    onStep: (Int) -> Unit,
    onToday: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = "Stundenplan",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            if (selectedDate != LocalDate.now()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(HiUniRadii.pill))
                        .background(semantics.surfaceAlt)
                        .clickable { onToday() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Heute",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        // Segmented pill switcher — surfaceAlt container, active tab = surface + shadow.
        Row(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(HiUniRadii.tile))
                .background(semantics.surfaceAlt)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CalendarViewMode.entries.forEach { mode ->
                val active = mode == viewMode
                val label = when (mode) {
                    CalendarViewMode.DAY -> "Tag"
                    CalendarViewMode.WEEK -> "Woche"
                    CalendarViewMode.MONTH -> "Monat"
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (active) Modifier.shadow(
                                elevation = 2.dp,
                                shape = RoundedCornerShape(HiUniRadii.tile - 4.dp),
                                clip = false
                            ) else Modifier
                        )
                        .clip(RoundedCornerShape(HiUniRadii.tile - 4.dp))
                        .background(if (active) colors.surface else Color.Transparent)
                        .clickable { onSelectMode(mode) }
                        .padding(vertical = 8.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) colors.onSurface else semantics.onSurfaceMuted,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        // Periodennavigation: ← Periode → ; springt um eine Woche bzw. einen Monat.
        Row(
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            NavArrow(
                onClick = { onStep(-1) },
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Zurück"
            )
            Text(
                text = periodLabel(viewMode, selectedDate),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            NavArrow(
                onClick = { onStep(1) },
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Weiter"
            )
        }
    }
}

@Composable
private fun NavArrow(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(HiUniRadii.tile - 4.dp))
            .background(semantics.surfaceAlt)
            .clickable(onClick = onClick),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.onSurface
        )
    }
}

private fun stepDate(mode: CalendarViewMode, date: LocalDate, delta: Int): LocalDate = when (mode) {
    CalendarViewMode.DAY, CalendarViewMode.WEEK -> date.plusWeeks(delta.toLong())
    CalendarViewMode.MONTH -> date.plusMonths(delta.toLong())
}

private fun periodLabel(mode: CalendarViewMode, date: LocalDate): String = when (mode) {
    CalendarViewMode.MONTH ->
        YearMonth.from(date).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
    else -> {
        // Mo–So Woche.
        val start = date.with(DayOfWeek.MONDAY)
        val end = start.plusDays(6)
        val dayFmt = DateTimeFormatter.ofPattern("d.", Locale.GERMAN)
        val monthFmt = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)
        if (start.month == end.month) {
            "${start.format(dayFmt)} – ${end.format(monthFmt)}"
        } else {
            "${start.format(monthFmt)} – ${end.format(monthFmt)}"
        }
    }
}
