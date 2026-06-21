package de.transio.hiuni.feature.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.common.DateTimeUtils
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JtTextStyle
import java.util.Locale

private val Zone: ZoneId = ZoneId.systemDefault()

@Composable
fun CalendarListView(
    events: List<CustomEventEntity>,
    onClickEvent: (CustomEventEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (events.isEmpty()) {
        EmptyState(message = "Keine Events in den nächsten 6 Monaten.")
        return
    }
    val grouped = events.groupBy { it.startTime.atZone(Zone).toLocalDate() }
        .toSortedMap()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        grouped.forEach { (date, dailyEvents) ->
            item(key = "header-$date") {
                Text(
                    text = DateTimeUtils.formatRelativeDay(dailyEvents.first().startTime),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(dailyEvents, key = { it.id }) { event ->
                EventCard(event = event, onClick = { onClickEvent(event) })
            }
        }
    }
}

@Composable
fun CalendarDayView(
    date: LocalDate,
    events: List<CustomEventEntity>,
    onClickEvent: (CustomEventEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val dayEvents = events.filter { it.startTime.atZone(Zone).toLocalDate() == date }
        .sortedBy { it.startTime }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = date.format(DateTimeFormatter.ofPattern("EEEE · dd. MMMM yyyy", Locale.GERMAN)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (dayEvents.isEmpty()) {
            EmptyState(message = "An diesem Tag sind keine Events geplant.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(dayEvents, key = { it.id }) { event ->
                    EventCard(event = event, onClick = { onClickEvent(event) })
                }
            }
        }
    }
}

@Composable
fun CalendarWeekView(
    selectedDate: LocalDate,
    events: List<CustomEventEntity>,
    onSelectDay: (LocalDate) -> Unit,
    onClickEvent: (CustomEventEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val weekStart = selectedDate.with(DayOfWeek.MONDAY)
    val weekDays = (0..4).map { weekStart.plusDays(it.toLong()) } // Mo–Fr
    val eventsByDay = events.groupBy { it.startTime.atZone(Zone).toLocalDate() }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            weekDays.forEach { day ->
                val isToday = day == LocalDate.now()
                val isSelected = day == selectedDate
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(HiUniRadii.tile))
                        .background(if (isSelected) colors.primaryContainer else colors.surfaceVariant)
                        .clickable { onSelectDay(day) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = day.dayOfWeek.getDisplayName(JtTextStyle.SHORT, Locale.GERMAN),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) colors.primary else semantics.onSurfaceMuted
                    )
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = when {
                            isSelected -> colors.primary
                            isToday -> colors.primary
                            else -> colors.onSurface
                        },
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            weekDays.forEach { day ->
                val dayEvents = eventsByDay[day].orEmpty().sortedBy { it.startTime }
                if (dayEvents.isNotEmpty()) {
                    item(key = "wk-header-$day") {
                        Text(
                            text = day.format(DateTimeFormatter.ofPattern("EEE · d. MMM", Locale.GERMAN)),
                            style = MaterialTheme.typography.titleSmall,
                            color = semantics.onSurfaceMuted,
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                        )
                    }
                    items(dayEvents, key = { it.id }) { event ->
                        EventCard(event = event, onClick = { onClickEvent(event) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: CustomEventEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val accent = when (event.sourceKind) {
        CustomEventEntity.SOURCE_MENSA_PIN -> semantics.amber
        CustomEventEntity.SOURCE_MOVIE_PIN -> semantics.purple
        else -> colors.primary
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(HiUniRadii.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = DateTimeUtils.formatTime(event.startTime),
                        style = MaterialTheme.typography.titleMedium,
                        color = accent
                    )
                }
                val subtitle = buildString {
                    event.location?.let { append(it) }
                    if (event.location != null && event.description != null) append(" · ")
                    event.description?.let { append(it) }
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    val semantics = HiUniColors.semantics
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        color = semantics.surfaceAlt,
        shape = RoundedCornerShape(HiUniRadii.card)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.EventBusy,
                contentDescription = null,
                tint = semantics.onSurfaceMuted
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = semantics.onSurfaceMuted
            )
        }
    }
}
