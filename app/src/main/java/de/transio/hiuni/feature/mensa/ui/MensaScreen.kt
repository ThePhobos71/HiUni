package de.transio.hiuni.feature.mensa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.mensa.Mealtime
import de.transio.hiuni.feature.mensa.MensaUiState
import de.transio.hiuni.feature.mensa.MensaViewModel
import de.transio.hiuni.feature.mensa.data.Announcement
import de.transio.hiuni.feature.mensa.data.MealEntity
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayLabel = DateTimeFormatter.ofPattern("EEE", Locale.GERMAN)
private val dayNumber = DateTimeFormatter.ofPattern("d", Locale.GERMAN)
private val fullDate = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

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
                onRefresh = viewModel::refresh,
                onSelectMealtime = viewModel::selectMealtime,
                onSelectCategory = viewModel::toggleCategory,
                onSelectDate = viewModel::selectDate
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
                    onPin = viewModel::pinToCalendar
                )
            }
        }
    }
}

@Composable
private fun MensaHeader(
    state: MensaUiState,
    onRefresh: () -> Unit,
    onSelectMealtime: (Mealtime) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectDate: (LocalDate) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(start = 22.dp, end = 12.dp, top = 22.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Mensa",
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.onSurface
                    )
                }
                Text(
                    text = subtitle(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OpenBadge(status = computeOpenStatus(state.selectedDate, state.selectedMealtime))
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Aktualisieren",
                        tint = colors.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        MealtimeToggle(
            active = state.selectedMealtime,
            onSelect = onSelectMealtime
        )
        Spacer(Modifier.height(14.dp))
        WeekStrip(
            selected = state.selectedDate,
            onSelect = onSelectDate
        )
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
private fun MealtimeToggle(
    active: Mealtime,
    onSelect: (Mealtime) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = semantics.surfaceAlt,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
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
                            color = if (isActive) semantics.onSurfaceMuted else semantics.onSurfaceMuted.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val anchor = if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) {
        today.with(DayOfWeek.MONDAY).plusWeeks(1)
    } else {
        today.with(DayOfWeek.MONDAY)
    }
    val weekDays = remember(anchor) { (0..4).map { anchor.plusDays(it.toLong()) } }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                modifier = Modifier.padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = day.format(dayLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = day.format(dayNumber),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.onPrimary
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(HiUniRadii.tile))
                .clickable { onClick() }
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = day.format(dayLabel),
                style = MaterialTheme.typography.labelMedium,
                color = semantics.onSurfaceMuted
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = day.format(dayNumber),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onSurface
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
    Surface(
        color = background,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp)
        )
    }
}

private sealed interface OpenStatus {
    data object Open : OpenStatus
    data class ClosingSoon(val minutes: Long) : OpenStatus
    data class OpensLater(val time: LocalTime) : OpenStatus
    data object ClosedToday : OpenStatus
    data object Preview : OpenStatus
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
            Triple(
                colors.primaryContainer,
                colors.primary,
                "AB ${status.time.format(timeFmt)} UHR"
            )
        OpenStatus.ClosedToday ->
            Triple(semantics.surfaceAlt, semantics.onSurfaceMuted, "GESCHLOSSEN")
        OpenStatus.Preview ->
            Triple(semantics.surfaceAlt, semantics.onSurfaceMuted, "VORSCHAU")
    }
    Surface(
        color = background,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

private fun computeOpenStatus(date: LocalDate, mealtime: Mealtime): OpenStatus {
    val today = LocalDate.now()
    if (date != today) return OpenStatus.Preview
    if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
        return OpenStatus.ClosedToday
    }
    val now = LocalDateTime.now().toLocalTime()
    val from = mealtime.from
    val to = mealtime.to
    return when {
        now.isBefore(from) -> OpenStatus.OpensLater(from)
        now.isBefore(to) -> {
            val minutesLeft = Duration.between(now, to).toMinutes()
            if (minutesLeft <= 30) OpenStatus.ClosingSoon(minutesLeft) else OpenStatus.Open
        }
        else -> OpenStatus.ClosedToday
    }
}

private fun subtitle(state: MensaUiState): String {
    val mealtime = state.selectedMealtime
    val hours = "${mealtime.from.format(timeFmt)} – ${mealtime.to.format(timeFmt)} Uhr"
    return "${state.selectedDate.format(fullDate)} · $hours"
}

@Composable
private fun MealList(
    announcements: List<Announcement>,
    meals: List<MealEntity>,
    onPin: (MealEntity) -> Unit
) {
    val semantics = HiUniColors.semantics
    if (announcements.isEmpty() && meals.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(
                color = semantics.surfaceAlt,
                shape = RoundedCornerShape(HiUniRadii.card),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalDining,
                        contentDescription = null,
                        tint = semantics.onSurfaceMuted
                    )
                    Text(
                        text = "Für diesen Tag liegen noch keine Daten vor. Pull-to-Refresh oder Aktualisieren versuchen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = semantics.onSurfaceMuted
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (announcements.isNotEmpty()) {
            items(announcements, key = { it.date.toString() + "-" + it.text.hashCode() }) { announcement ->
                AnnouncementBanner(announcement = announcement)
            }
        }
        items(meals, key = { it.sourceId + "-" + it.locationId + "-" + it.category }) { meal ->
            MealCard(meal = meal, onPin = { onPin(meal) })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun AnnouncementBanner(announcement: Announcement) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = semantics.amberSurface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile))
                    .background(semantics.amber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = semantics.amber,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "HINWEIS",
                    style = MaterialTheme.typography.labelSmall,
                    color = semantics.amber,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = announcement.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )
            }
        }
    }
}

@Suppress("unused")
private val keepImportsLayout = Unit

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealCard(meal: MealEntity, onPin: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val tagList = meal.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(HiUniRadii.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = meal.category.uppercase(Locale.GERMAN),
                        style = MaterialTheme.typography.labelSmall,
                        color = semantics.onSurfaceMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = meal.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (meal.priceLabel.isNotBlank()) {
                    Text(
                        text = meal.priceLabel,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.primary
                    )
                }
            }
            if (!meal.description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = meal.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    tagList.forEach { tag ->
                        TagPill(label = tag)
                    }
                }
                Surface(
                    color = colors.primaryContainer,
                    shape = RoundedCornerShape(HiUniRadii.tile),
                    modifier = Modifier.size(36.dp),
                    onClick = onPin
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "In Kalender packen",
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagPill(label: String) {
    val semantics = HiUniColors.semantics
    val isAllergen = label.startsWith("*")
    val displayLabel = if (isAllergen) label.removePrefix("*") else label
    val (background, foreground) = when {
        isAllergen -> semantics.redSurface to semantics.red
        displayLabel.contains("vegan", ignoreCase = true) -> semantics.greenSurface to semantics.green
        displayLabel.contains("veget", ignoreCase = true) -> semantics.greenSurface to semantics.green
        displayLabel.contains("fisch", ignoreCase = true) -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        displayLabel.contains("schwein", ignoreCase = true) -> semantics.amberSurface to semantics.amber
        displayLabel.contains("rind", ignoreCase = true) -> semantics.redSurface to semantics.red
        else -> semantics.surfaceAlt to semantics.onSurfaceMuted
    }
    Surface(
        color = background,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = if (isAllergen) "⚠ $displayLabel" else displayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
