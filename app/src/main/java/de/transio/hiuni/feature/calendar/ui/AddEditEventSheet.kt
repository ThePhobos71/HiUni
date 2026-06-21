package de.transio.hiuni.feature.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.common.DateTimeUtils
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import kotlinx.coroutines.launch
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
        reminderMinutesBefore: Int?
    ) -> Unit,
    onDelete: ((CustomEventEntity) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val initialStart = initial?.startTime?.atZone(Zone)?.toLocalDateTime()
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

    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = HiUniRadii.big, topEnd = HiUniRadii.big)
    ) {
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

            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
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
                        onSave(initial?.id ?: 0L, title, description, location, start, end, rem)
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

    LaunchedEffect(Unit) { sheetState.show() }

    // Suppress unused warning for DateTimeUtils (kept for future use).
    @Suppress("UNUSED_EXPRESSION") DateTimeUtils
}

private enum class PickerTarget { StartDate, EndDate, StartTime, EndTime }

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
