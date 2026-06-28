package de.transio.hiuni.feature.mensa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.mensa.data.MealEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.Locale

/**
 * Bottom-Sheet mit allen Detail-Infos zu einem Mensa-Gericht. Holt die meisten
 * Felder direkt aus [MealEntity] — Nährwerte werden aus dem persistierten JSON
 * dekodiert, das vom [de.transio.hiuni.feature.mensa.data.MensaDtos.toEntity]
 * gefüllt wird. Bei alten Bestands-Rows (vor v29) sind die Detail-Felder
 * leer/null; der nächste Refresh füllt sie nach.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MealDetailSheet(
    meal: MealEntity,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val tagList = remember(meal.tags) {
        meal.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
    val additiveList = remember(meal.additives) {
        meal.additives.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
    val specialList = remember(meal.specialTags) {
        meal.specialTags.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
    val nutrition = remember(meal.nutritionalValuesJson) {
        meal.nutritionalValuesJson?.let { raw ->
            // JsonObject parsen + Werte als String herausziehen — matched
            // wie wir's in MensaDtos.toEntity encoden (buildJsonObject).
            runCatching {
                Json.parseToJsonElement(raw).jsonObject.mapValues { (_, v) ->
                    (v as? JsonPrimitive)?.content.orEmpty()
                }
            }.getOrNull()
        }.orEmpty()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 12.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = meal.category.uppercase(Locale.GERMAN),
                style = MaterialTheme.typography.labelSmall,
                color = semantics.onSurfaceMuted,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = meal.name,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold
            )
            meal.nameEn?.takeIf { it.isNotBlank() && !it.equals(meal.name, ignoreCase = true) }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted,
                    fontStyle = FontStyle.Italic
                )
            }

            if (anyPriceShown(meal)) {
                Spacer(Modifier.height(16.dp))
                PriceTable(meal = meal)
            }

            if (tagList.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel(text = "Tags & Allergene")
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    tagList.forEach { DetailTagPill(label = it) }
                }
            }

            if (additiveList.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel(text = "Zusatzstoffe")
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    additiveList.forEach { AdditivePill(label = it) }
                }
            }

            if (specialList.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel(text = "Besonderheiten")
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    specialList.forEach { SpecialPill(label = it) }
                }
            }

            if (nutrition.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionLabel(text = "Nährwerte (pro 100 g)")
                Spacer(Modifier.height(8.dp))
                NutritionTable(values = nutrition)
            }

            if (!anyPriceShown(meal) && tagList.isEmpty() && additiveList.isEmpty() &&
                specialList.isEmpty() && nutrition.isEmpty()
            ) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Mehr Details liefert die STW-API zu diesem Gericht aktuell nicht.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(Locale.GERMAN),
        style = MaterialTheme.typography.labelMedium,
        color = HiUniColors.semantics.onSurfaceMuted,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun PriceTable(meal: MealEntity) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = semantics.surfaceAlt,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PriceColumn(label = "Studi", cents = meal.priceStudentCents, highlight = true)
            PriceColumn(label = "Mitarbeiter", cents = meal.priceEmployeeCents, highlight = false)
            PriceColumn(label = "Gast", cents = meal.priceGuestCents, highlight = false)
        }
    }
}

@Composable
private fun PriceColumn(label: String, cents: Int?, highlight: Boolean) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(Locale.GERMAN),
            style = MaterialTheme.typography.labelSmall,
            color = semantics.onSurfaceMuted,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = cents?.let { "%.2f €".format(it / 100.0) } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) colors.primary else colors.onSurface,
            fontWeight = if (highlight) FontWeight.ExtraBold else FontWeight.SemiBold
        )
    }
}

@Composable
private fun NutritionTable(values: Map<String, String>) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = semantics.surfaceAlt,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // Bevorzugte Reihenfolge — Kalorien zuerst, dann Makros, dann Rest.
            val order = listOf(
                "caloric_value", "fat", "saturated_fatty_acids",
                "carbohydrates", "sugar", "roughage", "protein", "salt"
            )
            val sorted = values.entries.sortedBy { entry ->
                order.indexOf(entry.key).let { if (it < 0) order.size else it }
            }
            sorted.forEachIndexed { index, (key, value) ->
                if (index > 0) Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = nutritionLabel(key),
                        style = MaterialTheme.typography.bodyMedium,
                        color = semantics.onSurfaceMuted
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun nutritionLabel(apiKey: String): String = when (apiKey) {
    "caloric_value" -> "Brennwert"
    "fat" -> "Fett"
    "saturated_fatty_acids" -> "davon gesättigte Fettsäuren"
    "carbohydrates" -> "Kohlenhydrate"
    "sugar" -> "davon Zucker"
    "roughage" -> "Ballaststoffe"
    "protein" -> "Eiweiß"
    "salt" -> "Salz"
    else -> apiKey.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.GERMAN) }
}

/**
 * Lokale Kopie von [TagPill] aus MealCards (das ist `private`). Logik identisch:
 * `*`-Prefix = Allergen (rot), `vegan`/`vegetarisch` grün, `fisch` primary,
 * `schwein` amber, `rind` rot, Rest neutral.
 */
@Composable
private fun DetailTagPill(label: String) {
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

@Composable
private fun AdditivePill(label: String) {
    val semantics = HiUniColors.semantics
    Surface(color = semantics.amberSurface, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = semantics.amber,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun SpecialPill(label: String) {
    val semantics = HiUniColors.semantics
    Surface(color = semantics.redSurface, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = "ℹ $label",
            style = MaterialTheme.typography.labelSmall,
            color = semantics.red,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun anyPriceShown(meal: MealEntity): Boolean =
    meal.priceStudentCents != null || meal.priceEmployeeCents != null || meal.priceGuestCents != null
