package de.transio.hiuni.feature.widgets.exams

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.transio.hiuni.MainActivity
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.widgets.WidgetDeepLinkController
import de.transio.hiuni.feature.widgets.WidgetHiltEntryPoint
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Home-Screen-Widget: Countdown zur nächsten anstehenden Klausur.
 *
 * - Datenquelle: [de.transio.hiuni.feature.lsf.data.LsfExamsRepository.observeAll]
 *   liefert alle bekannten Klausuren; das Widget filtert lokal auf zukünftige
 *   Einträge mit gesetztem [ExamEntity.examDate] und wählt die früheste.
 * - Countdown-Format skaliert mit der Rest-Zeit: "HEUTE HH:mm" / "MORGEN HH:mm"
 *   / "in X Tagen" / "am EEE, d. MMM" (>14 Tage).
 * - [SizeMode.Responsive] liefert dem Launcher drei Layouts (SMALL/MEDIUM/LARGE);
 *   die eigentliche Verdichtung passiert per [LocalSize]-Branch im Compose-Baum,
 *   damit wir nicht drei parallele Widget-Klassen brauchen.
 * - Whole-Widget-Tap öffnet den Klausuren-Screen via [WidgetDeepLinkController].
 */
class ExamCountdownWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(180.dp, 110.dp)   // ~2×2 — Countdown + Modul
        private val MEDIUM = DpSize(250.dp, 130.dp)  // ~4×2 — + Prüfungstext + Raum-Zeile
        private val LARGE = DpSize(250.dp, 200.dp)   // ~4×3 — + Prüfer-Zeile
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = WidgetHiltEntryPoint.get(context).examsRepository()
        provideContent {
            val today = remember { LocalDate.now() }
            val exams by repo.observeAll().collectAsState(initial = emptyList())
            val next = remember(exams, today) {
                exams
                    .filter { it.examDate != null && !it.examDate!!.isBefore(today) }
                    .sortedWith(
                        compareBy({ it.examDate }, { it.examTime ?: LocalTime.MIN })
                    )
                    .firstOrNull()
            }
            Content(next = next, today = today)
        }
    }

    @Composable
    private fun Content(next: ExamEntity?, today: LocalDate) {
        val context = LocalContext.current
        val size = LocalSize.current

        // Deep-Link auf den Klausuren-Screen. Extras sind nicht nötig — V1
        // zeigt einfach die volle Liste, User tappt selbst weiter.
        val openApp = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                action = WidgetDeepLinkController.ACTION_OPEN_EXAMS
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(SurfaceBackground)
                .cornerRadius(20.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clickable(openApp)
        ) {
            Text(
                text = "Nächste Klausur",
                style = TextStyle(
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.height(4.dp))

            if (next == null) {
                EmptyBody()
            } else {
                Body(exam = next, today = today, size = size)
            }
        }
    }

    @Composable
    private fun EmptyBody() {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "Keine anstehenden Klausuren",
                style = TextStyle(color = OnSurfaceMuted, fontSize = 14.sp)
            )
        }
    }

    @Composable
    private fun Body(exam: ExamEntity, today: LocalDate, size: DpSize) {
        val showFooter = size.width >= MEDIUM.width && size.height >= MEDIUM.height
        val showPruefer = size.height >= LARGE.height

        Text(
            text = formatCountdown(exam.examDate!!, exam.examTime, today),
            style = TextStyle(
                color = OnSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = exam.moduleName.ifBlank { exam.pruefungstext },
            style = TextStyle(
                color = OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            modifier = GlanceModifier.fillMaxWidth()
        )
        if (showFooter) {
            // Prüfungstext nur zeigen wenn er nicht mit dem Modulnamen kollidiert
            // (ExamEntity.moduleName wird oft aus dem Prüfungstext extrahiert —
            // dann wäre die zweite Zeile redundant).
            val subtitle = exam.pruefungstext.takeIf {
                it.isNotBlank() && !it.equals(exam.moduleName, ignoreCase = true)
            }
            if (subtitle != null) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = TextStyle(color = OnSurfaceMuted, fontSize = 12.sp),
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth()
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = formatRoomAndDate(exam),
                style = TextStyle(color = OnSurfaceMuted, fontSize = 12.sp),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth()
            )
            if (showPruefer) {
                val pruefer = exam.pruefer?.takeIf { it.isNotBlank() }
                if (pruefer != null) {
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = "Prüfer: $pruefer",
                        style = TextStyle(color = OnSurfaceMuted, fontSize = 12.sp),
                        maxLines = 1,
                        modifier = GlanceModifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Formatter-Helper
// ---------------------------------------------------------------------------

private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val WEEKDAY_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)
private val SHORT_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE dd.MM.", Locale.GERMAN)

/**
 * Bildet die Rest-Zeit als menschenlesbares Label ab. Für Termine, die heute
 * oder morgen anstehen, verlinken wir den bekannten Uhrzeit-Anteil mit —
 * dann ist "HEUTE 09:00" auf einen Blick lesbar. Für Termine in ferner
 * Zukunft (> 14 Tage) reicht das Datum, weil "in 42 Tagen" schwer greifbar ist.
 */
private fun formatCountdown(date: LocalDate, time: LocalTime?, now: LocalDate): String {
    val days = ChronoUnit.DAYS.between(now, date).toInt()
    return when {
        days < 0 -> "vergangen" // defensiv — sollte durch Filter nie triggern
        days == 0 -> time?.let { "HEUTE ${it.format(HH_MM)}" } ?: "HEUTE"
        days == 1 -> time?.let { "MORGEN ${it.format(HH_MM)}" } ?: "MORGEN"
        days in 2..14 -> "in $days Tagen"
        else -> "am ${date.format(WEEKDAY_DATE)}"
    }
}

/** Baut die Raum-+-Datum-Zeile: "F 002 · Mo 15.07. um 09:00" oder Fallbacks. */
private fun formatRoomAndDate(exam: ExamEntity): String {
    val date = exam.examDate?.format(SHORT_DATE).orEmpty()
    val time = exam.examTime?.format(HH_MM)
    val dateTime = when {
        date.isBlank() -> ""
        time != null -> "$date um $time"
        else -> date
    }
    val room = exam.rooms.firstOrNull()?.takeIf { it.isNotBlank() }
    return when {
        room != null && dateTime.isNotBlank() -> "$room · $dateTime"
        room != null -> room
        else -> dateTime
    }
}

// ---------------------------------------------------------------------------
// Colors — analog zu StundenplanWidget: eigene ColorProvider mit Day/Night-
// Splits, weil Glance außerhalb der Compose-Theme-Kette läuft.
// ---------------------------------------------------------------------------

private val SurfaceBackground: ColorProvider = DayNightColorProvider(
    day = Color(0xFFFFFFFF),
    night = Color(0xFF1B1B1F),
)
private val OnSurface: ColorProvider = DayNightColorProvider(
    day = Color(0xFF1B1B1F),
    night = Color(0xFFECECEE),
)
private val OnSurfaceMuted: ColorProvider = DayNightColorProvider(
    day = Color(0xFF5A5A63),
    night = Color(0xFFAFAFB6),
)
private val Accent: ColorProvider = DayNightColorProvider(
    day = Color(0xFF3B4FE0),
    night = Color(0xFF9DA8FF),
)
