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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Bottom-Sheet mit allen Detail-Infos zu einem Mensa-Gericht. Layout:
 * 1. Headline-Block: Kategorie · Titel · EN-Übersetzung
 * 2. Hero-Row: Studi-Preis groß (links) + Kalorien-Pill (rechts), nebeneinander
 * 3. Sekundär-Preise (Mitarbeiter/Gast) als kleine Inline-Zeile
 * 4. Eigenschaften (Diet-Tags) — grüne/primary Pills
 * 5. Allergene (mit Warn-Icon) — eigene Sektion, rote Warn-Header
 * 6. Zusatzstoffe — amber Pills
 * 7. Besonderheiten (special_tags) — Pills mit Info-Icon
 * 8. Volle Nährwert-Tabelle pro 100g
 *
 * Zwischen den Sektionen dezente HorizontalDivider statt fetter Spacer.
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

    val rawTags = remember(meal.tags) {
        meal.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
    val allergenList = remember(rawTags) { rawTags.filter { it.startsWith("*") } }
    val dietList = remember(rawTags) { rawTags.filterNot { it.startsWith("*") } }
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
    val kcal = remember(nutrition) { extractKcal(nutrition["caloric_value"]) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 4.dp, bottom = 28.dp)
        ) {
            // ── Headline ───────────────────────────────────────────────
            Text(
                text = meal.category.uppercase(Locale.GERMAN),
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = meal.name,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                lineHeight = MaterialTheme.typography.headlineSmall.fontSize * 1.15
            )
            meal.nameEn?.takeIf { it.isNotBlank() && !it.equals(meal.name, ignoreCase = true) }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted,
                    fontStyle = FontStyle.Italic
                )
            }

            // ── Hero: Preis + Kalorien ─────────────────────────────────
            if (meal.priceStudentCents != null || kcal != null) {
                Spacer(Modifier.height(18.dp))
                HeroRow(meal = meal, kcal = kcal)
            }

            // Sekundär-Preise: Mitarbeiter + Gast als kleiner Inline-Hinweis
            if (meal.priceEmployeeCents != null || meal.priceGuestCents != null) {
                Spacer(Modifier.height(8.dp))
                SecondaryPriceLine(meal = meal)
            }

            // ── Eigenschaften (Diet) ───────────────────────────────────
            if (dietList.isNotEmpty()) {
                SectionDivider()
                SectionHeader(text = "Eigenschaften")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dietList.forEach { DietTagPill(label = it) }
                }
            }

            // ── Allergene (eigener Block) ──────────────────────────────
            if (allergenList.isNotEmpty()) {
                SectionDivider()
                SectionHeader(text = "Allergene", accent = semantics.red)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allergenList.forEach { AllergenPill(label = it.removePrefix("*")) }
                }
            }

            // ── Zusatzstoffe ────────────────────────────────────────────
            if (additiveList.isNotEmpty()) {
                SectionDivider()
                SectionHeader(text = "Zusatzstoffe", accent = semantics.amber)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    additiveList.forEach { AdditivePill(label = it) }
                }
            }

            // ── Besonderheiten (special_tags) ──────────────────────────
            if (specialList.isNotEmpty()) {
                SectionDivider()
                SectionHeader(text = "Besonderheiten")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    specialList.forEach { SpecialPill(label = it) }
                }
            }

            // ── Nährwerte ──────────────────────────────────────────────
            if (nutrition.isNotEmpty()) {
                SectionDivider()
                SectionHeader(text = "Nährwerte · pro 100 g")
                NutritionTable(values = nutrition)
            }

            // Empty-Fallback
            if (meal.priceStudentCents == null && kcal == null && rawTags.isEmpty() &&
                additiveList.isEmpty() && specialList.isEmpty() && nutrition.isEmpty()
            ) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Die STW-API liefert zu diesem Gericht aktuell keine weiteren Details.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}

