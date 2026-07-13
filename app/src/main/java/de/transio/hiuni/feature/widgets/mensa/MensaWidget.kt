package de.transio.hiuni.feature.widgets.mensa

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
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
import androidx.glance.unit.ColorProvider
import de.transio.hiuni.MainActivity
import de.transio.hiuni.feature.mensa.data.MealEntity
import de.transio.hiuni.feature.widgets.WidgetDeepLinkController
import de.transio.hiuni.feature.widgets.WidgetHiltEntryPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home-Screen-Widget: Mensa-Speiseplan von heute.
 *
 * - Datenquelle: [MealEntity] aus dem MensaRepository (`observeForDate`)
 *   für den heutigen Tag. Die Location wird intern vom Repository via
 *   SettingsDataStore aufgelöst — kein Location-Picker im Widget.
 * - Kompakter Header mit Datum + Deep-Link ins App-Mensa-Tab.
 * - Meal-Row: Category-Pill (grün für vegan/vegetarisch, sonst grau),
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
        val openApp = actionStartActivity(openMensaIntent(context))

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(SurfaceBackground)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clickable(openApp)
        ) {
            Header(today = today)
            Spacer(GlanceModifier.height(6.dp))
            if (sorted.isEmpty()) {
                EmptyState(today = today)
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(items = sorted, itemId = { it.sourceId.hashCode().toLong() }) { meal ->
                        MealRow(meal = meal)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(today: LocalDate) {
    val locale = Locale.GERMAN
    val weekday = today.format(DateTimeFormatter.ofPattern("EEE", locale))
        .trimEnd('.')
    val date = today.format(DateTimeFormatter.ofPattern("dd.MM.", locale))

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Mensa heute · $weekday $date",
            style = TextStyle(
                color = OnSurface,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
    }
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
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = hint,
            style = TextStyle(color = OnSurfaceMuted),
        )
    }
}

@Composable
private fun MealRow(meal: MealEntity) {
    val tagSet = meal.tags.split(',').map { it.trim().lowercase() }.toSet()
    val isVeganOrVeggie = "vegan" in tagSet || "vegetarisch" in tagSet
    val pillBg = if (isVeganOrVeggie) VeggieSurface else NeutralSurface
    val pillFg = if (isVeganOrVeggie) VeggieAccent else OnSurfaceMuted

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Category-Pill links
        Box(
            modifier = GlanceModifier
                .width(72.dp)
                .cornerRadius(8.dp)
                .background(pillBg)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = shortCategory(meal.category),
                maxLines = 1,
                style = TextStyle(
                    color = pillFg,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = meal.name,
            maxLines = 1,
            style = TextStyle(color = OnSurface),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(6.dp))
        if (meal.priceLabel.isNotBlank()) {
            Text(
                text = meal.priceLabel,
                maxLines = 1,
                style = TextStyle(
                    color = OnSurface,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

/**
 * "Vegan Gericht 1" → "Vegan 1". Kürzt STW-Category-Strings auf den
 * Pill-Platz. Fallback: das rohe Feld — Truncate übernimmt der Text-Layout.
 */
private fun shortCategory(raw: String): String {
    val lower = raw.lowercase()
    val prefix = when {
        lower.startsWith("vegan") -> "Vegan"
        lower.startsWith("vegetarisch") -> "Veggie"
        lower.startsWith("fleisch") -> "Fleisch"
        lower.startsWith("fisch") -> "Fisch"
        lower.startsWith("beilage") -> "Beilage"
        lower.startsWith("suppe") -> "Suppe"
        lower.startsWith("dessert") -> "Dessert"
        lower.startsWith("aktion") -> "Aktion"
        else -> raw
    }
    // Zahl am Ende erhalten ("Gericht 1" → "1")
    val trailing = Regex("(\\d+)\\s*$").find(raw)?.value?.trim()
    return if (trailing != null && prefix != raw) "$prefix $trailing" else prefix
}

// ---------------------------------------------------------------------------
// Colors — analog zum StundenplanWidget: DayNightColorProvider pro Attribut,
// damit der Launcher automatisch Light/Dark wählt.
// ---------------------------------------------------------------------------

private val SurfaceBackground = DayNightColorProvider(
    day = Color(0xFFFFFFFF),
    night = Color(0xFF1B1B1F),
)
private val OnSurface = DayNightColorProvider(
    day = Color(0xFF1B1B1F),
    night = Color(0xFFECECEE),
)
private val OnSurfaceMuted: ColorProvider = DayNightColorProvider(
    day = Color(0xFF5A5A63),
    night = Color(0xFFAFAFB6),
)
private val VeggieSurface = DayNightColorProvider(
    day = Color(0xFFDFF5E4),
    night = Color(0xFF224A32),
)
private val VeggieAccent: ColorProvider = DayNightColorProvider(
    day = Color(0xFF1E7A3E),
    night = Color(0xFF7CD9A2),
)
private val NeutralSurface = DayNightColorProvider(
    day = Color(0xFFEDEDF1),
    night = Color(0xFF2A2A30),
)

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
