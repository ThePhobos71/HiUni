package de.transio.hiuni.feature.widgets.mensa

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.transio.hiuni.MainActivity
import de.transio.hiuni.R
import de.transio.hiuni.feature.mensa.data.MealEntity
import de.transio.hiuni.feature.widgets.WidgetDeepLinkController
import de.transio.hiuni.feature.widgets.WidgetHiltEntryPoint
import de.transio.hiuni.feature.widgets.common.WidgetEmpty
import de.transio.hiuni.feature.widgets.common.WidgetHeader
import de.transio.hiuni.feature.widgets.common.WidgetSurface
import de.transio.hiuni.feature.widgets.common.WidgetTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home-Screen-Widget: Mensa-Speiseplan von heute.
 *
 * - Datenquelle: [MealEntity] aus dem MensaRepository (`observeForDate`)
 *   für den heutigen Tag. Die Location wird intern vom Repository via
 *   SettingsDataStore aufgelöst — kein Location-Picker im Widget.
 * - Rahmen + Header stammen aus dem gemeinsamen Widget-Design-Kit
 *   ([WidgetSurface], [WidgetHeader]); Farben aus [WidgetTheme].
 * - Meals nach Tageszeit gruppiert (Frühstück / Mittag / Abend), jede
 *   Gruppe mit eigenem Section-Header. Grundlage: das Prefix in
 *   `MealEntity.category` (STW-API packt "Abend · " bzw. "Frühstück · "
 *   vor den Lane-Namen; Mittag hat kein Prefix).
 * - Meal-Row: schmale grüne Vegan/Veggie-Pill (nur wenn zutreffend),
 *   Meal-Name (weight 1f, single line), Preis rechts (aus `priceLabel`).
 * - [SizeMode.Responsive] gibt uns drei Breakpoints; die eigentliche
 *   Skalierung erledigt eine [LazyColumn].
 */
class MensaWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode =
        SizeMode.Responsive(
            setOf(
                SMALL,  // ~4x2
                MEDIUM, // ~4x3
                LARGE,  // ~4x4+
            )
        )

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        provideContent {
            Content()
        }
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current
        val entry = remember(context) { WidgetHiltEntryPoint.get(context) }
        val today = remember { LocalDate.now() }

        val meals by remember { entry.mensaRepository().observeForDate(today) }
            .collectAsState(initial = emptyList())

        val sorted = meals.sortedBy { it.category }
        val grouped = sorted.groupBy { mealtimeOf(it.category) }
        // Sortierung: Frühstück → Mittag → Abend (enum-ordinal reicht).
        val orderedGroups = Mealtime.entries.mapNotNull { mt ->
            grouped[mt]?.let { mt to it }
        }
        val openApp = actionStartActivity(openMensaIntent(context))

        val locale = Locale.GERMAN
        val weekday = today.format(DateTimeFormatter.ofPattern("EEE", locale))
            .trimEnd('.')
        val date = today.format(DateTimeFormatter.ofPattern("dd.MM.", locale))

        WidgetSurface(onClick = openApp) {
            WidgetHeader(
                iconRes = R.drawable.ic_widget_utensils,
                title = "Mensa",
                context = "$weekday $date",
            )
            Spacer(GlanceModifier.height(WidgetTheme.HeaderBottomSpacing))
            if (orderedGroups.isEmpty()) {
                EmptyState(today = today)
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    orderedGroups.forEachIndexed { index, (mealtime, mealsInGroup) ->
                        val isFirst = index == 0
                        item(itemId = mealtime.ordinal.toLong() + 1_000_000L) {
                            SectionHeader(label = mealtime.label, isFirst = isFirst)
                        }
                        items(
                            items = mealsInGroup,
                            itemId = { it.sourceId.hashCode().toLong() },
                        ) { meal ->
                            MealRow(meal = meal)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tageszeit-Buckets. Reihenfolge im enum entspricht der Anzeige-Reihenfolge.
 */
private enum class Mealtime(val label: String) {
    Fruehstueck("Frühstück"),
    Mittag("Mittag"),
    Abend("Abend"),
}

private fun mealtimeOf(category: String): Mealtime = when {
    category.startsWith("Frühstück") -> Mealtime.Fruehstueck
    category.startsWith("Abend") -> Mealtime.Abend
    else -> Mealtime.Mittag
}

@Composable
private fun SectionHeader(label: String, isFirst: Boolean) {
    // Erste Sektion sitzt direkt unter dem Header-Spacing → kein zusätzliches
    // Top-Padding. Folge-Sektionen bekommen etwas mehr Luft zur vorherigen Row.
    val topPadding = if (isFirst) 0.dp else 10.dp
    Text(
        text = label,
        maxLines = 1,
        style = TextStyle(
            color = WidgetTheme.OnSurfaceMuted,
            fontWeight = FontWeight.Bold,
        ),
        modifier = GlanceModifier.padding(top = topPadding, bottom = 2.dp),
    )
}

@Composable
private fun EmptyState(today: LocalDate) {
    val dayOfWeek = today.dayOfWeek.value // 1=Mo … 7=So
    val isWeekend = dayOfWeek >= 6 // Sa/So — Mensa geschlossen
    val hint = if (isWeekend) {
        "Heute keine Speisen — Wochenende?"
    } else {
        "Kein Menü heute"
    }
    WidgetEmpty(iconRes = R.drawable.ic_widget_utensils, message = hint)
}

@Composable
private fun MealRow(meal: MealEntity) {
    val tagSet = meal.tags.split(',').map { it.trim().lowercase() }.toSet()
    val veggieLabel = when {
        "vegan" in tagSet -> "Vegan"
        "vegetarisch" in tagSet -> "Veggie"
        else -> null
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Pill nur bei vegan/vegetarisch — sonst nimmt der Meal-Name den
        // ganzen Platz ein. Die STW-Category ("Abend Gericht 1", "Fleisch
        // Gericht 2") ist informationsarm; nur Vegan/Veggie ist ein
        // relevantes Filter-Signal fürs Auge.
        if (veggieLabel != null) {
            Box(
                modifier = GlanceModifier
                    .cornerRadius(6.dp)
                    .background(WidgetTheme.GreenSurface)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = veggieLabel,
                    maxLines = 1,
                    style = TextStyle(
                        color = WidgetTheme.Green,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            Spacer(GlanceModifier.width(8.dp))
        }
        Text(
            text = meal.name,
            maxLines = 1,
            style = TextStyle(color = WidgetTheme.OnSurface),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(6.dp))
        if (meal.priceLabel.isNotBlank()) {
            Text(
                text = meal.priceLabel,
                maxLines = 1,
                style = TextStyle(
                    color = WidgetTheme.OnSurface,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Deep-Link
// ---------------------------------------------------------------------------

private fun openMensaIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = WidgetDeepLinkController.ACTION_OPEN_MENSA
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

// ---------------------------------------------------------------------------
// Responsive-Breakpoints
// ---------------------------------------------------------------------------

private val SMALL = DpSize(250.dp, 110.dp)
private val MEDIUM = DpSize(250.dp, 180.dp)
private val LARGE = DpSize(250.dp, 250.dp)
