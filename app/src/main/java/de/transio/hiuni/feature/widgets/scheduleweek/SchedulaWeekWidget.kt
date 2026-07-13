package de.transio.hiuni.feature.widgets.scheduleweek

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
import androidx.glance.appwidget.lazy.LazyListScope
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.transio.hiuni.MainActivity
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.widgets.WidgetDeepLinkController
import de.transio.hiuni.feature.widgets.WidgetHiltEntryPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home-Screen-Widget: 7-Tage-Agenda des Stundenplans.
 *
 * - Zeigt Events der kommenden 7 Tage (heute + 6 Folgetage), gruppiert nach
 *   Datum. Section-Header "Mo · 30.06." markieren die Tageswechsel.
 * - Datenquelle: [CustomEventEntity] aus dem CalendarRepository via
 *   `observeRange(from = heute-00:00, to = heute+7d-00:00)`. Room + Recurrence-
 *   Expander liefern die entpackten Instanzen.
 * - Tage ohne Events werden übersprungen — kein "keine Termine"-Placeholder
 *   pro Tag, weil das im Widget-Format zu verschwenderisch wäre. Wenn ALLE
 *   7 Tage leer sind, gibt es einen einzelnen Empty-State.
 * - Whole-widget-Tap sowie Row-Tap öffnen die App im Kalender-Tab (V1: kein
 *   Event-Detail-Deeplink, das ist ein Follow-Up).
 * - [SizeMode.Responsive]: bei kleinen Höhen scrollt die [LazyColumn], sodass
 *   sich das Widget vom 4x2- bis zum vollen 4x5+-Layout anpasst, ohne dass
 *   wir separate Compose-Bäume pflegen müssen.
 */
class SchedulaWeekWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode =
        SizeMode.Responsive(
            setOf(
                SMALL,  // ~4x2 — heute + morgen
                MEDIUM, // ~4x3 — 3-4 Tage
                LARGE,  // ~4x5+ — volle Woche scrollbar
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
        remember(context) { WidgetHiltEntryPoint.get(context) }.let { entry ->
            val today = remember { LocalDate.now() }
            val zone = remember { ZoneId.systemDefault() }
            val from = remember(today) { today.atStartOfDay(zone).toInstant() }
            val to = remember(today) { today.plusDays(7).atStartOfDay(zone).toInstant() }

            val events by remember { entry.calendarRepository().observeRange(from, to) }
                .collectAsState(initial = emptyList())

            val now = Instant.now()
            // Vergangenes ausblenden, aber laufende Events (Ende in der Zukunft)
            // sollen sichtbar bleiben — daher gegen endTime filtern.
            val visible = events
                .filter { it.endTime.isAfter(now) }
                .sortedBy { it.startTime }

            // Gruppierung nach LocalDate. `groupBy` behält die Insertion-Order,
            // deshalb kommt der bereits nach startTime sortierte Input hier
            // durchsortiert nach Datum aufeinander an.
            val grouped: LinkedHashMap<LocalDate, MutableList<CustomEventEntity>> =
                LinkedHashMap()
            for (event in visible) {
                val date = event.startTime.atZone(zone).toLocalDate()
                grouped.getOrPut(date) { mutableListOf() }.add(event)
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(SurfaceBackground)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Header(context = context)
                Spacer(GlanceModifier.height(6.dp))
                if (grouped.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        renderAgenda(grouped = grouped, context = context)
                    }
                }
            }
        }
    }
}

/**
 * Erzeugt Section-Header + Event-Rows als LazyColumn-Items. Wir nutzen keine
 * verschachtelten Layouts — Glance-LazyColumn will pro Item einen eigenen
 * RemoteViews-Slot, also emitten wir Header und Event als geschwister-Items
 * mit disjunkten `itemId`s.
 */
private fun LazyListScope.renderAgenda(
    grouped: Map<LocalDate, List<CustomEventEntity>>,
    context: Context,
) {
    for ((date, dayEvents) in grouped) {
        // Section-Header stabile ID: negative Zahlen aus dem Datum, damit sie
        // nie mit den positiven Event-IDs kollidieren.
        val headerId = -date.toEpochDay()
        item(itemId = headerId) {
            DaySectionHeader(date = date)
        }
        for (event in dayEvents) {
            item(itemId = event.id) {
                EventRow(event = event, context = context)
            }
        }
    }
}

