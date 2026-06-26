package de.transio.hiuni.feature.exams.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AssignmentLate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.design.HiUniSemanticColors
import de.transio.hiuni.feature.exams.ExamsViewModel
import de.transio.hiuni.feature.lsf.data.ExamEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun ExamsScreen(
    onBack: () -> Unit,
    viewModel: ExamsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Top-Bar — gleicher Stil wie ProfileScreen / NotificationsScreen.
            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(
                    bottomStart = HiUniRadii.big,
                    bottomEnd = HiUniRadii.big
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Zurück",
                            tint = colors.onSurface
                        )
                    }
                    Text(
                        text = "Klausuren",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (state.exams.isEmpty() && !state.isLoading) {
                    EmptyState(
                        isRefreshing = state.isRefreshing,
                        onRefresh = { viewModel.refresh() }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.nextExam?.let { hero ->
                            item(key = "hero-${hero.rowId}") {
                                CountdownHero(exam = hero, semantics = semantics)
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        val timeline = state.timelineExams
                        if (timeline.isNotEmpty()) {
                            item(key = "timeline-header") {
                                Text(
                                    text = "Weitere Termine".uppercase(Locale.GERMAN),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = semantics.onSurfaceMuted,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                            }
                            timeline.forEach { exam ->
                                item(key = "exam-${exam.rowId}") {
                                    TimelineRow(exam = exam, semantics = semantics)
                                }
                            }
                        }
                        item(key = "spacer-bottom") { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Countdown-Hero
// ---------------------------------------------------------------------------

@Composable
private fun CountdownHero(
    exam: ExamEntity,
    semantics: HiUniSemanticColors
) {
    val colors = MaterialTheme.colorScheme
    val daysUntil = exam.examDate?.let {
        ChronoUnit.DAYS.between(LocalDate.now(), it)
    }
    val countdownLabel = when {
        daysUntil == null -> "Termin steht aus"
        daysUntil < 0L -> "Heute!"
        daysUntil == 0L -> "Heute!"
        daysUntil == 1L -> "Morgen"
        else -> "in $daysUntil Tagen"
    }
    // Farb-Stufen fürs Hero: ≤ 3 Tage = rot, ≤ 14 = amber, sonst neutral.
    // Bei „Termin steht aus" (kein Datum) → neutral.
    val countdownColor = when {
        daysUntil == null -> semantics.onSurfaceMuted
        daysUntil <= 3L -> semantics.red
        daysUntil <= 14L -> semantics.amber
        else -> colors.onPrimaryContainer
    }
    val title = exam.moduleName.ifBlank { exam.pruefungstext }

    Surface(
        color = colors.primaryContainer,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AssignmentLate,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nächste Klausur".uppercase(Locale.GERMAN),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onPrimaryContainer.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = countdownLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = countdownColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatHeroDate(exam),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer
                )
                exam.rooms.firstOrNull()?.takeIf { it.isNotBlank() }?.let { room ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Raum $room",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Timeline-Row
// ---------------------------------------------------------------------------

@Composable
private fun TimelineRow(
    exam: ExamEntity,
    semantics: HiUniSemanticColors
) {
    val colors = MaterialTheme.colorScheme
    val daysUntil = exam.examDate?.let {
        ChronoUnit.DAYS.between(LocalDate.now(), it)
    }
    // Akzent-Farbe (Balken + ggf. Pill): ≤7 = rot, ≤21 = amber, sonst primary.
    // Klausuren ohne Datum (oder in der Vergangenheit) bekommen den Neutral-Ton.
    val accent: Color = when {
        daysUntil == null -> semantics.onSurfaceMuted
        daysUntil < 0L -> semantics.onSurfaceMuted
        daysUntil <= 7L -> semantics.red
        daysUntil <= 21L -> semantics.amber
        else -> colors.primary
    }
    val isUrgent = daysUntil != null && daysUntil in 0L..7L
    val isSoon = daysUntil != null && daysUntil in 0L..21L

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // IntrinsicSize.Min, damit der linke 4dp-Balken via fillMaxHeight
                // auf die durch den Text-Block bestimmte Höhe der Row stretched.
                .height(IntrinsicSize.Min)
        ) {
            // Linker farbiger Balken (4dp), füllt die volle Höhe der Card.
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exam.moduleName.ifBlank { exam.pruefungstext },
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatRowSubline(exam),
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted
                    )
                    if (exam.examDate == null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Termin steht aus",
                            style = MaterialTheme.typography.labelSmall,
                            color = semantics.onSurfaceMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                // Tage-Chip (hand-styled Pill, kein M3 AssistChip).
                if (daysUntil != null && daysUntil >= 0L) {
                    val (pillLabel, pillBg, pillFg) = when {
                        daysUntil == 0L -> Triple("Heute", semantics.red, semantics.onRed)
                        daysUntil == 1L -> Triple("Morgen", semantics.amber, semantics.onAmber)
                        isUrgent -> Triple("in $daysUntil Tagen", semantics.red, semantics.onRed)
                        isSoon -> Triple("in $daysUntil Tagen", semantics.amber, semantics.onAmber)
                        else -> Triple(
                            "in $daysUntil Tagen",
                            colors.primaryContainer,
                            colors.onPrimaryContainer
                        )
                    }
                    Surface(
                        color = pillBg,
                        shape = RoundedCornerShape(HiUniRadii.pill)
                    ) {
                        Text(
                            text = pillLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = pillFg,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty-State
// ---------------------------------------------------------------------------

@Composable
private fun EmptyState(
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AssignmentLate,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Noch keine Klausuren",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Sobald deine LSF-POS-Anmeldungen synchronisiert sind, landen die Termine hier. Falls du eingeloggt bist und trotzdem nichts kommt: jetzt synchronisieren.",
            style = MaterialTheme.typography.bodyMedium,
            color = semantics.onSurfaceMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        TextButton(
            onClick = onRefresh,
            enabled = !isRefreshing
        ) {
            Text(if (isRefreshing) "Synchronisiere…" else "Jetzt synchronisieren")
        }
    }
}

// ---------------------------------------------------------------------------
// Formatting-Helpers
// ---------------------------------------------------------------------------

private val HeroDateFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)
private val RowDateFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d. MMM yyyy", Locale.GERMAN)

private fun formatHeroDate(exam: ExamEntity): String {
    val date = exam.examDate ?: return "Termin steht aus"
    val dateLabel = date.format(HeroDateFmt)
    val time = exam.examTime?.let { "%02d:%02d Uhr".format(it.hour, it.minute) }
    return if (time != null) "$dateLabel · $time" else dateLabel
}

private fun formatRowSubline(exam: ExamEntity): String {
    val date = exam.examDate?.format(RowDateFmt)
    val time = exam.examTime?.let { "%02d:%02d Uhr".format(it.hour, it.minute) }
    val room = exam.rooms.firstOrNull()
    val parts = listOfNotNull(date, time, room)
    return if (parts.isEmpty()) exam.semester else parts.joinToString(" · ")
}
