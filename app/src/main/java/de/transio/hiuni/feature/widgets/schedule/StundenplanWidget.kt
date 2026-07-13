package de.transio.hiuni.feature.widgets.schedule

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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.transio.hiuni.MainActivity
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.widgets.WidgetHiltEntryPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home-Screen-Widget: Tagesplan von heute.
 *
 * - Nutzt den Glance-Composition-Root ([provideContent]) und rendert dort das
 *   Widget-Compose-UI (Glance-Emitters, nicht Material-Compose).
 * - Datenquelle: [CustomEventEntity] aus dem CalendarRepository (`observeRange`)
 *   für das Zeitfenster [00:00, 24:00) des heutigen Tages in der System-TZ.
 * - Zeigt einen kompakten Header mit Datum + Icon, das die App auf dem
 *   CalendarScreen öffnet. Row-Taps deeplink’en per Extra `eventId` in die App
 *   — MainActivity-Wiring folgt in der Wrap-up-Task.
 * - [SizeMode.Responsive] gibt uns zwei Breakpoints; die eigentliche Skalierung
 *   erledigt eine [LazyColumn], die je nach Widget-Höhe mehr Zeilen zeigt.
 */
class StundenplanWidget : GlanceAppWidget() {

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
        val zone = remember { ZoneId.systemDefault() }
        val from = remember(today) { today.atStartOfDay(zone).toInstant() }
        val to = remember(today) { today.plusDays(1).atStartOfDay(zone).toInstant() }
        // Fenster für den "nächster Uni-Tag"-Hint im Empty-State — 7 Tage voraus.
        val weekAhead = remember(today) { today.plusDays(7).atStartOfDay(zone).toInstant() }

        val events by remember { entry.calendarRepository().observeRange(from, to) }
            .collectAsState(initial = emptyList())
        val weekEvents by remember {
            entry.calendarRepository().observeRange(to, weekAhead)
        }.collectAsState(initial = emptyList())

        val now = Instant.now()
        // Vergangenes ausblenden, aber laufende Events (Start liegt vor now,
        // Ende danach) müssen sichtbar bleiben — also filtern wir gegen `endTime`.
        val visible = events
            .filter { it.endTime.isAfter(now) }
            .sortedBy { it.startTime }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(SurfaceBackground)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Header(today = today, context = context)
            Spacer(GlanceModifier.height(6.dp))
            if (visible.isEmpty()) {
                EmptyState(today = today, upcoming = weekEvents)
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(items = visible, itemId = { it.id }) { event ->
                        EventRow(event = event, context = context)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(today: LocalDate, context: Context) {
    val locale = Locale.GERMAN
    // "Mo 13.07." — kurz genug für schmale Widgets, verzichtet auf Jahr.
    val weekday = today.format(DateTimeFormatter.ofPattern("EEE", locale))
        .trimEnd('.')
    val date = today.format(DateTimeFormatter.ofPattern("dd.MM.", locale))

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Heute, $weekday $date",
            style = TextStyle(
                color = OnSurface,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        // Öffnet die App auf dem Kalender. `eventId` = -1 → Root-View.
        Box(
            modifier = GlanceModifier
                .size(28.dp)
                .cornerRadius(14.dp)
                .background(AccentSurface)
                .clickable(actionStartActivity(openCalendarIntent(context, eventId = -1L))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = TextStyle(color = Accent, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun EmptyState(today: LocalDate, upcoming: List<CustomEventEntity>) {
    val dayOfWeek = today.dayOfWeek.value // 1=Mo … 7=So
    val isWeekend = dayOfWeek >= 5 // Fr/Sa/So laut Spec
    val hint = if (isWeekend) {
        upcoming.minByOrNull { it.startTime }?.let { next ->
            val nextDay = next.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
            val label = nextDay.format(
                DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)
            )
            "Nächster Uni-Tag: $label"
        } ?: "Nichts anstehend — genieße den freien Tag"
    } else {
        "Nichts anstehend heute — genieße den freien Tag"
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
private fun EventRow(event: CustomEventEntity, context: Context) {
    val zone = ZoneId.systemDefault()
    val time = event.startTime.atZone(zone)
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    val barColor = courseColorForWidget(event)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(actionStartActivity(openCalendarIntent(context, eventId = event.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Farb-Bar links
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
// Colors — bewusst inline gehalten, keine Compose-Theme-Abhängigkeit im Widget.
// Feintuning kann später ins Design-System wandern. `ColorProvider(day, night)`
// gibt uns pro Attribut einen Light- und einen Dark-Wert, den der Launcher
// automatisch je nach System-Theme wählt.
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

/** Deterministische Widget-Farb-Bar pro Kurs. Key-Auswahl entspricht der In-App-Logik
 *  in `feature/calendar/ui/CourseColor.kt`: LSF-Series-Uid (Prefix vor '#'), sonst Titel. */
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
// Deep-Link — Wrap-up-Task wire’d das in MainActivity.onNewIntent.
// ---------------------------------------------------------------------------

internal const val ACTION_OPEN_CALENDAR = "de.transio.hiuni.OPEN_CALENDAR"
internal const val EXTRA_EVENT_ID = "eventId"

private fun openCalendarIntent(context: Context, eventId: Long): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = ACTION_OPEN_CALENDAR
        putExtra(EXTRA_EVENT_ID, eventId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

// ---------------------------------------------------------------------------
// Responsive-Breakpoints
// ---------------------------------------------------------------------------

private val SMALL = DpSize(250.dp, 110.dp)
private val MEDIUM = DpSize(250.dp, 180.dp)
private val LARGE = DpSize(250.dp, 250.dp)
