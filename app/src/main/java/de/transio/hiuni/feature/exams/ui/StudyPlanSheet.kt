package de.transio.hiuni.feature.exams.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.common.DateTimeFormats
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.exams.studyplan.ExamPlanIssue
import de.transio.hiuni.feature.exams.studyplan.ExamPlanSummary
import de.transio.hiuni.feature.exams.studyplan.StudyPlanFormat
import de.transio.hiuni.feature.exams.studyplan.StudyPlanStart
import de.transio.hiuni.feature.exams.studyplan.StudyPlanUiState
import de.transio.hiuni.feature.exams.studyplan.StudyPlanViewModel
import de.transio.hiuni.feature.settings.ui.ChipRow
import de.transio.hiuni.feature.settings.ui.formatReminderLabel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/**
 * Sheet des Lernplan-Generators: oben die Live-Vorschau ("12 Lerneinheiten,
 * 18 Std, bis Fr 13. Nov" plus kompakte Zeile pro Klausur), darunter Auswahl und
 * Einstellungen als handgestylte Pills (wie in den Einstellungen), unten
 * "Übernehmen" und, falls schon ein Plan eingetragen ist, "Lernplan löschen".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudyPlanSheet(
    state: StudyPlanUiState,
    viewModel: StudyPlanViewModel
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val settings = state.settings
    var datePickerOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = viewModel::dismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Lernplan erstellen",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Verteilt Lerneinheiten auf die freien Lücken in deinem Kalender, " +
                    "gewichtet nach LP und Zeit bis zur Klausur.",
                style = MaterialTheme.typography.bodyMedium,
                color = semantics.onSurfaceMuted
            )

            PreviewCard(state)

            FieldLabel("Klausuren")
            if (state.candidates.isEmpty()) {
                Text(
                    text = "Keine anstehende Klausur mit Datum.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    state.candidates.forEach { candidate ->
                        TogglePill(
                            label = "${candidate.exam.title} · " +
                                candidate.exam.examDate.format(DateTimeFormats.dateShort),
                            active = candidate.selected,
                            onClick = { viewModel.toggleExam(candidate.exam.examId) }
                        )
                    }
                }
            }

            FieldLabel("Start")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TogglePill("Heute", state.start == StudyPlanStart.TODAY) {
                    viewModel.setStart(StudyPlanStart.TODAY)
                }
                TogglePill("Morgen", state.start == StudyPlanStart.TOMORROW) {
                    viewModel.setStart(StudyPlanStart.TOMORROW)
                }
                val customLabel = if (state.start == StudyPlanStart.CUSTOM) {
                    state.startDate?.format(DateTimeFormats.dayShortNoComma) ?: "Datum wählen"
                } else {
                    "Datum wählen"
                }
                TogglePill(customLabel, state.start == StudyPlanStart.CUSTOM) { datePickerOpen = true }
            }

            FieldLabel("Lernzeit von")
            ChipRow(
                options = listOf(7, 8, 9, 10),
                selected = settings.windowStart.hour,
                label = { "$it Uhr" },
                onSelect = { hour -> viewModel.updateSettings { it.copy(windowStart = LocalTime.of(hour, 0)) } }
            )
            FieldLabel("Lernzeit bis")
            ChipRow(
                options = listOf(17, 18, 20, 22),
                selected = settings.windowEnd.hour,
                label = { "$it Uhr" },
                onSelect = { hour -> viewModel.updateSettings { it.copy(windowEnd = LocalTime.of(hour, 0)) } }
            )

            FieldLabel("Höchstens pro Tag")
            ChipRow(
                options = listOf(120, 180, 240, 360),
                selected = settings.maxMinutesPerDay,
                label = { StudyPlanFormat.hours(it.toLong()) },
                onSelect = { minutes -> viewModel.updateSettings { it.copy(maxMinutesPerDay = minutes) } }
            )

            FieldLabel("Blocklänge")
            ChipRow(
                options = listOf(45, 60, 90, 120),
                selected = settings.blockMinutes,
                label = { "$it Min" },
                onSelect = { minutes -> viewModel.updateSettings { it.copy(blockMinutes = minutes) } }
            )

            FieldLabel("Puffer zu Terminen")
            ChipRow(
                options = listOf(0, 15, 30),
                selected = settings.bufferMinutes,
                label = { if (it == 0) "Keiner" else "$it Min" },
                onSelect = { minutes -> viewModel.updateSettings { it.copy(bufferMinutes = minutes) } }
            )

            FieldLabel("Lernumfang pro LP")
            ChipRow(
                options = listOf(2, 3, 4, 5),
                selected = settings.hoursPerCredit,
                label = { "$it Std" },
                onSelect = { hours -> viewModel.updateSettings { it.copy(hoursPerCredit = hours) } }
            )

            FieldLabel("Freie Tage")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DayOfWeek.entries.forEach { day ->
                    val active = day in settings.freeDays
                    TogglePill(day.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.GERMAN), active) {
                        viewModel.updateSettings {
                            it.copy(freeDays = if (active) it.freeDays - day else it.freeDays + day)
                        }
                    }
                }
            }

            FieldLabel("Erinnerung vor jeder Einheit")
            ChipRow(
                options = listOf(0, 5, 15, 30),
                selected = settings.reminderMinutesBefore ?: 0,
                label = ::formatReminderLabel,
                onSelect = { minutes ->
                    viewModel.updateSettings { it.copy(reminderMinutesBefore = minutes.takeIf { m -> m > 0 }) }
                }
            )

            Text(
                text = "Künftige Lerneinheiten aus einem früheren Plan werden ersetzt. Vergangene " +
                    "und im Kalender bearbeitete Einheiten bleiben erhalten.",
                style = MaterialTheme.typography.bodySmall,
                color = semantics.onSurfaceMuted
            )

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = viewModel::dismiss, modifier = Modifier.weight(1f)) {
                    Text("Abbrechen")
                }
                Button(
                    onClick = viewModel::apply,
                    enabled = state.canApply,
                    modifier = Modifier.weight(1f)
                ) { Text(if (state.isApplying) "Trage ein…" else "Übernehmen") }
            }
            if (state.hasActivePlan) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Lernplan löschen (${StudyPlanViewModel.unitsLabel(state.activeBlockCount)})",
                        color = colors.error
                    )
                }
            }
        }
    }

    if (datePickerOpen) {
        val initialMillis = (state.startDate ?: LocalDate.now())
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        // Der M3-DatePicker liefert UTC-Mitternacht des gewählten Tages.
                        val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.setStart(StudyPlanStart.CUSTOM, date)
                    }
                    datePickerOpen = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("Abbrechen") }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Lernplan löschen?") },
            text = {
                Text(
                    "${StudyPlanViewModel.unitsLabel(state.activeBlockCount)} werden aus dem Kalender " +
                        "entfernt. Vergangene und bearbeitete Einheiten bleiben."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deletePlan()
                }) { Text("Löschen", color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Vorschau
// ---------------------------------------------------------------------------

@Composable
private fun PreviewCard(state: StudyPlanUiState) {
    val colors = MaterialTheme.colorScheme
    val preview = state.preview
    Surface(
        color = colors.primaryContainer,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Vorschau".uppercase(Locale.GERMAN),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onPrimaryContainer.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold
            )
            when {
                preview == null -> Text(
                    text = "Berechne freie Lücken…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer
                )
                preview.isEmpty -> Text(
                    text = StudyPlanFormat.emptyMessage(preview.emptyReason, state.hasCandidates),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer
                )
                else -> {
                    Text(
                        text = StudyPlanFormat.headline(preview),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer
                    )
                    Text(
                        text = StudyPlanFormat.subline(preview, ZoneId.systemDefault()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onPrimaryContainer
                    )
                }
            }
            // Auch bei leerem Plan zeigen: so sieht man, welche Klausur woran scheitert.
            if (preview != null && preview.exams.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                preview.exams.forEach { ExamPreviewRow(it) }
            }
        }
    }
}

@Composable
private fun ExamPreviewRow(summary: ExamPlanSummary) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${StudyPlanFormat.examLine(summary)} · Klausur " +
                    summary.examDate.format(DateTimeFormats.dateShort),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
        StudyPlanFormat.issueLabel(summary)?.let { label ->
            Spacer(Modifier.width(8.dp))
            val (bg, fg) = when (summary.issue) {
                ExamPlanIssue.PARTIAL -> semantics.amber to semantics.onAmber
                else -> semantics.red to semantics.onRed
            }
            Pill(label, bg, fg)
        }
    }
}

// ---------------------------------------------------------------------------
// Bausteine
// ---------------------------------------------------------------------------

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text.uppercase(Locale.GERMAN),
        style = MaterialTheme.typography.labelMedium,
        color = HiUniColors.semantics.onSurfaceMuted,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** Handgestylte Auswahl-Pill im Stil der [ChipRow]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TogglePill(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = if (active) colors.primary else semantics.surfaceAlt,
        shape = RoundedCornerShape(HiUniRadii.pill),
        onClick = onClick
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) colors.onPrimary else semantics.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(HiUniRadii.pill)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
