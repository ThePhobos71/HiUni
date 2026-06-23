package de.transio.hiuni.feature.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.common.DateTimeUtils
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JtTextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val Zone: ZoneId = ZoneId.systemDefault()

/* ──────────────────────────────────────────────────────────────────
 * TAG (Day) — Mo–Fr Day-Picker + Stundenraster 8–18 mit Kursblöcken
 * ────────────────────────────────────────────────────────────────── */

@Composable
fun CalendarDayView(
    selectedDate: LocalDate,
    events: List<CustomEventEntity>,
    onSelectDay: (LocalDate) -> Unit,
    onLongPressDay: (LocalDate) -> Unit,
    onClickEvent: (CustomEventEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val weekStart = selectedDate.with(DayOfWeek.MONDAY)
    val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) } // Mo–So
    val dayEvents = events
        .filter { it.startTime.atZone(Zone).toLocalDate() == selectedDate }
        .sortedBy { it.startTime }

    Column(modifier = modifier.fillMaxSize()) {
        // Day-Picker Mo–Fr — aktiver Tag = primary background, heutiger Tag = primary text + dot.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 22.dp, vertical = 4.dp)
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            weekDays.forEach { day ->
                DayPickerCell(
                    day = day,
                    selected = day == selectedDate,
                    today = day == LocalDate.now(),
                    onClick = { onSelectDay(day) },
                    onLongClick = { onLongPressDay(day) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (dayEvents.isEmpty()) {
            EmptyDay(message = "Keine Veranstaltungen", subtitle = "Freier Tag!")
        } else {
            HourGrid(
                events = dayEvents,
                onClickEvent = onClickEvent
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayPickerCell(
    day: LocalDate,
    selected: Boolean,
    today: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(HiUniRadii.tile))
            .background(if (selected) colors.primary else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = day.dayOfWeek.getDisplayName(JtTextStyle.SHORT, Locale.GERMAN).take(2),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.onPrimary.copy(alpha = 0.72f) else semantics.onSurfaceMuted,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = when {
                selected -> colors.onPrimary
                today -> colors.primary
                else -> colors.onSurface
            },
            fontWeight = FontWeight.ExtraBold
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (today && !selected) colors.primary else Color.Transparent)
        )
    }
}

@Composable
private fun HourGrid(
    events: List<CustomEventEntity>,
    onClickEvent: (CustomEventEntity) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val (startH, endH) = computeHourRange(events)
    val hourPx = 60.dp
    val hours = (endH - startH).coerceAtLeast(1)
    val gridHeight = hourPx * hours
    val labelHours = (startH..endH step 2).toList()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp, bottom = 28.dp)
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Zeit-Gutter.
        Box(modifier = Modifier.width(36.dp).height(gridHeight)) {
            labelHours.forEach { h ->
                Text(
                    text = "${h}:00",
                    style = MaterialTheme.typography.labelSmall,
                    color = semantics.onSurfaceMuted,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .offset(y = hourPx * (h - startH) - 7.dp)
                        .fillMaxWidth()
                )
            }
        }
        // Eigentliches Grid mit Linien + Blöcken.
        BoxWithConstraints(modifier = Modifier.weight(1f).height(gridHeight)) {
            // Horizontale Stundenlinien.
            labelHours.forEach { h ->
                Box(
                    modifier = Modifier
                        .offset(y = hourPx * (h - startH))
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.outline)
                )
            }
            // Kursblöcke absolut positioniert.
            events.forEach { event ->
                val zoned = event.startTime.atZone(Zone)
                val startMin = zoned.hour * 60 + zoned.minute
                val endZoned = event.endTime.atZone(Zone)
                val endMin = endZoned.hour * 60 + endZoned.minute
                val topFraction = (startMin - startH * 60) / 60f
                val durationFraction = (endMin - startMin) / 60f
                val topOffset = hourPx * topFraction + 4.dp
                val blockHeight = hourPx * durationFraction - 8.dp
                if (blockHeight <= 0.dp) return@forEach
                CourseBlock(
                    event = event,
                    color = rememberCourseColor(event),
                    modifier = Modifier
                        .offset(y = topOffset)
                        .fillMaxWidth()
                        .height(blockHeight)
                        .clickable { onClickEvent(event) }
                )
            }
        }
    }
}

@Composable
private fun CourseBlock(
    event: CustomEventEntity,
    color: CourseColor,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(HiUniRadii.card - 4.dp))
            .background(color.bg)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxSize()
                .background(color.dot)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                color = color.fg,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = buildString {
                    append(DateTimeUtils.formatTime(event.startTime))
                    append(" – ")
                    append(DateTimeUtils.formatTime(event.endTime))
                    event.location?.let { append(" · ").append(it) }
                },
                style = MaterialTheme.typography.labelSmall,
                color = color.fg.copy(alpha = 0.75f),
                maxLines = 1
            )
            event.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = color.fg.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
 * WOCHE (Week) — 5-Spalten Mini-Grid Mo–Fr
 * ────────────────────────────────────────────────────────────────── */

