package de.transio.hiuni.feature.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.transio.hiuni.core.common.DateTimeUtils
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.calendar.data.RecurrenceRule
import de.transio.hiuni.ui.responsive.LocalWindowSizeClass
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Zone: ZoneId = ZoneId.systemDefault()
private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", Locale.GERMAN)
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
private val ReminderOptions = listOf(0, 5, 10, 15, 30, 60, 120)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEventSheet(
    initial: CustomEventEntity?,
    defaultReminderMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        title: String,
        description: String?,
        location: String?,
        start: Instant,
        end: Instant,
        reminderMinutesBefore: Int?,
        recurrenceRule: String?
    ) -> Unit,
    onDelete: ((CustomEventEntity) -> Unit)? = null,
    initialDate: LocalDate? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val initialStart = initial?.startTime?.atZone(Zone)?.toLocalDateTime()
        ?: initialDate?.let { date ->
            val now = LocalDateTime.now()
            val isToday = date == now.toLocalDate()
            val time = if (isToday) {
                now.toLocalTime().plusHours(1).withMinute(0).withSecond(0).withNano(0)
            } else {
                LocalTime.of(9, 0)
            }
            date.atTime(time)
        }
        ?: LocalDateTime.now().plusHours(1).withMinute(0)
    val initialEnd = initial?.endTime?.atZone(Zone)?.toLocalDateTime()
        ?: initialStart.plusHours(1)

    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var location by remember { mutableStateOf(initial?.location.orEmpty()) }
    var startDate by remember { mutableStateOf(initialStart.toLocalDate()) }
    var startTime by remember { mutableStateOf(initialStart.toLocalTime().withSecond(0).withNano(0)) }
    var endDate by remember { mutableStateOf(initialEnd.toLocalDate()) }
    var endTime by remember { mutableStateOf(initialEnd.toLocalTime().withSecond(0).withNano(0)) }
    var reminderMinutes by remember {
        mutableStateOf(initial?.reminderMinutesBefore ?: defaultReminderMinutes)
    }
    var confirmDelete by remember { mutableStateOf(false) }

    // Wiederholungs-Sektion. Parsed aus initial.recurrenceRule (JSON) oder Defaults.
    val initialRule = remember(initial) { RecurrenceRule.fromJsonString(initial?.recurrenceRule) }
    var recurrenceFreq by remember { mutableStateOf(initialRule?.freq) }
    // byDays-Default: Master-Wochentag (oder eingestellter startDate-Wochentag).
    var recurrenceByDays by remember(initialRule, startDate) {
        mutableStateOf(
            initialRule?.byDays?.toSet()
                ?: setOf(startDate.dayOfWeek)
        )
    }
    var recurrenceInterval by remember { mutableStateOf(initialRule?.interval ?: 1) }
    var recurrenceUntil by remember { mutableStateOf<LocalDate?>(initialRule?.until) }

    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }

    // Auf Tablet-Landscape (Expanded) erscheint das Formular als zentrierter
    // Dialog — der volle ModalBottomSheet würde sonst über die ganze Breite
    // schmieren. Compact/Medium bleiben beim Sheet.
    val isExpanded = LocalWindowSizeClass.current?.widthSizeClass == WindowWidthSizeClass.Expanded

    val formContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initial == null) "Neues Event" else "Event bearbeiten",
                    style = MaterialTheme.typography.headlineMedium
                )
                if (initial != null && onDelete != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Löschen",
                            tint = HiUniColors.semantics.red
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titel") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Beschreibung (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Ort (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Text("Start", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { pickerTarget = PickerTarget.StartDate },
                    label = { Text(startDate.format(dateFormatter)) }
                )
                AssistChip(
                    onClick = { pickerTarget = PickerTarget.StartTime },
                    label = { Text(startTime.format(timeFormatter)) }
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Ende", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { pickerTarget = PickerTarget.EndDate },
                    label = { Text(endDate.format(dateFormatter)) }
                )
                AssistChip(
                    onClick = { pickerTarget = PickerTarget.EndTime },
                    label = { Text(endTime.format(timeFormatter)) }
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("Erinnerung", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ReminderOptions.forEach { minutes ->
                    FilterChip(
                        selected = reminderMinutes == minutes,
                        onClick = { reminderMinutes = minutes },
                        label = {
                            Text(
                                text = when (minutes) {
                                    0 -> "Aus"
                                    in 1..59 -> "$minutes Min"
                                    60 -> "1 Std"
                                    else -> "${minutes / 60} Std"
                                }
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Wiederholung", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            RecurrenceFreqRow(
                selected = recurrenceFreq,
                onSelect = { recurrenceFreq = it }
            )

            // WEEKLY: Wochentags-Toggles
            if (recurrenceFreq == RecurrenceRule.Freq.WEEKLY) {
                Spacer(Modifier.height(10.dp))
                Text("Wochentage", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                WeekdayPills(
                    selected = recurrenceByDays,
                    onToggle = { day ->
                        recurrenceByDays = if (day in recurrenceByDays) {
                            (recurrenceByDays - day).ifEmpty { setOf(day) }
                        } else {
                            recurrenceByDays + day
                        }
                    }
                )
            }

            // End-Datum-Picker (alle freq außer null)
            if (recurrenceFreq != null) {
                Spacer(Modifier.height(10.dp))
                Text("Endet am", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { pickerTarget = PickerTarget.RecurrenceUntil },
                        label = {
                            Text(recurrenceUntil?.format(dateFormatter) ?: "Kein Ende")
                        }
                    )
                    if (recurrenceUntil != null) {
                        TextButton(onClick = { recurrenceUntil = null }) {
                            Text("Zurücksetzen")
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = {
                        if (isExpanded) {
                            // Dialog-Modus: kein Sheet-State zu hiden.
                            onDismiss()
                        } else {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Abbrechen")
                }
                Button(
                    onClick = {
                        val start = LocalDateTime.of(startDate, startTime).atZone(Zone).toInstant()
                        val rawEnd = LocalDateTime.of(endDate, endTime).atZone(Zone).toInstant()
                        val end = if (rawEnd.isAfter(start)) rawEnd else start.plusSeconds(3600)
                        val rem = reminderMinutes.takeIf { it > 0 }
                        val rule = recurrenceFreq?.let { freq ->
                            RecurrenceRule(
                                freq = freq,
                                interval = recurrenceInterval.coerceAtLeast(1),
                                byDays = if (freq == RecurrenceRule.Freq.WEEKLY) {
                                    recurrenceByDays.toList().ifEmpty { null }
                                } else null,
                                until = recurrenceUntil
                            ).toJsonString()
                        }
                        onSave(initial?.id ?: 0L, title, description, location, start, end, rem, rule)
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (initial == null) "Anlegen" else "Speichern", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (isExpanded) {
        // Tablet/Landscape: Material-3-Dialog mit Surface, max ~560dp Breite.
        // Scrollbar, falls Recurrence-/Reminder-Sektionen den verfügbaren
        // Höhenrahmen sprengen.
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            // usePlatformDefaultWidth=false → wir zentrieren die Surface selbst
            // in einem Box, sonst klebt sie oben links am Dialog-Window.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(HiUniRadii.big),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .widthIn(min = 360.dp, max = 560.dp)
                        .heightIn(max = 720.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        formContent()
                    }
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = HiUniRadii.big, topEnd = HiUniRadii.big)
        ) {
            formContent()
        }
    }

    when (val target = pickerTarget) {
        PickerTarget.StartDate -> DatePickerSheet(
            initial = startDate,
            onDismiss = { pickerTarget = null },
            onPick = {
                startDate = it
                if (endDate.isBefore(it)) endDate = it
                pickerTarget = null
            }
        )
        PickerTarget.EndDate -> DatePickerSheet(
            initial = endDate,
            onDismiss = { pickerTarget = null },
            onPick = { endDate = it; pickerTarget = null }
        )
        PickerTarget.StartTime -> TimePickerSheet(
            initial = startTime,
            onDismiss = { pickerTarget = null },
            onPick = { startTime = it; pickerTarget = null }
        )
        PickerTarget.EndTime -> TimePickerSheet(
            initial = endTime,
            onDismiss = { pickerTarget = null },
            onPick = { endTime = it; pickerTarget = null }
        )
        PickerTarget.RecurrenceUntil -> DatePickerSheet(
            initial = recurrenceUntil ?: startDate.plusMonths(3),
            onDismiss = { pickerTarget = null },
            onPick = { recurrenceUntil = it; pickerTarget = null }
        )
        null -> Unit
    }

    if (confirmDelete && initial != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Event löschen?") },
            text = { Text("\"${initial.title}\" wird unwiderruflich entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(initial)
                    onDismiss()
                }) {
                    Text("Löschen", color = HiUniColors.semantics.red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            }
        )
    }

    if (!isExpanded) {
        LaunchedEffect(Unit) { sheetState.show() }
    }

    // Suppress unused warning for DateTimeUtils (kept for future use).
    @Suppress("UNUSED_EXPRESSION") DateTimeUtils
}

private enum class PickerTarget { StartDate, EndDate, StartTime, EndTime, RecurrenceUntil }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(Zone).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(Instant.ofEpochMilli(it).atZone(Zone).toLocalDate())
                } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPick: (LocalTime) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uhrzeit wählen") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/* ──────────────────────────────────────────────────────────────────
 * Wiederholungs-UI: Frequenz-Chips + Wochentags-Pillen
 * ────────────────────────────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurrenceFreqRow(
    selected: RecurrenceRule.Freq?,
    onSelect: (RecurrenceRule.Freq?) -> Unit
) {
    val options: List<Pair<RecurrenceRule.Freq?, String>> = listOf(
        null to "Einmalig",
        RecurrenceRule.Freq.DAILY to "Täglich",
        RecurrenceRule.Freq.WEEKLY to "Wöchentlich",
        RecurrenceRule.Freq.MONTHLY to "Monatlich"
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.forEach { (freq, label) ->
            FilterChip(
                selected = selected == freq,
                onClick = { onSelect(freq) },
                label = { Text(label) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekdayPills(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit
) {
    // Hand-styled Pillen statt FilterChip — passend zum HiUni-Look (siehe
    // memory: "prefer hand-styled Surface+Text pills over stock M3 components").
    val days = listOf(
        DayOfWeek.MONDAY to "Mo",
        DayOfWeek.TUESDAY to "Di",
        DayOfWeek.WEDNESDAY to "Mi",
        DayOfWeek.THURSDAY to "Do",
        DayOfWeek.FRIDAY to "Fr",
        DayOfWeek.SATURDAY to "Sa",
        DayOfWeek.SUNDAY to "So"
    )
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        days.forEach { (day, label) ->
            val isSelected = day in selected
            androidx.compose.material3.Surface(
                onClick = { onToggle(day) },
                shape = RoundedCornerShape(HiUniRadii.pill),
                color = if (isSelected) colors.primary else semantics.surfaceAlt,
                contentColor = if (isSelected) colors.onPrimary else colors.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        role = Role.Checkbox
                        this.selected = isSelected
                    }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
