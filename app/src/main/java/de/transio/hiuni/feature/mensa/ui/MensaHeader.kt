package de.transio.hiuni.feature.mensa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.common.DateTimeFormats
import de.transio.hiuni.core.common.isWeekend
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.mensa.DietFilter
import de.transio.hiuni.feature.mensa.Mealtime
import de.transio.hiuni.feature.mensa.MensaUiState
import de.transio.hiuni.feature.mensa.data.MensaHours
import de.transio.hiuni.feature.mensa.data.OpenStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayNumber = DateTimeFormatter.ofPattern("d", Locale.GERMAN)

@Composable
internal fun MensaHeader(
    state: MensaUiState,
    onSelectMealtime: (Mealtime) -> Unit,
    onSelectDietFilter: (DietFilter?) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenSearch: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mensa",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.onSurface
                )
                Text(
                    text = subtitle(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OpenBadge(
                    status = MensaHours.statusFor(
                        date = state.selectedDate,
                        mealtime = state.selectedMealtime,
                        locationId = state.mensaLocationId
                    )
                )
                // Such-Icon — öffnet eine inline Search-Bar, die Header + Content ersetzt.
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onOpenSearch)
                        .semantics { role = Role.Button },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(HiUniRadii.tile - 4.dp))
                            .background(semantics.surfaceAlt),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Suchen",
                            tint = colors.onSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        MealtimeToggle(active = state.selectedMealtime, onSelect = onSelectMealtime)
        Spacer(Modifier.height(14.dp))
        WeekStrip(selected = state.selectedDate, onSelect = onSelectDate)
        if (state.availableDietFilters.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            DietFilterPills(
                filters = state.availableDietFilters,
                active = state.activeDietFilter,
                onToggle = onSelectDietFilter
            )
        }
    }
}

@Composable
private fun MealtimeToggle(active: Mealtime, onSelect: (Mealtime) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = semantics.surfaceAlt,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Mealtime.entries.forEach { mealtime ->
                val isActive = mealtime == active
                Surface(
                    color = if (isActive) colors.surface else semantics.surfaceAlt,
                    shape = RoundedCornerShape(HiUniRadii.tile),
                    onClick = { onSelect(mealtime) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.Button }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = mealtime.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isActive) colors.onSurface else semantics.onSurfaceMuted
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${mealtime.from.format(DateTimeFormats.time24)} – ${mealtime.to.format(DateTimeFormats.time24)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) semantics.onSurfaceMuted
                            else semantics.onSurfaceMuted.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val anchor = if (today.isWeekend()) {
        today.with(DayOfWeek.MONDAY).plusWeeks(1)
    } else {
        today.with(DayOfWeek.MONDAY)
    }
    // 4 Wochen Mo–Fr (= 20 Tage) horizontal scrollbar. Reicht, weil STW-ON ~2
    // Wochen voraus liefert — der User kann aber in beide vorlinks navigieren
    // ohne dass wir Daten zur Verfügung stellen müssen (Tap auf einen leeren Tag
    // zeigt einfach Empty-State).
    val weekDays = remember(anchor) {
        (0..3).flatMap { w ->
            (0..4).map { d -> anchor.plusWeeks(w.toLong()).plusDays(d.toLong()) }
        }
    }
    val listState = rememberLazyListState()
    // Beim ersten Composieren UND wenn selected wechselt: scrolle in den
    // sichtbaren Bereich. Spart das manuelle Wischen, wenn der User auf
    // einen Search-Treffer in der nächsten Woche springt.
    androidx.compose.runtime.LaunchedEffect(selected, weekDays) {
        val idx = weekDays.indexOfFirst { it == selected }
        if (idx >= 0) {
            listState.animateScrollToItem(
                index = idx,
                scrollOffset = if (idx == 0) 0 else -16
            )
        }
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(end = 8.dp)
    ) {
        items(items = weekDays, key = { it.toEpochDay() }) { day ->
            WeekDayCell(
                day = day,
                isSelected = day == selected,
                isToday = day == today,
                modifier = Modifier.width(56.dp),
                onClick = { onSelect(day) }
            )
        }
    }
}

@Composable
private fun WeekDayCell(
    day: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    if (isSelected) {
        Surface(
            color = colors.primary,
            shape = RoundedCornerShape(HiUniRadii.tile),
            onClick = onClick,
            modifier = modifier.semantics {
                role = Role.Button
                onClick(label = "Tag auswählen", action = null)
            }
        ) {
            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = day.format(DateTimeFormats.weekdayShort),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onPrimary,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = day.format(dayNumber),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.onPrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(HiUniRadii.tile))
                .clickable(onClickLabel = "Tag auswählen") { onClick() }
                .semantics { role = Role.Button }
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = day.format(DateTimeFormats.weekdayShort),
                style = MaterialTheme.typography.labelMedium,
                color = semantics.onSurfaceMuted,
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = day.format(dayNumber),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onSurface,
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.height(4.dp))
            if (isToday) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                )
            } else {
                Spacer(Modifier.size(5.dp))
            }
        }
    }
}

@Composable
private fun DietFilterPills(
    filters: List<DietFilter>,
    active: DietFilter?,
    onToggle: (DietFilter?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item(key = "all") {
            DietPill(label = "Alle", selected = active == null, onClick = { onToggle(null) })
        }
        items(filters, key = { it.name }) { filter ->
            DietPill(
                label = filter.label,
                selected = active == filter,
                onClick = { onToggle(filter) }
            )
        }
    }
}

@Composable
private fun DietPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val background = if (selected) colors.primary else semantics.surfaceAlt
    val foreground = if (selected) colors.onPrimary else semantics.onSurfaceMuted
    Surface(
        color = background,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
        modifier = Modifier.semantics { role = Role.Button }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun OpenBadge(status: OpenStatus) {
    val semantics = HiUniColors.semantics
    val colors = MaterialTheme.colorScheme
    val (background, foreground, label) = when (status) {
        OpenStatus.Open ->
            Triple(semantics.greenSurface, semantics.green, "GEÖFFNET")
        is OpenStatus.ClosingSoon ->
            Triple(semantics.amberSurface, semantics.amber, "SCHLIESST IN ${status.minutes} MIN")
        is OpenStatus.OpensLater ->
            Triple(colors.primaryContainer, colors.primary, "AB ${status.time.format(DateTimeFormats.time24)} UHR")
        OpenStatus.ClosedToday ->
            Triple(semantics.surfaceAlt, semantics.onSurfaceMuted, "GESCHLOSSEN")
        OpenStatus.Preview ->
            Triple(semantics.surfaceAlt, semantics.onSurfaceMuted, "VORSCHAU")
    }
    Surface(color = background, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

private fun subtitle(state: MensaUiState): String {
    val mealtime = state.selectedMealtime
    val hours = "${mealtime.from.format(DateTimeFormats.time24)} – ${mealtime.to.format(DateTimeFormats.time24)} Uhr"
    return "${state.selectedDate.format(DateTimeFormats.dayFull)} · $hours"
}
