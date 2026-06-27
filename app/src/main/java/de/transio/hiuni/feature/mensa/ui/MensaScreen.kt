package de.transio.hiuni.feature.mensa.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.mensa.MensaViewModel
import de.transio.hiuni.feature.mensa.data.MealEntity
import de.transio.hiuni.feature.mensacard.ui.MensaCardTeaser
import de.transio.hiuni.ui.responsive.FullWidthContent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MensaScreen(
    onOpenMensaCard: () -> Unit = {},
    viewModel: MensaViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    // System-Back schließt die Suche bevorzugt vor dem Tab-Wechsel.
    BackHandler(enabled = state.isSearchOpen) { viewModel.closeSearch() }

    // Auf Tablet-Landscape rendert MealList ein 2-Spalten-Grid — dafür muss
    // der Screen aus dem 1100dp-Cap des AdaptiveContentBox raus, sonst stehen
    // die Cards mittig auf dem Tablet statt voll-breit.
    FullWidthContent {
    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isSearchOpen) {
                MensaSearchBar(
                    query = state.searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    onClose = viewModel::closeSearch
                )
                MensaSearchResults(
                    query = state.searchQuery,
                    results = state.searchResults,
                    onSelect = viewModel::selectSearchResult
                )
            } else {
                MensaHeader(
                    state = state,
                    onSelectMealtime = viewModel::selectMealtime,
                    onSelectCategory = viewModel::toggleCategory,
                    onSelectDate = viewModel::selectDate,
                    onOpenSearch = viewModel::openSearch
                )
                MensaCardTeaser(
                    onOpen = onOpenMensaCard,
                    modifier = Modifier.padding(
                        start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp
                    )
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
                        selectedDate = state.selectedDate,
                        onPin = viewModel::pinToCalendar
                    )
                }
            }
        }
    }
    } // end FullWidthContent
}

/* ──────────────────────────────────────────────────────────────────
 * Volltext-Suche
 * ────────────────────────────────────────────────────────────────── */

private val resultDayFmt = DateTimeFormatter.ofPattern("EEE d. MMM", Locale.GERMAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MensaSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(start = 10.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile - 4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Suche schließen",
                    tint = colors.onSurface
                )
            }
        }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = "Gericht, Beilage, Kategorie…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted
                )
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = semantics.surfaceAlt,
                unfocusedContainerColor = semantics.surfaceAlt,
                disabledContainerColor = semantics.surfaceAlt,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(HiUniRadii.pill),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable { onQueryChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(HiUniRadii.tile)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Eingabe löschen",
                                tint = semantics.onSurfaceMuted
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun MensaSearchResults(
    query: String,
    results: List<MealEntity>,
    onSelect: (MealEntity) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tokens = remember(query) {
        query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            query.isBlank() -> {
                SearchEmptyHint(
                    title = "Wonach hast du Hunger?",
                    subtitle = "Tippe ein, was du suchst — Gericht, Beilage, Kategorie."
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
                    contentPadding = PaddingValues(
                        start = 18.dp, end = 18.dp, top = 12.dp, bottom = 100.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = results,
                        key = { it.sourceId + "-" + it.locationId + "-" + it.category }
                    ) { meal ->
                        MensaSearchResultRow(
                            meal = meal,
                            tokens = tokens,
                            highlightColor = colors.primaryContainer,
                            onClick = { onSelect(meal) }
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
        horizontalAlignment = Alignment.CenterHorizontally,
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
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MensaSearchResultRow(
    meal: MealEntity,
    tokens: List<String>,
    highlightColor: Color,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val isEvening = meal.category.startsWith("Abend", ignoreCase = true)
    val categoryLabel = if (isEvening) "Abend" else "Mittag"
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatRelativeDayLabel(meal.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = semantics.onSurfaceMuted,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    color = semantics.surfaceAlt,
                    shape = RoundedCornerShape(HiUniRadii.pill)
                ) {
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = semantics.onSurfaceMuted,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (meal.priceLabel.isNotBlank()) {
                    Surface(
                        color = colors.primaryContainer,
                        shape = RoundedCornerShape(HiUniRadii.pill)
                    ) {
                        Text(
                            text = meal.priceLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primary,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = highlightTokens(meal.name, tokens, highlightColor, colors.onSurface),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
            if (!meal.description.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = highlightTokens(
                        meal.description,
                        tokens,
                        highlightColor,
                        semantics.onSurfaceMuted
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted,
                    maxLines = 2
                )
            }
        }
    }
}

private fun formatRelativeDayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (ChronoUnit.DAYS.between(today, date)) {
        0L -> "Heute"
        1L -> "Morgen"
        -1L -> "Gestern"
        else -> date.format(resultDayFmt)
    }
}

/**
 * Hebt alle Tokens (case-insensitiv) im Text mit `background = highlight` hervor.
 * Duplikat aus `CalendarScreen.kt` — bewusst kein shared util, da nur ~20 Zeilen.
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
