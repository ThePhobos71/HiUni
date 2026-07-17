package de.transio.hiuni.feature.widgets.exams

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.transio.hiuni.MainActivity
import de.transio.hiuni.R
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.widgets.WidgetDeepLinkController
import de.transio.hiuni.feature.widgets.WidgetHiltEntryPoint
import de.transio.hiuni.feature.widgets.common.WidgetEmpty
import de.transio.hiuni.feature.widgets.common.WidgetHeader
import de.transio.hiuni.feature.widgets.common.WidgetSurface
import de.transio.hiuni.feature.widgets.common.WidgetTheme
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
 * - Countdown mit Ampel-Farbe (rot ≤ 2 Tage, amber ≤ 7 Tage, sonst primary)
 *   in einer eingefärbten Pill.
 * - Icons für Datum/Zeit/Raum aus dem gemeinsamen Widget-Design-Kit.
 * - Whole-Widget-Tap öffnet den Klausuren-Screen via [WidgetDeepLinkController].
 */
class ExamCountdownWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(180.dp, 110.dp)   // ~2×2 — Countdown + Modul
        private val MEDIUM = DpSize(250.dp, 130.dp)  // ~4×2 — + Prüfungstext + Meta-Zeile
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
                    .mapNotNull { exam -> exam.examDate?.let { date -> exam to date } }
                    .filter { (_, date) -> !date.isBefore(today) }
                    .sortedWith(
                        compareBy({ (_, date) -> date }, { (exam, _) -> exam.examTime ?: LocalTime.MIN })
                    )
                    .firstOrNull()
                    ?.first
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

        WidgetSurface(onClick = openApp) {
            WidgetHeader(
                iconRes = R.drawable.ic_widget_exam,
                title = "Nächste Klausur",
                context = null,
            )
            Spacer(GlanceModifier.height(WidgetTheme.HeaderBottomSpacing))

            if (next == null) {
                WidgetEmpty(
                    iconRes = R.drawable.ic_widget_exam,
                    message = "Keine anstehenden Klausuren",
                )
            } else {
                Body(exam = next, today = today, size = size)
            }
        }
    }

    @Composable
    private fun Body(exam: ExamEntity, today: LocalDate, size: DpSize) {
        val showFooter = size.width >= MEDIUM.width && size.height >= MEDIUM.height
        val showPruefer = size.height >= LARGE.height
        // examDate ist per Konstruktion nicht null (siehe mapNotNull-Filter in
        // provideGlance); defensiv trotzdem geguarded, damit ein künftiger
        // Refactor des Callers keine Crash-Falle öffnet.
        val examDate = exam.examDate ?: return
        val days = ChronoUnit.DAYS.between(today, examDate).toInt()
        val (fg, bg) = ampelColors(days)

        // Countdown als Pill mit Ampel-Farbe (rot ≤ 2, amber ≤ 7, sonst primary).
        Box(
            modifier = GlanceModifier
                .background(bg)
                .cornerRadius(10.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = formatCountdown(examDate, exam.examTime, today),
                style = TextStyle(
                    color = fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.height(6.dp))

        Text(
            text = exam.moduleName.ifBlank { exam.pruefungstext },
            style = TextStyle(
                color = WidgetTheme.OnSurface,
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
                    style = TextStyle(color = WidgetTheme.OnSurfaceMuted, fontSize = 12.sp),
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth()
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            MetaRow(exam = exam)
            if (showPruefer) {
                val pruefer = exam.pruefer?.takeIf { it.isNotBlank() }
                if (pruefer != null) {
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = "Prüfer: $pruefer",
                        style = TextStyle(color = WidgetTheme.OnSurfaceMuted, fontSize = 12.sp),
                        maxLines = 1,
                        modifier = GlanceModifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    @Composable
    private fun MetaRow(exam: ExamEntity) {
        val date = exam.examDate?.format(SHORT_DATE)
        val time = exam.examTime?.format(HH_MM)
        val room = exam.rooms.firstOrNull()?.takeIf { it.isNotBlank() }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (date != null) {
                MetaChip(iconRes = R.drawable.ic_widget_calendar, text = date)
            }
            if (time != null) {
                if (date != null) Spacer(GlanceModifier.width(10.dp))
                MetaChip(iconRes = R.drawable.ic_widget_clock, text = time)
            }
            if (room != null) {
                if (date != null || time != null) Spacer(GlanceModifier.width(10.dp))
                MetaChip(iconRes = R.drawable.ic_widget_place, text = room)
            }
        }
    }

    @Composable
    private fun MetaChip(iconRes: Int, text: String) {
        // Eigener Row-Wrapper: sonst emittiert jeder Chip drei Kinder
        // (Image + Spacer + Text) direkt in die MetaRow. Bei Datum + Zeit +
        // Raum wären das 3·3 + 2 Trenn-Spacer = 11 Kinder — genau über der
        // harten Glance-Grenze (GLANCE_MAX_CONTAINER_CHILDREN). Als eigener
        // Container zählt jeder Chip nur noch als *ein* Kind der MetaRow.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(14.dp),
                colorFilter = ColorFilter.tint(WidgetTheme.OnSurfaceMuted),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = text,
                style = TextStyle(color = WidgetTheme.OnSurfaceMuted, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }

    /**
     * Ampel-Mapping: rot ≤ 2 Tage, amber ≤ 7 Tage, sonst primary.
     * Rückgabe: (Textfarbe, Hintergrundfarbe der Pill).
     */
    private fun ampelColors(days: Int): Pair<ColorProvider, ColorProvider> = when {
        days <= 2 -> WidgetTheme.Red to WidgetTheme.RedSurface
        days <= 7 -> WidgetTheme.Amber to WidgetTheme.AmberSurface
        else -> WidgetTheme.Primary to WidgetTheme.PrimaryContainer
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