@Composable
fun CalendarWeekView(
    selectedDate: LocalDate,
    events: List<CustomEventEntity>,
    onSelectDay: (LocalDate) -> Unit,
    onLongPressDay: (LocalDate) -> Unit,
    onClickEvent: (CustomEventEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val weekStart = selectedDate.with(DayOfWeek.MONDAY)
    val eventsByDay = events.groupBy { it.startTime.atZone(Zone).toLocalDate() }
    // Sa/So nur einblenden, wenn dort tatsächlich Termine liegen — sonst wird
    // das Mini-Grid Mo–So zu eng. Default Mo–Fr.
    val saturday = weekStart.plusDays(5)
    val sunday = weekStart.plusDays(6)
    val showSaturday = !eventsByDay[saturday].isNullOrEmpty()
    val showSunday = !eventsByDay[sunday].isNullOrEmpty()
    val lastIndex = when {
        showSunday -> 6
        showSaturday -> 5
        else -> 4
    }
    val weekDays = (0..lastIndex).map { weekStart.plusDays(it.toLong()) }
    val (startH, endH) = computeHourRange(events)
    val hourPx = 40.dp
    val hours = (endH - startH).coerceAtLeast(1)
    val gridHeight = hourPx * hours
    val labelHours = (startH..endH step 2).toList()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Zeit-Gutter.
            Box(modifier = Modifier.width(24.dp)) {
                Spacer(modifier = Modifier.height(34.dp))
                Box(modifier = Modifier.height(gridHeight)) {
                    labelHours.forEach { h ->
                        Text(
                            text = "$h",
                            style = MaterialTheme.typography.labelSmall,
                            color = semantics.onSurfaceMuted,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .offset(y = hourPx * (h - startH) - 5.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
            weekDays.forEach { day ->
                WeekDayColumn(
                    day = day,
                    today = day == LocalDate.now(),
                    selected = day == selectedDate,
                    events = eventsByDay[day].orEmpty(),
                    startH = startH,
                    endH = endH,
                    hourPx = hourPx,
                    gridHeight = gridHeight,
                    onClickHeader = { onSelectDay(day) },
                    onLongClickHeader = { onLongPressDay(day) },
                    onClickEvent = onClickEvent,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekDayColumn(
    day: LocalDate,
    today: Boolean,
    selected: Boolean,
    events: List<CustomEventEntity>,
    startH: Int,
    endH: Int,
    hourPx: androidx.compose.ui.unit.Dp,
    gridHeight: androidx.compose.ui.unit.Dp,
    onClickHeader: () -> Unit,
    onLongClickHeader: () -> Unit,
    onClickEvent: (CustomEventEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Tages-Header (Mo / 18 in einer Kreis-Pille wenn heute).
        Column(
            modifier = Modifier
                .padding(bottom = 6.dp)
                .clip(RoundedCornerShape(HiUniRadii.tile))
                .combinedClickable(onClick = onClickHeader, onLongClick = onLongClickHeader)
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = day.dayOfWeek.getDisplayName(JtTextStyle.SHORT, Locale.GERMAN).take(2),
                style = MaterialTheme.typography.labelSmall,
                color = if (today) colors.primary else semantics.onSurfaceMuted,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (today) colors.primary else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        today -> colors.onPrimary
                        selected -> colors.primary
                        else -> colors.onSurface
                    },
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        // Mini-Grid.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .clip(RoundedCornerShape(HiUniRadii.tile))
                .background(colors.surface)
        ) {
            // Stundenlinien.
            (startH..endH step 2).forEach { h ->
                Box(
                    modifier = Modifier
                        .offset(y = hourPx * (h - startH))
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.outline.copy(alpha = 0.6f))
                )
            }
            events.forEach { event ->
                val zoned = event.startTime.atZone(Zone)
                val startMin = zoned.hour * 60 + zoned.minute
                val endZoned = event.endTime.atZone(Zone)
                val endMin = endZoned.hour * 60 + endZoned.minute
                val topFraction = (startMin - startH * 60) / 60f
                val durationFraction = (endMin - startMin) / 60f
                val topOffset = hourPx * topFraction + 2.dp
                val blockHeight = hourPx * durationFraction - 4.dp
                if (blockHeight <= 0.dp) return@forEach
                MiniCourseBlock(
                    event = event,
                    color = rememberCourseColor(event),
                    modifier = Modifier
                        .offset(y = topOffset, x = 2.dp)
                        .fillMaxWidth()
                        .padding(end = 4.dp)
                        .height(blockHeight)
                        .clickable { onClickEvent(event) }
                )
            }
        }
    }
}

@Composable
private fun MiniCourseBlock(
    event: CustomEventEntity,
    color: CourseColor,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.bg)
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxSize().background(color.dot))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp, vertical = 4.dp)
        ) {
            Text(
                text = event.title.substringBefore(' ').take(10),
                style = MaterialTheme.typography.labelSmall,
                color = color.fg,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            event.location?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = color.fg.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
 * MONAT (Month) — 7-Spalten Grid mit Punkten + Tages-Details darunter
 * ────────────────────────────────────────────────────────────────── */

@Composable
fun CalendarMonthView(
    selectedDate: LocalDate,
    events: List<CustomEventEntity>,
    onSelectDay: (LocalDate) -> Unit,
    onLongPressDay: (LocalDate) -> Unit,
    onClickEvent: (CustomEventEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val month = YearMonth.from(selectedDate)
    val firstOfMonth = month.atDay(1)
    val gridStart = firstOfMonth.with(DayOfWeek.MONDAY)
    // 6 Wochen = 42 Tage. Reicht für jeden Monat.
    val gridDays = (0L until 42L).map { gridStart.plusDays(it) }
    val eventsByDay = events.groupBy { it.startTime.atZone(Zone).toLocalDate() }
    val today = LocalDate.now()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        // Wochentag-Header.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = semantics.onSurfaceMuted,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // 6 × 7 Grid.
        gridDays.chunked(7).forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                week.forEach { date ->
                    MonthCell(
                        date = date,
                        inMonth = date.month == selectedDate.month,
                        today = date == today,
                        selected = date == selectedDate,
                        dotColors = eventsByDay[date].orEmpty().take(3).map { rememberCourseColor(it).dot },
                        onClick = { onSelectDay(date) },
                        onLongClick = { onLongPressDay(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // Tages-Details.
        val dayEvents = eventsByDay[selectedDate].orEmpty().sortedBy { it.startTime }
        val dayLabel = selectedDate.format(
            DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
        ).uppercase()
        Text(
            text = dayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = semantics.onSurfaceMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (dayEvents.isEmpty()) {
            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(HiUniRadii.card),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Keine Veranstaltungen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(18.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dayEvents.forEach { event ->
                    MonthEventRow(
                        event = event,
                        color = rememberCourseColor(event),
                        onClick = { onClickEvent(event) }
                    )
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthCell(
    date: LocalDate,
    inMonth: Boolean,
    today: Boolean,
    selected: Boolean,
    dotColors: List<Color>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(HiUniRadii.tile - 4.dp))
            .background(
                when {
                    selected -> colors.primary
                    today -> colors.primaryContainer
                    else -> Color.Transparent
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (today || selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = when {
                !inMonth -> semantics.onSurfaceMuted.copy(alpha = 0.4f)
                selected -> colors.onPrimary
                today -> colors.primary
                isWeekend -> semantics.onSurfaceMuted
                else -> colors.onSurface
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dotColors.forEach { dotColor ->
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) colors.onPrimary else dotColor)
                )
            }
        }
    }
}

@Composable
private fun MonthEventRow(
    event: CustomEventEntity,
    color: CourseColor,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.dot)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = buildString {
                        append(DateTimeUtils.formatTime(event.startTime))
                        append(" – ")
                        append(DateTimeUtils.formatTime(event.endTime))
                        event.location?.let { append(" · ").append(it) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = semantics.onSurfaceMuted,
                    maxLines = 1
                )
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
 * Helpers
 * ────────────────────────────────────────────────────────────────── */

private fun computeHourRange(events: List<CustomEventEntity>): Pair<Int, Int> {
    if (events.isEmpty()) return 8 to 20
    var minH = 8
    var maxH = 20
    events.forEach { e ->
        val s = LocalDateTime.ofInstant(e.startTime, Zone)
        val end = LocalDateTime.ofInstant(e.endTime, Zone)
        minH = min(minH, s.hour)
        // ceil end up to next full hour.
        val endHour = end.hour + (if (end.minute > 0) 1 else 0)
        maxH = max(maxH, endHour)
    }
    // Auf gerade Stunden snappen, damit die 2h-Labels sauber sind.
    minH = (minH / 2) * 2
    maxH = ((maxH + 1) / 2) * 2
    return minH to maxH
}

@Composable
private fun EmptyDay(message: String, subtitle: String) {
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.EventBusy,
            contentDescription = null,
            tint = semantics.onSurfaceMuted.copy(alpha = 0.3f),
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = semantics.onSurfaceMuted,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = semantics.onSurfaceMuted.copy(alpha = 0.7f)
        )
    }
}

@Suppress("unused")
@Composable
fun CalendarListView(
    events: List<CustomEventEntity>,
    onClickEvent: (CustomEventEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    // Behalten als optionaler Einstiegspunkt — derzeit nicht in der Header-Tab-Leiste
    // verlinkt, könnte aber von Home/Embed wiederverwendet werden.
    if (events.isEmpty()) {
        EmptyDay(message = "Keine Events", subtitle = "Lege einen Termin an.")
        return
    }
    val grouped = events.groupBy { it.startTime.atZone(Zone).toLocalDate() }.toSortedMap()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 100.dp))
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        grouped.forEach { (_, dailyEvents) ->
            Text(
                text = DateTimeUtils.formatRelativeDay(dailyEvents.first().startTime),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            dailyEvents.forEach { event ->
                MonthEventRow(
                    event = event,
                    color = rememberCourseColor(event),
                    onClick = { onClickEvent(event) }
                )
            }
        }
    }
}
