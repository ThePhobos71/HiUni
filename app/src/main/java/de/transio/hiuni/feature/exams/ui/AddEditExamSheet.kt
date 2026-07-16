package de.transio.hiuni.feature.exams.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import de.transio.hiuni.feature.lsf.data.ExamEntity
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId

/**
 * Add/Edit-Sheet für manuell erfasste Klausuren. Am Stil von
 * [de.transio.hiuni.feature.courses.ui.EditCourseSheet] orientiert (ModalBottomSheet,
 * OutlinedTextField, AssistChip für Datum). Erlaubt:
 *
 *  - Titel (Pflicht) — landet als `moduleName`.
 *  - Optionale Kurs-Auswahl aus vorhandenen Kursen — setzt `courseId` und, solange
 *    der User den Titel nicht selbst überschrieben hat, auch den Titel.
 *  - Datum + Uhrzeit (beide optional; Countdown/Timeline rendern „Termin steht aus"
 *    wenn kein Datum gesetzt ist).
 *  - Ort (optional) — als einzelnes Element in `rooms`.
 *
 * Speichern ist nur mit nicht-leerem Titel möglich.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditExamSheet(
    initial: ExamEntity,
    courses: List<CourseEntity>,
    onDismiss: () -> Unit,
    onSave: (ExamEntity) -> Unit,
    onDelete: ((ExamEntity) -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val isEdit = initial.rowId != 0L

    var title by remember { mutableStateOf(initial.moduleName) }
    var room by remember { mutableStateOf(initial.rooms.firstOrNull().orEmpty()) }
    var courseId by remember { mutableStateOf(initial.courseId) }
    var examDate by remember { mutableStateOf(initial.examDate) }
    var examTime by remember { mutableStateOf(initial.examTime) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var timePickerOpen by remember { mutableStateOf(false) }
    var courseMenuOpen by remember { mutableStateOf(false) }

    val selectedCourse = remember(courseId, courses) {
        courses.firstOrNull { it.id == courseId }
    }

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
                text = if (isEdit) "Klausur bearbeiten" else "Klausur eintragen",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titel") },
                placeholder = { Text("z.B. Datenbanksysteme 2") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Optionale Kurs-Auswahl. Wählt der User einen Kurs, wird der Titel
            // übernommen, sofern er noch leer / gleich dem alten Kursnamen war.
            if (courses.isNotEmpty()) {
                Box {
                    AssistChip(
                        onClick = { courseMenuOpen = true },
                        label = {
                            Text(selectedCourse?.name ?: "Kurs zuordnen (optional)")
                        }
                    )
                    DropdownMenu(
                        expanded = courseMenuOpen,
                        onDismissRequest = { courseMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Kein Kurs") },
                            onClick = {
                                courseId = null
                                courseMenuOpen = false
                            }
                        )
                        courses.forEach { course ->
                            DropdownMenuItem(
                                text = { Text(course.name) },
                                onClick = {
                                    courseId = course.id
                                    if (title.isBlank()) title = course.name
                                    courseMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Datum",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = { datePickerOpen = true },
                    label = {
                        Text(examDate?.format(DateTimeFormats.dateWithYear) ?: "wählen")
                    }
                )
                if (examDate != null) {
                    TextButton(onClick = { examDate = null }) { Text("Löschen") }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Uhrzeit",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = { timePickerOpen = true },
                    label = {
                        Text(examTime?.format(DateTimeFormats.time24) ?: "wählen")
                    }
                )
                if (examTime != null) {
                    TextButton(onClick = { examTime = null }) { Text("Löschen") }
                }
            }

            OutlinedTextField(
                value = room,
                onValueChange = { room = it },
                label = { Text("Ort (optional)") },
                placeholder = { Text("z.B. SC.A.0.09") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Abbrechen") }
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val cleanTitle = title.trim()
                        val updated = initial.copy(
                            moduleName = cleanTitle,
                            pruefungstext = cleanTitle,
                            examDate = examDate,
                            examTime = examTime,
                            rooms = room.trim().takeIf { it.isNotBlank() }?.let { listOf(it) }
                                ?: emptyList(),
                            courseId = courseId
                        )
                        onSave(updated)
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Speichern") }
            }

            if (isEdit && onDelete != null) {
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDelete(initial)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Klausur löschen",
                        color = MaterialTheme.colorScheme.error
                    )
                }
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

    if (timePickerOpen) {
        val base = examTime ?: LocalTime.of(9, 0)
        val timeState = rememberTimePickerState(
            initialHour = base.hour,
            initialMinute = base.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { timePickerOpen = false },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    examTime = LocalTime.of(timeState.hour, timeState.minute)
                    timePickerOpen = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { timePickerOpen = false }) { Text("Abbrechen") }
            }
        )
    }
}
