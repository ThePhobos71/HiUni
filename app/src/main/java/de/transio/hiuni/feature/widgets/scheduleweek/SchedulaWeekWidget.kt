package de.transio.hiuni.feature.widgets.scheduleweek

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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.LazyListScope
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
 * Home-Screen-Widget: 7-Tage-Agenda des Stundenplans.
 *
 * - Nutzt das Widget-Design-Kit (`common/`): [WidgetSurface] als Card-Rahmen,
 *   [WidgetHeader] mit Kalender-Icon und [WidgetPalette] für die
 *   deterministische Kurs-Farbe je Event.
 * - Zeigt Events der kommenden 7 Tage (heute + 6 Folgetage), gruppiert nach
 *   Datum. Section-Header "Mo 15.07." markieren den Tageswechsel.
 * - Datenquelle: [CustomEventEntity] aus dem CalendarRepository via
 *   `observeRange(from = heute-00:00, to = heute+7d-00:00)`.
 * - Tage ohne Events werden übersprungen. Wenn alle 7 Tage leer sind, gibt
 *   es einen einzelnen [WidgetEmpty]-State.
 * - Whole-Widget-Tap öffnet die App im Kalender-Tab.
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
        val entry = remember(context) { WidgetHiltEntryPoint.get(context) }
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

        val openApp = actionStartActivity(openCalendarWeekIntent(context))

        WidgetSurface(onClick = openApp) {
            WidgetHeader(
                iconRes = R.drawable.ic_widget_calendar,
                title = "7 Tage",
            )
            Spacer(GlanceModifier.height(WidgetTheme.HeaderBottomSpacing))
            if (grouped.isEmpty()) {
                WidgetEmpty(
                    iconRes = R.drawable.ic_widget_calendar,
                    message = "Keine Termine diese Woche",
                )
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    renderAgenda(grouped = grouped)
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
                EventRow(event = event)
            }
        }
    }
}

@Composable
private fun DaySectionHeader(date: LocalDate) {
    val locale = Locale.GERMAN
    val weekday = date.format(DateTimeFormatter.ofPattern("EEE", locale)).trimEnd('.')
    val shortDate = date.format(DateTimeFormatter.ofPattern("dd.MM.", locale))
    Text(
        text = "$weekday $shortDate",
        maxLines = 1,
        style = TextStyle(
            color = WidgetTheme.OnSurfaceMuted,
            fontWeight = FontWeight.Bold,
        ),
        modifier = GlanceModifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun EventRow(event: CustomEventEntity) {
    val zone = ZoneId.systemDefault()
    val time = event.startTime.atZone(zone)
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    // Key-Auswahl analog zur App (`feature/calendar/ui/CourseColor.kt`) und
    // zum Stundenplan-Heute-Widget — LSF-Series-Uid (Prefix vor '#'), sonst
    // Titel. Dieselbe Vorlesung → derselbe Akzent.
    val courseKey = event.sourceReference?.substringBefore('#')?.takeIf { it.isNotBlank() }
        ?: event.title
    val color = WidgetPalette.colorFor(courseKey)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
