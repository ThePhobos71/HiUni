package de.transio.hiuni.feature.sport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.common.DateTimeFormats
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.sport.SportUiState
import de.transio.hiuni.feature.sport.SportViewModel
import de.transio.hiuni.feature.sport.data.SportEventEntity
import de.transio.hiuni.ui.responsive.FullWidthContent
import de.transio.hiuni.ui.responsive.LocalWindowSizeClass
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val ZONE = ZoneId.of("Europe/Berlin")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportScreen(
    onOpenDetail: (Long) -> Unit = {},
    viewModel: SportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.lastError) {
        val err = state.lastError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(err)
        viewModel.consumeError()
    }

    // Auf Tablet-Landscape rendert SportBody ein 2-Spalten-Grid für Events —
    // damit das sinnvoll zur Geltung kommt, opt-out vom 1100dp-Cap.
    FullWidthContent {
    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    SportHeader(state = state)
                    if (state.distinctTitles.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        FilterChipsRow(
                            titles = state.distinctTitles,
                            selected = state.selectedFilter,
                            onSelect = viewModel::setFilter
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    SportBody(state = state, onOpenDetail = onOpenDetail)
                }
            }
        }
    }
    } // end FullWidthContent
}

/* ───────────────────────────────────────────────────────────
 * Header
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun SportHeader(state: SportUiState) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val count = state.filteredEvents.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Hochschulsport",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            StatusPill(
                text = "$count TERMINE",
                color = semantics.green,
                background = semantics.greenSurface
            )
        }
        Text(
            text = "Aus dem supersaas-Plan",
            style = MaterialTheme.typography.bodySmall,
            color = semantics.onSurfaceMuted,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/* ───────────────────────────────────────────────────────────
 * Filter-Chips
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun FilterChipsRow(
    titles: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        item(key = "__all__") {
            FilterChip(
                label = "Alle",
                active = selected == null,
                onClick = { onSelect(null) }
            )
        }
        items(titles, key = { it }) { title ->
            FilterChip(
                label = title,
                active = selected == title,
                onClick = { onSelect(if (selected == title) null else title) }
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = if (active) colors.primary else semantics.surfaceAlt,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) colors.onPrimary else colors.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/* ───────────────────────────────────────────────────────────
 * Body — Tagesgruppen + Event-Karten
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun SportBody(state: SportUiState, onOpenDetail: (Long) -> Unit) {
    val semantics = HiUniColors.semantics
    val events = state.filteredEvents

    if (events.isEmpty() && state.isRefreshing) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    if (events.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state.selectedFilter != null) "Keine Termine für \"${state.selectedFilter}\""
                else "Keine Termine im Plan-Fenster",
                style = MaterialTheme.typography.bodyMedium,
                color = semantics.onSurfaceMuted
            )
        }
        return
    }

    // Tagesgruppierung via lokalem Datum in Europe/Berlin — DST-stabil.
    val grouped = remember(events) {
        events.groupBy { it.startTime.atZone(ZONE).toLocalDate() }
            .toSortedMap()
    }

    val isExpanded = LocalWindowSizeClass.current?.widthSizeClass == WindowWidthSizeClass.Expanded

    if (isExpanded) {
        // Tablet-Landscape: 2-Spalten-Grid. DayHeader spannt beide Spalten,
        // damit die Tages-Gruppierung visuell klar bleibt.
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for ((date, dayEvents) in grouped) {
                item(
                    key = "header-$date",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    DayHeader(date = date)
                }
                items(dayEvents, key = { it.supersaasSlotId }) { event ->
                    EventCard(event = event, onClick = { onOpenDetail(event.supersaasSlotId) })
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for ((date, dayEvents) in grouped) {
            item(key = "header-$date") {
                DayHeader(date = date)
            }
            items(dayEvents, key = { it.supersaasSlotId }) { event ->
                EventCard(event = event, onClick = { onOpenDetail(event.supersaasSlotId) })
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    val semantics = HiUniColors.semantics
    val today = LocalDate.now(ZONE)
    val label = when (date) {
        today -> "HEUTE"
        today.plusDays(1) -> "MORGEN"
        else -> {
            // "MO, 1. JUL"
            date.format(DateTimeFormats.dayShort).uppercase(Locale.GERMAN).replace(".,", ",")
        }
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = semantics.onSurfaceMuted,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
    )
}

/* ───────────────────────────────────────────────────────────
 * Event-Karte
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun EventCard(event: SportEventEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    val start = event.startTime.atZone(ZONE).toLocalTime().format(DateTimeFormats.time24)
    val end = event.endTime.atZone(ZONE).toLocalTime().format(DateTimeFormats.time24)

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.title.ifBlank { "Termin" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (event.isCancelled) semantics.red else colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                CapacityPill(event = event)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$start – $end Uhr",
                style = MaterialTheme.typography.bodySmall,
                color = semantics.onSurfaceMuted,
                fontWeight = FontWeight.Medium
            )
            if (!event.location.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                LocationPill(text = event.location)
            }
        }
    }
}

@Composable
private fun CapacityPill(event: SportEventEntity) {
    val semantics = HiUniColors.semantics
    val (text, color, bg) = when {
        event.isCancelled -> Triple("ABGESAGT", semantics.red, semantics.redSurface)
        event.waitlistCount > 0 -> Triple(
            "WARTELISTE ${event.waitlistCount}",
            semantics.amber,
            semantics.amberSurface
        )
        event.capacity <= 0 -> Triple("OFFEN", semantics.green, semantics.greenSurface)
        event.freeSpots == 0 -> Triple("VOLL", semantics.red, semantics.redSurface)
        else -> Triple(
            "${event.freeSpots}/${event.capacity} FREI",
            semantics.green,
            semantics.greenSurface
        )
    }
    StatusPill(text = text, color = color, background = bg)
}

@Composable
private fun LocationPill(text: String) {
    val semantics = HiUniColors.semantics
    Surface(
        color = semantics.greenSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = semantics.green,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StatusPill(text: String, color: Color, background: Color) {
    Surface(
        color = background,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
