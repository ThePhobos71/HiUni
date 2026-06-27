package de.transio.hiuni.feature.courses.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import de.transio.hiuni.ui.responsive.FullWidthContent
import de.transio.hiuni.ui.responsive.LocalWindowSizeClass
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val examDateFmt = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)

@Composable
fun CoursesScreen(
    initialLsfId: String? = null,
    viewModel: CoursesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected = state.selectedCourse

    // Deep-Link aus dem Kalender: wenn `initialLsfId` gesetzt ist, gleich den
    // passenden Kurs auswählen + Semester nachziehen.
    LaunchedEffect(initialLsfId) {
        if (!initialLsfId.isNullOrBlank()) {
            viewModel.selectByLsfId(initialLsfId)
        }
    }

    if (selected != null) {
        // System-Back nicht zum NavGraph weiterreichen — er soll innerhalb des
        // Kurse-Tabs zur Liste zurückspringen, nicht den User aus dem Tab rausnehmen.
        BackHandler(enabled = state.editing == null) { viewModel.select(null) }
        CourseDetail(
            course = selected,
            parent = state.parentOf(selected),
            onBack = { viewModel.select(null) },
            onEdit = { viewModel.startEdit(selected) },
            onOpenParent = { parent -> viewModel.select(parent.id) },
            onNotesChange = { notes -> viewModel.updateNotes(selected.id, notes) }
        )
    } else {
        CoursesList(
            state = state,
            onSelect = { viewModel.select(it.id) },
            onSelectSemester = viewModel::selectSemester
        )
    }

    state.editing?.let { editing ->
        EditCourseSheet(
            initial = editing,
            onDismiss = viewModel::dismissSheet,
            onSave = viewModel::save
        )
    }
}

