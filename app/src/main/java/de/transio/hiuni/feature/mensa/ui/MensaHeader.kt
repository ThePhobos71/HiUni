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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.common.isWeekend
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.mensa.Mealtime
import de.transio.hiuni.feature.mensa.MensaUiState
import de.transio.hiuni.feature.mensa.data.MensaHours
import de.transio.hiuni.feature.mensa.data.OpenStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayLabel = DateTimeFormatter.ofPattern("EEE", Locale.GERMAN)
private val dayNumber = DateTimeFormatter.ofPattern("d", Locale.GERMAN)
private val fullDate = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun MensaHeader(
    state: MensaUiState,
    onSelectMealtime: (Mealtime) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenSearch: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
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
                OpenBadge(status = MensaHours.statusFor(state.selectedDate, state.selectedMealtime))
                // Such-Icon — öffnet eine inline Search-Bar, die Header + Content ersetzt.
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onOpenSearch),
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
        if (state.categories.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            CategoryPills(
                categories = state.categories,
                active = state.activeCategory,
                onToggle = onSelectCategory
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
                    modifier = Modifier.weight(1f)
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
                            text = "${mealtime.from.format(timeFmt)} – ${mealtime.to.format(timeFmt)}",
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
    val weekDays = remember(anchor) { (0..4).map { anchor.plusDays(it.toLong()) } }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        weekDays.forEach { day ->
            WeekDayCell(
                day = day,
                isSelected = day == selected,
                isToday = day == today,
                modifier = Modifier.weight(1f),
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
            modifier = modifier
        ) {
            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = day.format(dayLabel),
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
                .clickable { onClick() }
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = day.format(dayLabel),
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
private fun CategoryPills(
    categories: List<String>,
    active: String?,
    onToggle: (String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item(key = "all") {
            CategoryPill(label = "Alle", selected = active == null, onClick = { onToggle(null) })
        }
        items(categories, key = { it }) { category ->
            CategoryPill(
                label = category,
                selected = active == category,
                onClick = { onToggle(category) }
            )
        }
    }
}

@Composable
private fun CategoryPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val background = if (selected) colors.primary else semantics.surfaceAlt
    val foreground = if (selected) colors.onPrimary else semantics.onSurfaceMuted
    Surface(color = background, shape = RoundedCornerShape(20.dp), onClick = onClick) {
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
            Triple(colors.primaryContainer, colors.primary, "AB ${status.time.format(timeFmt)} UHR")
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
    val hours = "${mealtime.from.format(timeFmt)} – ${mealtime.to.format(timeFmt)} Uhr"
    return "${state.selectedDate.format(fullDate)} · $hours"
}
