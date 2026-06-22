package de.transio.hiuni.feature.courses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.courses.CoursesViewModel
import de.transio.hiuni.feature.courses.data.CourseEntity
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val examDateFmt = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)

@Composable
fun CoursesScreen(viewModel: CoursesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected = state.selectedCourse

    if (selected != null) {
        CourseDetail(
            course = selected,
            onBack = { viewModel.select(null) },
            onEdit = { viewModel.startEdit(selected) },
            onDelete = { viewModel.delete(selected.id) }
        )
    } else {
        CoursesList(
            state = state,
            onSelect = { viewModel.select(it.id) },
            onAdd = { viewModel.startAdd() }
        )
    }

    if (state.showAddSheet) {
        AddEditCourseSheet(
            initial = state.editing,
            onDismiss = viewModel::dismissSheet,
            onSave = viewModel::save
        )
    }
}

@Composable
private fun CoursesList(
    state: de.transio.hiuni.feature.courses.CoursesUiState,
    onSelect: (CourseEntity) -> Unit,
    onAdd: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = colors.primary,
                contentColor = colors.onPrimary
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Kurs anlegen")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 16.dp)
            ) {
                Text(
                    text = "Meine Kurse",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.onSurface
                )
                if (state.courses.isNotEmpty()) {
                    Text(
                        text = "${state.courses.size} Modul${if (state.courses.size == 1) "" else "e"} · " +
                            "${state.totalCredits} LP gesamt",
                        style = MaterialTheme.typography.bodyMedium,
                        color = semantics.onSurfaceMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (state.courses.isEmpty()) {
                EmptyState(onAdd = onAdd)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.courses, key = { it.id }) { course ->
                        CourseRow(course = course, onClick = { onSelect(course) })
                    }
                    item { StatsCard(state = state) }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = semantics.surfaceAlt,
            shape = RoundedCornerShape(HiUniRadii.card),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.School,
                    contentDescription = null,
                    tint = semantics.onSurfaceMuted
                )
                Text(
                    text = "Noch keine Kurse angelegt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted
                )
                Button(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Kurs anlegen")
                }
            }
        }
    }
}

@Composable
private fun CourseRow(course: CourseEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val accent = courseAccent(course.id)
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile))
                    .background(accent.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MenuBook,
                    contentDescription = null,
                    tint = accent.base
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1
                )
                if (course.professor.isNotBlank()) {
                    Text(
                        text = course.professor,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(8.dp))
                ProgressBar(progress = course.progress, color = accent.base, height = 4.dp)
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${course.credits} LP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = semantics.onSurfaceMuted
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = semantics.onSurfaceMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, color: Color, height: androidx.compose.ui.unit.Dp) {
    val semantics = HiUniColors.semantics
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(semantics.surfaceAlt)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

@Composable
private fun StatsCard(state: de.transio.hiuni.feature.courses.CoursesUiState) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.primaryContainer,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            StatItem(label = "Kurse", value = state.courses.size.toString())
            StatItem(label = "LP gesamt", value = state.totalCredits.toString())
            StatItem(label = "Semester", value = state.semestersSeen.coerceAtLeast(1).toString())
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.primary.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CourseDetail(
    course: CourseEntity,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val accent = courseAccent(course.id)
    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DetailHeader(
                course = course,
                accent = accent,
                onBack = onBack,
                onEdit = onEdit,
                onDelete = onDelete
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoRow(label = "Leistungspunkte", value = "${course.credits} LP")
                InfoRow(label = "Semester", value = course.semester)
                InfoRow(
                    label = "Nächste Prüfung",
                    value = course.nextExamDate?.format(examDateFmt) ?: "–"
                )
                ProgressCard(course = course, accent = accent.base)
                GradeStatusCard(course = course, accent = accent)
                if (!course.notes.isNullOrBlank()) {
                    NotesCard(notes = course.notes)
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(
    course: CourseEntity,
    accent: CourseAccent,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.surface)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Zurück",
                    tint = accent.base
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Bearbeiten",
                        tint = accent.base
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Löschen",
                        tint = accent.base
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onSurface
            )
            if (course.professor.isNotBlank()) {
                Text(
                    text = course.professor,
                    style = MaterialTheme.typography.bodyLarge,
                    color = accent.base,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = semantics.onSurfaceMuted
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
        }
    }
}

@Composable
private fun ProgressCard(course: CourseEntity, accent: Color) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "KURSFORTSCHRITT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = semantics.onSurfaceMuted
            )
            Spacer(Modifier.height(10.dp))
            ProgressBar(progress = course.progress, color = accent, height = 8.dp)
            Spacer(Modifier.height(8.dp))
            val pct = (course.progress * 100).roundToInt()
            val sessionSuffix = if (course.totalSessions > 0) {
                " (${course.attendedSessions}/${course.totalSessions})"
            } else ""
            Text(
                text = "$pct % der Vorlesungen besucht$sessionSuffix",
                style = MaterialTheme.typography.bodySmall,
                color = semantics.onSurfaceMuted
            )
        }
    }
}

@Composable
private fun GradeStatusCard(course: CourseEntity, accent: CourseAccent) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val hasGrade = !course.grade.isNullOrBlank()
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "NOTENSTATUS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = semantics.onSurfaceMuted
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(HiUniRadii.tile))
                        .background(accent.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = accent.base
                    )
                }
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(
                        text = if (hasGrade) "Note: ${course.grade}" else "Note steht noch aus",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    val subline = course.nextExamDate?.let {
                        "Endklausur am ${it.format(examDateFmt)}"
                    } ?: "Bewertung am Semesterende"
                    Text(
                        text = subline,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesCard(notes: String) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "NOTIZEN",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = semantics.onSurfaceMuted
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface
            )
        }
    }
}
