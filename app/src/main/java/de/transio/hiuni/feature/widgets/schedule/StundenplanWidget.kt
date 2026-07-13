package de.transio.hiuni.feature.widgets.schedule

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import de.transio.hiuni.MainActivity
import de.transio.hiuni.R
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.widgets.WidgetDeepLinkController
import de.transio.hiuni.feature.widgets.WidgetHiltEntryPoint
import de.transio.hiuni.feature.widgets.common.WidgetEmpty
import de.transio.hiuni.feature.widgets.common.WidgetHeader
import de.transio.hiuni.feature.widgets.common.WidgetPalette
import de.transio.hiuni.feature.widgets.common.WidgetSurface
import de.transio.hiuni.feature.widgets.common.WidgetTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home-Screen-Widget: Tagesplan von heute.
 *
 * - Nutzt das Widget-Design-Kit (`common/`): [WidgetSurface] als Card-Rahmen,
 *   [WidgetHeader] für Icon-+-Titel-+-Datum und [WidgetPalette] für die
 *   deterministische Kurs-Farbe je Event.
 * - Datenquelle: [CustomEventEntity] aus dem CalendarRepository
 *   (`observeRange`) für das Zeitfenster [00:00, 24:00) des heutigen Tages.
 * - Tap auf das Widget öffnet die App auf dem Kalender-Tab
 *   (siehe [WidgetDeepLinkController.ACTION_OPEN_CALENDAR]).
 * - [SizeMode.Responsive] liefert dem Launcher drei Layouts; die eigentliche
 *   Skalierung erledigt eine [LazyColumn].
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

        val events by remember { entry.calendarRepository().observeRange(from, to) }
            .collectAsState(initial = emptyList())

        val now = Instant.now()
        // Vergangenes ausblenden, aber laufende Events (Start liegt vor now,
        // Ende danach) müssen sichtbar bleiben — also filtern wir gegen `endTime`.
        val visible = events
            .filter { it.endTime.isAfter(now) }
            .sortedBy { it.startTime }

        val openApp = actionStartActivity(openCalendarIntent(context))

        val locale = Locale.GERMAN
        val weekday = today.format(DateTimeFormatter.ofPattern("EEE", locale)).trimEnd('.')
        val date = today.format(DateTimeFormatter.ofPattern("dd.MM.", locale))

        WidgetSurface(onClick = openApp) {
            WidgetHeader(
                iconRes = R.drawable.ic_widget_schedule,
                title = "Heute",
                context = "$weekday $date",
            )
            Spacer(GlanceModifier.height(WidgetTheme.HeaderBottomSpacing))
            if (visible.isEmpty()) {
                WidgetEmpty(
                    iconRes = R.drawable.ic_widget_schedule,
                    message = "Keine Termine heute",
                )
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(items = visible, itemId = { it.id }) { event ->
                        EventRow(event = event)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: CustomEventEntity) {
    val zone = ZoneId.systemDefault()
    val time = event.startTime.atZone(zone)
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    // Key-Auswahl entspricht der In-App-Logik in `feature/calendar/ui/CourseColor.kt`
    // (LSF-Series-Uid = Prefix vor '#', sonst Titel). Dadurch kriegt dieselbe
    // Vorlesung in App + Widget denselben Akzent.
    val courseKey = event.sourceReference?.substringBefore('#')?.takeIf { it.isNotBlank() }
        ?: event.title
    val color = WidgetPalette.colorFor(courseKey)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Kurs-Farb-Streifen links (4dp, feste Zeilenhöhe 32dp).
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(32.dp)
                .cornerRadius(2.dp)
                .background(color.dot),
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = event.title,
                maxLines = 1,
                style = TextStyle(
                    color = WidgetTheme.OnSurface,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_clock),
                    contentDescription = null,
                    modifier = GlanceModifier.size(14.dp),
                    colorFilter = ColorFilter.tint(WidgetTheme.OnSurfaceMuted),
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = time,
                    maxLines = 1,
                    style = TextStyle(color = WidgetTheme.OnSurfaceMuted),
                )
                val loc = event.location
                if (!loc.isNullOrBlank()) {
                    Spacer(GlanceModifier.width(8.dp))
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_place),
                        contentDescription = null,
                        modifier = GlanceModifier.size(14.dp),
                        colorFilter = ColorFilter.tint(WidgetTheme.OnSurfaceMuted),
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        text = loc,
                        maxLines = 1,
                        style = TextStyle(color = WidgetTheme.OnSurfaceMuted),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Deep-Link — WidgetDeepLinkController.handleIntent verarbeitet das in
// MainActivity und emittiert an den AppNavGraph.
// ---------------------------------------------------------------------------

private fun openCalendarIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = WidgetDeepLinkController.ACTION_OPEN_CALENDAR
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

// ---------------------------------------------------------------------------
// Responsive-Breakpoints
// ---------------------------------------------------------------------------

private val SMALL = DpSize(250.dp, 110.dp)
private val MEDIUM = DpSize(250.dp, 180.dp)
private val LARGE = DpSize(250.dp, 250.dp)