@Composable
private fun Header(context: Context) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(openCalendarWeekIntent(context))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "7 Tage-Übersicht",
            style = TextStyle(
                color = OnSurface,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        // Kleines Icon-Feld rechts. Öffnet dieselbe Route wie ein Row-Tap.
        Box(
            modifier = GlanceModifier
                .size(28.dp)
                .cornerRadius(14.dp)
                .background(AccentSurface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "›",
                style = TextStyle(color = Accent, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Keine Termine in den nächsten 7 Tagen.",
            style = TextStyle(color = OnSurfaceMuted),
        )
    }
}

@Composable
private fun DaySectionHeader(date: LocalDate) {
    val locale = Locale.GERMAN
    val weekday = date.format(DateTimeFormatter.ofPattern("EEE", locale)).trimEnd('.')
    val shortDate = date.format(DateTimeFormatter.ofPattern("dd.MM.", locale))
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
    ) {
        Text(
            text = "$weekday · $shortDate",
            style = TextStyle(
                color = Accent,
                fontWeight = FontWeight.Bold,
            ),
        )
        // Feine Divider-Line unter dem Header — 1dp hohe Box, weil Glance
        // keinen dedizierten Divider-Emitter kennt.
        Spacer(GlanceModifier.height(2.dp))
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DividerColor),
        ) {}
    }
}

@Composable
private fun EventRow(event: CustomEventEntity, context: Context) {
    val zone = ZoneId.systemDefault()
    val time = event.startTime.atZone(zone)
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    val barColor = courseColorForWidget(event)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(actionStartActivity(openCalendarWeekIntent(context))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(32.dp)
                .cornerRadius(2.dp)
                .background(barColor),
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = time,
            style = TextStyle(
                color = OnSurface,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.width(44.dp),
        )
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = event.title,
                maxLines = 1,
                style = TextStyle(color = OnSurface),
            )
            val loc = event.location
            if (!loc.isNullOrBlank()) {
                Text(
                    text = loc,
                    maxLines = 1,
                    style = TextStyle(color = OnSurfaceMuted),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Colors — inline gehalten, keine Compose-Theme-Abhängigkeit im Widget.
// `ColorProvider(day, night)` gibt uns pro Attribut einen Light- und Dark-Wert,
// den der Launcher automatisch je nach System-Theme wählt. Werte matched mit
// StundenplanWidget, damit die beiden Widgets visuell zueinander passen.
// ---------------------------------------------------------------------------

private val SurfaceBackground = DayNightColorProvider(
    day = Color(0xFFFFFFFF),
    night = Color(0xFF1B1B1F),
)
private val OnSurface = DayNightColorProvider(
    day = Color(0xFF1B1B1F),
    night = Color(0xFFECECEE),
)
private val OnSurfaceMuted = DayNightColorProvider(
    day = Color(0xFF5A5A63),
    night = Color(0xFFAFAFB6),
)
private val Accent = DayNightColorProvider(
    day = Color(0xFF3B4FE0),
    night = Color(0xFF9DA8FF),
)
private val AccentSurface = DayNightColorProvider(
    day = Color(0xFFE6E9FF),
    night = Color(0xFF2C3070),
)
private val DividerColor = DayNightColorProvider(
    day = Color(0xFFE2E2E7),
    night = Color(0xFF34343A),
)

/**
 * Deterministische Widget-Farb-Bar pro Kurs. Key-Auswahl entspricht der
 * In-App-Logik in `feature/calendar/ui/CourseColor.kt` (LSF-Series-Uid Prefix
 * vor '#', sonst Titel) und ist mit dem StundenplanWidget identisch, sodass
 * dasselbe Modul-Event in beiden Widgets in derselben Farbe erscheint.
 */
private fun courseColorForWidget(event: CustomEventEntity): ColorProvider {
    val key = event.sourceReference?.substringBefore('#')?.takeIf { it.isNotBlank() }
        ?: event.courseLsfId?.takeIf { it.isNotBlank() }
        ?: event.title
    val index = ((key.hashCode() % WIDGET_PALETTE.size) + WIDGET_PALETTE.size) % WIDGET_PALETTE.size
    return WIDGET_PALETTE[index]
}

private val WIDGET_PALETTE: List<ColorProvider> = listOf(
    DayNightColorProvider(day = Color(0xFF3B4FE0), night = Color(0xFF9DA8FF)), // Indigo
    DayNightColorProvider(day = Color(0xFF2E9E60), night = Color(0xFF7CD9A2)), // Green
    DayNightColorProvider(day = Color(0xFFE0A020), night = Color(0xFFFFCE73)), // Amber
    DayNightColorProvider(day = Color(0xFF9C4CD1), night = Color(0xFFD5A6F0)), // Purple
    DayNightColorProvider(day = Color(0xFFD94848), night = Color(0xFFF29191)), // Red
)

// ---------------------------------------------------------------------------
// Deep-Link — WidgetDeepLinkController.handleIntent verarbeitet das im
// MainActivity und emittiert an den AppNavGraph.
// ---------------------------------------------------------------------------

private fun openCalendarWeekIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = WidgetDeepLinkController.ACTION_OPEN_CALENDAR_WEEK
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

// ---------------------------------------------------------------------------
// Responsive-Breakpoints
// ---------------------------------------------------------------------------

private val SMALL = DpSize(250.dp, 110.dp)
private val MEDIUM = DpSize(250.dp, 180.dp)
private val LARGE = DpSize(250.dp, 300.dp)
