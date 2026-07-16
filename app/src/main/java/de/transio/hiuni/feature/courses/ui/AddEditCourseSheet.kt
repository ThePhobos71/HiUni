package de.transio.hiuni.feature.courses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.common.DateTimeFormats
import de.transio.hiuni.feature.courses.data.CourseEntity
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Edit-Sheet für die persönlichen Tracking-Felder eines Kurses. Stammdaten (Name,
 * Dozent, LP, Semester, Raum) kommen vom LSF und sind hier nicht editierbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCourseSheet(
    initial: CourseEntity,
    onDismiss: () -> Unit,
    onSave: (CourseEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var attendedRaw by remember { mutableStateOf(initial.attendedSessions.toString()) }
    var totalRaw by remember { mutableStateOf(initial.totalSessions.toString()) }
    var grade by remember { mutableStateOf(initial.grade.orEmpty()) }
    var notes by remember { mutableStateOf(initial.notes.orEmpty()) }
    var examDate by remember { mutableStateOf(initial.nextExamDate) }
    var datePickerOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Kurs aktualisieren",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = initial.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = attendedRaw,
                    onValueChange = { attendedRaw = it.filter(Char::isDigit).take(3) },
                    label = { Text("Besucht") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = totalRaw,
                    onValueChange = { totalRaw = it.filter(Char::isDigit).take(3) },
                    label = { Text("Gesamt") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = grade,
                onValueChange = { grade = it.take(4) },
                label = { Text("Note (optional)") },
                placeholder = { Text("z.B. 1.7") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Nächste Prüfung",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = { datePickerOpen = true },
                    label = {
                        Text(examDate?.format(DateTimeFormats.dateWithYear) ?: "Datum wählen")
                    }
                )
                if (examDate != null) {
                    TextButton(onClick = { examDate = null }) { Text("Löschen") }
                }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notizen") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                    modifier = Modifier.weight(1f)
                ) { Text("Abbrechen") }
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val updated = initial.copy(
                            attendedSessions = attendedRaw.toIntOrNull() ?: 0,
                            totalSessions = totalRaw.toIntOrNull() ?: 0,
                            grade = grade.trim().takeIf { it.isNotBlank() },
                            notes = notes.trim().takeIf { it.isNotBlank() },
                            nextExamDate = examDate
                        )
                        onSave(updated)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Speichern") }
            }
        }
    }

    if (datePickerOpen) {
        val zone = ZoneId.systemDefault()
        val initialMillis = examDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        examDate = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
                    }
                    datePickerOpen = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("Abbrechen") }
            }
        ) { DatePicker(state = pickerState) }
    }
}

