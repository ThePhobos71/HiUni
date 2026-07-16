package de.transio.hiuni.feature.calendar.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.minimumInteractiveComponentSize
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.common.DateTimeUtils
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniMotion
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.design.components.HiUniSearchBar
import de.transio.hiuni.feature.calendar.CalendarViewMode
import de.transio.hiuni.feature.calendar.CalendarViewModel
import de.transio.hiuni.ui.responsive.FullWidthContent
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

    val context = LocalContext.current

    // Click-Handler für Events:
    //  - LSF-Stundenplan + verknüpfter Kurs → springt direkt in die Kurs-Detail-Seite.
    //  - Learnweb-Assignment → öffnet die Moodle-URL im Browser (read-only, kein Edit).
    //  - Learnweb-iCal-Event → öffnet die Event-URL im Browser, falls vorhanden;
    //    sonst Edit-Sheet (für reine Calendar-Notizen ohne Link).
    //  - Alles andere → Edit-Sheet.
    val onClickEvent: (CustomEventEntity) -> Unit = { event ->
        val lsfId = event.courseLsfId
        when {
            event.sourceKind == CustomEventEntity.SOURCE_LSF_STUNDENPLAN && lsfId != null ->
                onOpenCourse(lsfId)
            event.sourceKind == CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT ->
                scope.launch {
                    val url = viewModel.resolveLearnwebAssignmentUrl(event) ?: return@launch
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            event.sourceKind == CustomEventEntity.SOURCE_LEARNWEB_ICAL ->
                scope.launch {
                    val url = viewModel.resolveLearnwebICalUrl(event)
                    if (url == null) {
                        // Read-only Event ohne URL → kein Edit, kein Crash. UI
                        // bleibt einfach still; in den DetailSheets könnte man
                        // später Description anzeigen, aktuell ist no-op okay.
                        return@launch
                    }
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            else -> viewModel.openEdit(event)
        }
    }

    // System-Back schließt die Suche bevorzugt vor dem Tab-Wechsel.
    BackHandler(enabled = state.isSearchOpen) { viewModel.closeSearch() }

    // Calendar nutzt die volle Bildschirmbreite — der globale 1100dp-Cap würde
    // den Stundenraster (Day/Week) und das 7-Spalten-Monatsgrid auf Tablet-
    // Landscape unnötig schmal halten. List/Day sind zwar text-lastiger, aber
    // der einheitliche Scaffold-Header (Such-Icon, Today-Pill, Tab-Switcher)
    // soll konsistent über die volle Breite atmen.
    FullWidthContent {
    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (!state.isSearchOpen) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.openAdd() },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Event") },
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isSearchOpen) {
                HiUniSearchBar(
                    query = state.searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    onClose = viewModel::closeSearch,
                    placeholder = "Veranstaltung, Ort, Prof…"
                )
                CalendarSearchResults(
                    query = state.searchQuery,
                    results = state.searchResults,
                    courseShortNames = state.courseShortNameByLsfId,
                    onSelect = viewModel::selectSearchResult
                )
            } else {
                CalendarHeader(
                    viewMode = state.viewMode,
                    selectedDate = state.selectedDate,
                    onSelectMode = viewModel::selectViewMode,
                    onStep = { delta ->
                        viewModel.selectDate(stepDate(state.viewMode, state.selectedDate, delta))
                    },
                    onToday = { viewModel.selectDate(LocalDate.now()) },
                    onOpenSearch = viewModel::openSearch
                )
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    AnimatedContent(
                        targetState = state.viewMode,
                        transitionSpec = {
                            fadeIn(HiUniMotion.contentSwitchTween()) togetherWith
                                fadeOut(HiUniMotion.contentSwitchTween())
                        },
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
    }
    } // end FullWidthContent

    if (state.isAddSheetOpen) {
        AddEditEventSheet(
            initial = state.editing,
            defaultReminderMinutes = defaultReminder,
            onDismiss = viewModel::closeAddOrEdit,
            onSave = { id, title, description, location, start, end, reminder, recurrence ->
                scope.launch {
                    viewModel.save(id, title, description, location, start, end, reminder, recurrence)
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
    onToday: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        .clickable(
                            onClickLabel = "Zu heute springen",
                            role = Role.Button,
                            onClick = onToday
                        )
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
            // Such-Icon — öffnet eine inline Search-Bar, die Header + Content ersetzt.
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(
                        onClickLabel = "Suchen",
                        role = Role.Button,
                        onClick = onOpenSearch
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(HiUniRadii.tile - 4.dp))
                        .background(semantics.surfaceAlt),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    // contentDescription bleibt null — die äußere clickable-Box
                    // trägt bereits das onClickLabel "Suchen".
                    contentDescription = null,
                    tint = colors.onSurface
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
                        .clickable(
                            role = Role.Tab,
                            onClick = { onSelectMode(mode) }
                        )
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
            .minimumInteractiveComponentSize()
            .clickable(
                onClickLabel = contentDescription,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(HiUniRadii.tile - 4.dp))
                .background(semantics.surfaceAlt),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
        Icon(
            imageVector = icon,
            // contentDescription bleibt null — die äußere clickable-Box trägt
            // bereits das onClickLabel, sonst würde TalkBack den Namen doppelt lesen.
            contentDescription = null,
            tint = colors.onSurface
        )
        }
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

/* ──────────────────────────────────────────────────────────────────
 * Volltext-Suche
 * ────────────────────────────────────────────────────────────────── */

@Composable
private fun CalendarSearchResults(
    query: String,
    results: List<CustomEventEntity>,
    courseShortNames: Map<String, String>,
    onSelect: (CustomEventEntity) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val tokens = remember(query) {
        query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            query.isBlank() -> {
                SearchEmptyHint(
                    title = "Wonach suchst du?",
                    subtitle = "Tippe ein, was du suchst — Titel, Ort oder Prof-Name."
                )
            }
            results.isEmpty() -> {
                SearchEmptyHint(
                    title = "Keine Treffer",
                    subtitle = "Tippe noch was anderes."
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 18.dp, end = 18.dp, top = 12.dp, bottom = 100.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = results, key = { it.id }) { event ->
                        SearchResultRow(
                            event = event,
                            tokens = tokens,
                            displayTitle = event.courseLsfId
                                ?.let { courseShortNames[it] }
                                ?: event.title,
                            highlightColor = colors.primaryContainer,
                            onClick = { onSelect(event) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyHint(title: String, subtitle: String) {
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = semantics.onSurfaceMuted.copy(alpha = 0.35f),
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = semantics.onSurfaceMuted,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = semantics.onSurfaceMuted.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun SearchResultRow(
    event: CustomEventEntity,
    tokens: List<String>,
    displayTitle: String,
    highlightColor: Color,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val color = rememberCourseColor(event)
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "Termindetails öffnen",
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 42.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.dot)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = highlightTokens(displayTitle, tokens, highlightColor, colors.onSurface),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(DateTimeUtils.formatRelativeDay(event.startTime))
                        append(" · ")
                        append(DateTimeUtils.formatTime(event.startTime))
                        append(" – ")
                        append(DateTimeUtils.formatTime(event.endTime))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = semantics.onSurfaceMuted,
                    maxLines = 1
                )
                event.location?.takeIf { it.isNotBlank() }?.let { loc ->
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(HiUniRadii.pill))
                            .background(semantics.surfaceAlt)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = loc,
                            style = MaterialTheme.typography.labelSmall,
                            color = semantics.onSurfaceMuted,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hebt alle Tokens (case-insensitiv) im Text mit `background = highlight` hervor.
 * Funktioniert per linearer Scan-Through über alle Token-Match-Bereiche, gemerged
 * zu nicht-überlappenden Intervallen. Wenn keine Treffer → Plain-Text.
 */
private fun highlightTokens(
    text: String,
    tokens: List<String>,
    highlight: Color,
    fg: Color
): AnnotatedString {
    if (tokens.isEmpty() || text.isEmpty()) return AnnotatedString(text)
    val lower = text.lowercase()
    val ranges = mutableListOf<IntRange>()
    tokens.forEach { token ->
        if (token.isBlank()) return@forEach
        var idx = 0
        while (idx <= lower.length - token.length) {
            val found = lower.indexOf(token, idx)
            if (found < 0) break
            ranges.add(found until (found + token.length))
            idx = found + token.length
        }
    }
    if (ranges.isEmpty()) return AnnotatedString(text)
    // Merge überlappende/angrenzende Ranges.
    val merged = ranges.sortedBy { it.first }.fold(mutableListOf<IntRange>()) { acc, r ->
        if (acc.isNotEmpty() && r.first <= acc.last().last + 1) {
            val prev = acc.removeAt(acc.size - 1)
            acc.add(prev.first..maxOf(prev.last, r.last))
        } else {
            acc.add(r)
        }
        acc
    }
    return buildAnnotatedString {
        var cursor = 0
        merged.forEach { range ->
            if (cursor < range.first) append(text.substring(cursor, range.first))
            withStyle(SpanStyle(background = highlight, color = fg)) {
                append(text.substring(range.first, range.last + 1))
            }
            cursor = range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