@Composable
private fun CoursesList(
    state: de.transio.hiuni.feature.courses.CoursesUiState,
    onSelect: (CourseEntity) -> Unit,
    onSelectSemester: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val visible = state.visibleCourses
    val isExpanded = LocalWindowSizeClass.current?.widthSizeClass == WindowWidthSizeClass.Expanded
    // Tablet-Landscape: 2-Spalten-Grid für Kurs-Cards. FullWidthContent
    // entfernt den 1100dp-Cap, damit das Grid voll-breit atmet.
    FullWidthContent {
    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
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
                if (visible.isNotEmpty()) {
                    Text(
                        text = "${visible.size} Modul${if (visible.size == 1) "" else "e"} · " +
                            "${state.totalCredits} LP gesamt",
                        style = MaterialTheme.typography.bodyMedium,
                        color = semantics.onSurfaceMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (state.availableSemesters.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    SemesterChipRow(
                        semesters = state.availableSemesters,
                        selected = state.selectedSemester,
                        onSelect = onSelectSemester
                    )
                }
            }

            if (state.courses.isEmpty()) {
                EmptyState()
            } else if (visible.isEmpty()) {
                EmptySemesterState(state.selectedSemester.orEmpty())
            } else if (isExpanded) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { course ->
                        CourseRow(
                            course = course,
                            isChild = course.parentLsfId != null,
                            onClick = { onSelect(course) }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StatsCard(visible = visible, totalCredits = state.totalCredits)
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(80.dp)) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { course ->
                        CourseRow(
                            course = course,
                            isChild = course.parentLsfId != null,
                            onClick = { onSelect(course) }
                        )
                    }
                    item { StatsCard(visible = visible, totalCredits = state.totalCredits) }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
    } // end FullWidthContent
}

@Composable
private fun SemesterChipRow(
    semesters: List<String>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(semesters, key = { it }) { semester ->
            val isActive = semester == selected
            Surface(
                color = if (isActive) colors.primary else semantics.surfaceAlt,
                shape = RoundedCornerShape(HiUniRadii.pill),
                onClick = { onSelect(semester) }
            ) {
                Text(
                    text = semester,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isActive) colors.onPrimary else semantics.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptySemesterState(semester: String) {
    val semantics = HiUniColors.semantics
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = if (semester.isBlank()) "Keine Kurse in diesem Semester."
            else "Keine Kurse für $semester.",
            style = MaterialTheme.typography.bodyMedium,
            color = semantics.onSurfaceMuted
        )
    }
}

@Composable
private fun EmptyState() {
    val semantics = HiUniColors.semantics
    de.transio.hiuni.core.design.components.EmptyState(
        icon = Icons.Outlined.School,
        iconAccent = semantics.onSurfaceMuted,
        containerColor = semantics.surfaceAlt,
        body = "Noch keine Kurse synchronisiert.",
        secondaryBody = "Öffne die Einstellungen und tippe „Kurse jetzt importieren“, um deine LSF-Veranstaltungen automatisch zu laden."
    )
}

@Composable
private fun CourseRow(course: CourseEntity, isChild: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val accent = courseAccent(course.parentLsfId?.let { "lsf-$it" } ?: course.id)
    // Tutorien werden 16dp eingerückt und kleiner gerendert, damit die Hierarchie
    // zur Mutter-Vorlesung visuell sichtbar ist.
    val rowPaddingStart = if (isChild) 16.dp else 0.dp
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = rowPaddingStart)
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (isChild) 36.dp else 48.dp)
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
                if (course.isTutoriumLike && !course.courseType.isNullOrBlank()) {
                    Text(
                        text = course.courseType.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent.base
                    )
                }
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
private fun StatsCard(visible: List<CourseEntity>, totalCredits: Int) {
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
            StatItem(label = "Kurse", value = visible.size.toString())
            StatItem(label = "LP gesamt", value = totalCredits.toString())
            val abgeschlossen = visible.count { !it.grade.isNullOrBlank() }
            StatItem(label = "Mit Note", value = abgeschlossen.toString())
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
    parent: CourseEntity?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenParent: (CourseEntity) -> Unit,
    onNotesChange: (String) -> Unit
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
                onEdit = onEdit
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                parent?.let { p ->
                    ParentLectureRow(parent = p, onClick = { onOpenParent(p) })
                }
                course.courseType?.takeIf { it.isNotBlank() }?.let { type ->
                    InfoRow(label = "Veranstaltungsart", value = type)
                }
                InfoRow(label = "Leistungspunkte", value = if (course.credits > 0) "${course.credits} LP" else "–")
                course.sws?.takeIf { it > 0 }?.let { sws ->
                    InfoRow(label = "SWS", value = sws.toString())
                }
                InfoRow(label = "Semester", value = course.semester.ifBlank { "–" })
                course.moduleAbbreviation?.takeIf { it.isNotBlank() }?.let { abbr ->
                    InfoRow(label = "Modulkürzel", value = abbr)
                }
                course.room?.takeIf { it.isNotBlank() }?.let { room ->
                    InfoRow(label = "Raum", value = room)
                }
                course.lsfStatus?.takeIf { it.isNotBlank() }?.let { status ->
                    InfoRow(label = "LSF-Status", value = status)
                }
                InfoRow(
                    label = "Nächste Prüfung",
                    value = course.nextExamDate?.format(examDateFmt) ?: "–"
                )
                ProgressCard(course = course, accent = accent.base)
                GradeStatusCard(course = course, accent = accent)
                course.description?.takeIf { it.isNotBlank() }?.let { description ->
                    TextSectionCard(title = "LERNINHALTE", body = description)
                }
                course.remark?.takeIf { it.isNotBlank() }?.let { remark ->
                    TextSectionCard(title = "BEMERKUNG", body = remark)
                }
                course.targetAudience?.takeIf { it.isNotBlank() }?.let { audience ->
                    TextSectionCard(title = "ZIELGRUPPE", body = audience)
                }
                NotesCard(
                    course = course,
                    onChange = onNotesChange
                )
            }
        }
    }
}

@Composable
private fun DetailHeader(
    course: CourseEntity,
    accent: CourseAccent,
    onBack: () -> Unit,
    onEdit: () -> Unit
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
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Bearbeiten",
                    tint = accent.base
                )
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
private fun ParentLectureRow(parent: CourseEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.primaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(HiUniRadii.tile),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GEHÖRT ZU",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
                Text(
                    text = parent.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Zur Vorlesung",
                tint = semantics.onSurfaceMuted
            )
        }
    }
}

@Composable
private fun TextSectionCard(title: String, body: String) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = semantics.onSurfaceMuted
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesCard(
    course: CourseEntity,
    onChange: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    // Lokaler Draft-State, der den DB-Wert mirror't. `remember(course.id)` resettet
    // beim Wechsel auf einen anderen Kurs — sonst würden Notizen von Kurs A in das
    // Feld von Kurs B "schwappen".
    var draft by remember(course.id) { mutableStateOf(course.notes.orEmpty()) }
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
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    onChange(it)
                },
                placeholder = {
                    Text(
                        text = "Eigene Notizen zu diesem Kurs…",
                        color = semantics.onSurfaceMuted
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = colors.outline.copy(alpha = 0.3f)
                ),
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Automatisch gespeichert beim Tippen",
                style = MaterialTheme.typography.labelSmall,
                color = semantics.onSurfaceMuted
            )
        }
    }
}
