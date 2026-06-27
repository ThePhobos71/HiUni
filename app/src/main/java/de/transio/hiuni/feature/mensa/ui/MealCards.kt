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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import de.transio.hiuni.ui.responsive.LocalWindowSizeClass
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.common.isWeekend
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.mensa.data.Announcement
import de.transio.hiuni.feature.mensa.data.MealEntity
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun MealList(
    announcements: List<Announcement>,
    meals: List<MealEntity>,
    selectedDate: LocalDate,
    onPin: (MealEntity) -> Unit
) {
    val isEmpty = announcements.isEmpty() && meals.isEmpty()
    val isExpanded = LocalWindowSizeClass.current?.widthSizeClass == WindowWidthSizeClass.Expanded

    if (isExpanded) {
        // Tablet-Landscape: 2-Spalten-Grid. Announcements + Empty-State spannen
        // beide Spalten, damit der Hinweis-Banner nicht halb-breit wirkt.
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isEmpty) {
                item(
                    key = "empty-state",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    EmptyStateCard(selectedDate = selectedDate)
                }
                return@LazyVerticalGrid
            }
            if (announcements.isNotEmpty()) {
                items(
                    announcements,
                    key = { it.date.toString() + "-" + it.text.hashCode() },
                    span = { GridItemSpan(maxLineSpan) }
                ) { announcement ->
                    AnnouncementBanner(announcement = announcement)
                }
            }
            items(
                meals,
                key = { it.sourceId + "-" + it.locationId + "-" + it.category }
            ) { meal ->
                MealCard(meal = meal, onPin = { onPin(meal) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(80.dp)) }
        }
        return
    }

    // Auch im Empty-State LazyColumn rendern, sonst frisst PullToRefreshBox
    // das Pull-Gesture nicht — das passiert v.a. bei Abend-Plan ohne Gerichte.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isEmpty) {
            item(key = "empty-state") {
                EmptyStateCard(selectedDate = selectedDate)
            }
            return@LazyColumn
        }
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
private fun EmptyStateCard(selectedDate: LocalDate) {
    val semantics = HiUniColors.semantics
    val message = if (selectedDate.isWeekend()) {
        "Mensa hat am Wochenende geschlossen. Wähle einen Wochentag."
    } else {
        "Für diesen Tag liegen noch keine Daten vor. Pull-to-Refresh oder Aktualisieren versuchen."
    }
    Surface(
        color = semantics.surfaceAlt,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
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
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = semantics.onSurfaceMuted
            )
        }
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
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                    tagList.forEach { tag -> TagPill(label = tag) }
                }
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onPin),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = colors.primaryContainer,
                        shape = RoundedCornerShape(HiUniRadii.tile),
                        modifier = Modifier.size(36.dp)
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
}

@Composable
private fun TagPill(label: String) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val isAllergen = label.startsWith("*")
    val displayLabel = if (isAllergen) label.removePrefix("*") else label
    val (background, foreground) = when {
        isAllergen -> semantics.redSurface to semantics.red
        displayLabel.contains("vegan", ignoreCase = true) -> semantics.greenSurface to semantics.green
        displayLabel.contains("veget", ignoreCase = true) -> semantics.greenSurface to semantics.green
        displayLabel.contains("fisch", ignoreCase = true) -> colors.primaryContainer to colors.primary
        displayLabel.contains("schwein", ignoreCase = true) -> semantics.amberSurface to semantics.amber
        displayLabel.contains("rind", ignoreCase = true) -> semantics.redSurface to semantics.red
        else -> semantics.surfaceAlt to semantics.onSurfaceMuted
    }
    Surface(color = background, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = if (isAllergen) "⚠ $displayLabel" else displayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
