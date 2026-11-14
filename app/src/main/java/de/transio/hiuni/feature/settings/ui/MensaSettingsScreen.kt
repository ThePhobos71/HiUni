package de.transio.hiuni.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.design.components.HiUniTopBar
import de.transio.hiuni.feature.mensa.FavoriteMealRow
import de.transio.hiuni.feature.mensa.FavoriteMealsUiState
import de.transio.hiuni.feature.mensa.FavoriteMealsViewModel
import de.transio.hiuni.feature.mensa.data.PriceGroupPreference
import de.transio.hiuni.feature.mensa.ui.FavoriteKeywordField
import de.transio.hiuni.feature.settings.MensaPriceGroupUiState
import de.transio.hiuni.feature.settings.MensaPriceGroupViewModel
import de.transio.hiuni.feature.settings.SettingsViewModel

/**
 * Mensa-Standort, Preisgruppe und Lieblingsgerichte. Der Standort wirkt sich auf alle Mensa-Cards
 * in Home und dem Mensa-Tab aus, die Preisgruppe auf jede Preisanzeige (Mensa-Tab,
 * Gerichtsdetail, Suche, Widget).
 */
@Composable
fun MensaSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    priceGroupViewModel: MensaPriceGroupViewModel = hiltViewModel(),
    favoritesViewModel: FavoriteMealsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val priceGroupState by priceGroupViewModel.state.collectAsStateWithLifecycle()
    val favoritesState by favoritesViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            HiUniTopBar(
                title = "Mensa",
                onBack = onBack,
                subtitle = "Standort, Preisgruppe, Lieblingsgerichte"
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SectionCard(
                        icon = Icons.Outlined.LocalDining,
                        title = "Mensa-Standort",
                        subtitle = state.selectedLocation?.name ?: "Standort wählen"
                    ) {
                        state.locations.forEach { location ->
                            LocationRow(
                                location = location,
                                isSelected = location.id == state.selectedLocationId,
                                onClick = { viewModel.selectLocation(location.id) }
                            )
                        }
                    }
                }
                item {
                    PriceGroupCard(
                        state = priceGroupState,
                        onSelect = priceGroupViewModel::select
                    )
                }
                item {
                    FavoriteMealsCard(
                        state = favoritesState,
                        onAdd = favoritesViewModel::add,
                        onRemove = favoritesViewModel::remove,
                        onEdited = favoritesViewModel::clearRejected
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

/**
 * Preisgruppe: Automatik (aus Uni-Login abgeleitet) oder fester Override. Die
 * Automatik-Zeile nennt die erkannte Gruppe und woran sie festgemacht wurde.
 */
@Composable
private fun PriceGroupCard(
    state: MensaPriceGroupUiState,
    onSelect: (PriceGroupPreference) -> Unit
) {
    SectionCard(
        icon = Icons.Outlined.Euro,
        title = "Preisgruppe",
        subtitle = "Angezeigt: Preise für ${state.effective.label}"
    ) {
        PriceGroupPreference.entries.forEach { option ->
            RadioOptionRow(
                title = option.fixedGroup?.label ?: state.autoLabel,
                description = when (option) {
                    PriceGroupPreference.AUTO -> state.detection.source.reason
                    PriceGroupPreference.STUDENT -> "Für immatrikulierte Studierende"
                    PriceGroupPreference.EMPLOYEE -> "Für Beschäftigte der Hochschulen"
                    PriceGroupPreference.GUEST -> "Für alle anderen Gäste"
                },
                isSelected = option == state.preference,
                onClick = { onSelect(option) }
            )
        }
    }
}

/**
 * Lieblingsgericht-Alarm: Stichwörter merken und entfernen. Je Stichwort steht der
 * nächste Treffer im Speiseplan dabei, falls es einen gibt.
 */
@Composable
private fun FavoriteMealsCard(
    state: FavoriteMealsUiState,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEdited: () -> Unit
) {
    val semantics = HiUniColors.semantics
    var draft by rememberSaveable { mutableStateOf("") }
    // Angenommenes Stichwort: die Liste wächst, das Feld wird wieder leer.
    val count = state.rows.size
    var lastCount by remember { mutableStateOf(count) }
    if (count != lastCount) {
        if (count > lastCount) draft = ""
        lastCount = count
    }

    SectionCard(
        icon = Icons.Outlined.StarBorder,
        title = "Lieblingsgerichte",
        subtitle = "Mitteilung, sobald eines im Speiseplan steht"
    ) {
        if (state.rows.isEmpty()) {
            Text(
                text = "Noch keine Lieblingsgerichte. Tippe im Speiseplan auf ein Gericht " +
                    "und dann auf den Stern, oder trage hier ein Stichwort ein.",
                style = MaterialTheme.typography.bodySmall,
                color = semantics.onSurfaceMuted
            )
        }
        state.rows.forEach { row ->
            FavoriteMealRowItem(row = row, onRemove = { onRemove(row.keyword) })
        }
        Spacer(Modifier.height(10.dp))
        FavoriteKeywordField(
            value = draft,
            onValueChange = {
                draft = it
                onEdited()
            },
            actionLabel = "Hinzufügen",
            onSubmit = { onAdd(draft) },
            rejected = state.rejected
        )
    }
}

@Composable
private fun FavoriteMealRowItem(row: FavoriteMealRow, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = semantics.surfaceAlt,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 2.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.keyword,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = row.nextHit?.let { "Nächster Treffer: ${shortDay(it.date)}, ${it.name}" }
                        ?: "Aktuell nicht im Speiseplan",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "${row.keyword} entfernen",
                        onClick = onRemove
                    )
            ) {
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.titleMedium,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}

private val WEEKDAYS_SHORT = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

/** Kurzes Datum für die Trefferzeile, z.B. `Do 19.11.`. */
private fun shortDay(date: java.time.LocalDate): String =
    "%s %02d.%02d.".format(WEEKDAYS_SHORT[date.dayOfWeek.value - 1], date.dayOfMonth, date.monthValue)