// ─── Sections ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    text: String,
    accent: androidx.compose.ui.graphics.Color = HiUniColors.semantics.onSurfaceMuted
) {
    Text(
        text = text.uppercase(Locale.GERMAN),
        style = MaterialTheme.typography.labelMedium,
        color = accent,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp()
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SectionDivider() {
    val colors = MaterialTheme.colorScheme
    Spacer(Modifier.height(20.dp))
    HorizontalDivider(color = colors.outline.copy(alpha = 0.18f))
    Spacer(Modifier.height(16.dp))
}

// ─── Hero: Preis + Kalorien ──────────────────────────────────────────────

@Composable
private fun HeroRow(meal: MealEntity, kcal: Int?) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Studi-Preis als großer primary-getönter Block
        meal.priceStudentCents?.let { cents ->
            HeroCard(
                background = colors.primaryContainer,
                foreground = colors.primary,
                label = "Studi-Preis",
                value = "%.2f €".format(cents / 100.0),
                modifier = Modifier
                    .weight(1f)
            )
        }
        // Kalorien als sekundärer Block (amber für "Energie")
        kcal?.let { value ->
            HeroCard(
                background = semantics.amberSurface,
                foreground = semantics.amber,
                label = "Kalorien",
                value = "$value kcal",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HeroCard(
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val semantics = HiUniColors.semantics
    Surface(
        color = background,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = label.uppercase(Locale.GERMAN),
                style = MaterialTheme.typography.labelSmall,
                color = foreground.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = foreground,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun SecondaryPriceLine(meal: MealEntity) {
    val semantics = HiUniColors.semantics
    val parts = buildList {
        meal.priceEmployeeCents?.let { add("Mitarbeiter %.2f €".format(it / 100.0)) }
        meal.priceGuestCents?.let { add("Gast %.2f €".format(it / 100.0)) }
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString("  ·  "),
        style = MaterialTheme.typography.bodySmall,
        color = semantics.onSurfaceMuted
    )
}

// ─── Nutrition Table ─────────────────────────────────────────────────────

@Composable
private fun NutritionTable(values: Map<String, String>) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val order = listOf(
        "caloric_value", "fat", "saturated_fatty_acids",
        "carbohydrates", "sugar", "roughage", "protein", "salt"
    )
    val sorted = values.entries.sortedBy { entry ->
        order.indexOf(entry.key).let { if (it < 0) order.size else it }
    }
    Surface(
        color = semantics.surfaceAlt,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            sorted.forEachIndexed { index, (key, value) ->
                if (index > 0) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = colors.outline.copy(alpha = 0.15f))
                    Spacer(Modifier.height(8.dp))
                }
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
                        fontWeight = if (key == "caloric_value") FontWeight.ExtraBold else FontWeight.SemiBold
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
 * Extrahiert die kcal-Zahl aus dem API-Roh-String "586 kJ (140 kcal)". Wenn
 * das Format abweicht (nur kJ angegeben oder Dezimalstellen), fällt das auf
 * null zurück und der Hero blendet die Kalorien-Pille aus.
 */
private fun extractKcal(raw: String?): Int? {
    if (raw.isNullOrBlank()) return null
    return Regex("(\\d+)\\s*kcal", RegexOption.IGNORE_CASE)
        .find(raw)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
}

// ─── Pills ───────────────────────────────────────────────────────────────

@Composable
private fun DietTagPill(label: String) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val (background, foreground) = when {
        label.contains("vegan", ignoreCase = true) -> semantics.greenSurface to semantics.green
        label.contains("veget", ignoreCase = true) -> semantics.greenSurface to semantics.green
        label.contains("klima", ignoreCase = true) -> semantics.greenSurface to semantics.green
        label.contains("fisch", ignoreCase = true) -> colors.primaryContainer to colors.primary
        label.contains("geflügel", ignoreCase = true) -> colors.primaryContainer to colors.primary
        label.contains("schwein", ignoreCase = true) -> semantics.amberSurface to semantics.amber
        label.contains("rind", ignoreCase = true) -> semantics.amberSurface to semantics.amber
        else -> semantics.surfaceAlt to semantics.onSurfaceMuted
    }
    Pill(label = label, background = background, foreground = foreground)
}

@Composable
private fun AllergenPill(label: String) {
    val semantics = HiUniColors.semantics
    Pill(
        label = label,
        leadingIcon = Icons.Outlined.WarningAmber,
        background = semantics.redSurface,
        foreground = semantics.red
    )
}

@Composable
private fun AdditivePill(label: String) {
    val semantics = HiUniColors.semantics
    Pill(
        label = label,
        background = semantics.amberSurface,
        foreground = semantics.amber
    )
}

@Composable
private fun SpecialPill(label: String) {
    val semantics = HiUniColors.semantics
    Pill(
        label = label,
        leadingIcon = Icons.Outlined.Info,
        background = semantics.surfaceAlt,
        foreground = semantics.onSurfaceMuted
    )
}

@Composable
private fun Pill(
    label: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
    leadingIcon: ImageVector? = null
) {
    Surface(color = background, shape = RoundedCornerShape(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = foreground,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────

private fun Number.sp(): androidx.compose.ui.unit.TextUnit =
    androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
