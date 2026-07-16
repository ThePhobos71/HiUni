package de.transio.hiuni.feature.todos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.calendar.ui.courseColorFor
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.todos.data.TodoEntity
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Zone: ZoneId = ZoneId.systemDefault()
private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", Locale.GERMAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTodoSheet(
    initial: TodoEntity?,
    courses: List<CourseEntity>,
    onDismiss: () -> Unit,
    onSave: (id: Long, title: String, dueDate: LocalDate?, courseId: String?) -> Unit,
    onDelete: ((TodoEntity) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var dueDate by remember { mutableStateOf(initial?.dueDate) }
    var courseId by remember { mutableStateOf(initial?.courseId) }
    var pickerOpen by remember { mutableStateOf(false) }
    var coursePickerOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val selectedCourse = courseId?.let { id -> courses.firstOrNull { it.id == id } }

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
                    text = if (initial == null) "Neue Aufgabe" else "Aufgabe bearbeiten",
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

            Spacer(Modifier.height(16.dp))
            Text("Fällig", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { pickerOpen = true },
                    label = {
                        Text(dueDate?.format(dateFormatter) ?: "Kein Datum")
                    }
                )
                if (dueDate != null) {
                    AssistChip(
                        onClick = { dueDate = null },
                        label = { Text("Entfernen") }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Kurs", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { coursePickerOpen = true },
                    label = {
                        Text(selectedCourse?.let { courseShortLabel(it) } ?: "Kein Kurs")
                    },
                    enabled = courses.isNotEmpty()
                )
                if (courseId != null) {
                    AssistChip(
                        onClick = { courseId = null },
                        label = { Text("Entfernen") }
                    )
                }
            }
            if (courses.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Noch keine Kurse importiert.",
                    style = MaterialTheme.typography.labelMedium,
                    color = HiUniColors.semantics.onSurfaceMuted
                )
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
                    onClick = { onSave(initial?.id ?: 0L, title, dueDate, courseId) },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (initial == null) "Anlegen" else "Speichern",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (coursePickerOpen) {
        CoursePickerSheet(
            courses = courses,
            selectedId = courseId,
            onDismiss = { coursePickerOpen = false },
            onPick = {
                courseId = it
                coursePickerOpen = false
            }
        )
    }

    if (pickerOpen) {
        DatePickerSheet(
            initial = dueDate ?: LocalDate.now(),
            onDismiss = { pickerOpen = false },
            onPick = {
                dueDate = it
                pickerOpen = false
            }
        )
    }

    if (confirmDelete && initial != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Aufgabe löschen?") },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoursePickerSheet(
    courses: List<CourseEntity>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    // Aktuelles Semester zuerst (lexikografisch absteigend — Format "WS 2026/27" sortiert sich
    // halbwegs ok, im Zweifel ist die User-Auswahl entscheidend), innerhalb des Semesters
    // alphabetisch nach Modulkürzel/Name.
    val grouped = remember(courses) {
        // Semester-Sortierung: erst nach Jahr absteigend, innerhalb desselben Jahres WS vor SS
        // (WS 2026/27 ist neuer als SS 2026). Reine lex-Sortierung liefert das nicht — "S" < "W"
        // würde sonst SS vor WS schieben.
        courses
            .sortedWith(
                compareByDescending<CourseEntity> { semesterYear(it.semester) }
                    .thenByDescending { semesterKindOrder(it.semester) }
                    .thenBy { it.name.lowercase(Locale.GERMAN) }
            )
            .groupBy { it.semester }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = HiUniRadii.big, topEnd = HiUniRadii.big)
    ) {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
            Text(
                text = "Kurs wählen",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(12.dp))
            CourseRow(
                label = "Kein Kurs",
                hint = "Aufgabe bleibt unabhängig",
                accent = semantics.onSurfaceMuted,
                selected = selectedId == null,
                onClick = { onPick(null) }
            )
            LazyColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                grouped.forEach { (semester, list) ->
                    item(key = "header-$semester") {
                        Text(
                            text = semester.uppercase(Locale.GERMAN),
                            style = MaterialTheme.typography.labelSmall,
                            color = semantics.onSurfaceMuted,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(list, key = { it.id }) { course ->
                        val color = courseColorFor(course)
                        CourseRow(
                            label = course.name,
                            hint = listOfNotNull(
                                course.moduleAbbreviation?.takeIf {
                                    it.isNotBlank() && !it.equals(course.name, ignoreCase = true)
                                },
                                course.professor.takeIf { it.isNotBlank() },
                                course.courseType?.takeIf { it.isNotBlank() }
                            ).joinToString(" · ").ifBlank { null },
                            accent = color.dot,
                            selected = course.id == selectedId,
                            onClick = { onPick(course.id) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    LaunchedEffect(Unit) { sheetState.show() }
}

@Composable
private fun CourseRow(
    label: String,
    hint: String?,
    accent: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = if (selected) colors.primaryContainer else colors.surface,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClickLabel = "Kurs auswählen", onClick = onClick)
                .semantics { role = Role.Button }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) colors.primary else colors.onSurface,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
                if (!hint.isNullOrBlank()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelMedium,
                        color = semantics.onSurfaceMuted
                    )
                }
            }
        }
    }
}

internal fun courseShortLabel(course: CourseEntity): String =
    course.moduleAbbreviation?.takeIf { it.isNotBlank() } ?: course.name

/**
 * Extrahiert die Jahreszahl aus "WS 2026/27" oder "SS 2026". Beide Varianten enthalten
 * das Start-Jahr als erste 4-stellige Zahl; das reicht für die Sortierung.
 */
private fun semesterYear(semester: String): Int =
    Regex("""\d{4}""").find(semester)?.value?.toIntOrNull() ?: 0

/**
 * 1 für Wintersemester, 0 für Sommersemester — damit innerhalb des gleichen Start-Jahres
 * WS vor SS rangiert (WS 2026/27 ist neuer als SS 2026).
 */
private fun semesterKindOrder(semester: String): Int =
    if (semester.trim().startsWith("WS", ignoreCase = true)) 1 else 0

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
