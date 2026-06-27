package de.transio.hiuni.feature.todos.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.calendar.ui.courseColorFor
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.todos.TodosViewModel
import de.transio.hiuni.feature.todos.data.TodoEntity
import de.transio.hiuni.ui.responsive.FullWidthContent
import de.transio.hiuni.ui.responsive.LocalWindowSizeClass
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodosScreen(
    viewModel: TodosViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val isExpanded = LocalWindowSizeClass.current?.widthSizeClass == WindowWidthSizeClass.Expanded

    // Tablet-Landscape: 2-Spalten-Grid für Todo-Cards. FullWidthContent hebt
    // den 1100dp-Cap auf, damit das Grid voll-breit atmet.
    FullWidthContent {
    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAdd() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Neue Aufgabe") },
                containerColor = colors.primary,
                contentColor = colors.onPrimary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TodosHeader(openCount = state.openCount, totalCount = state.todos.size)

            if (state.todos.isEmpty()) {
                TodosEmptyState()
            } else if (isExpanded) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = 4.dp,
                        // Platz für den FAB
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = state.todos, key = { it.id }) { todo ->
                        TodoRow(
                            todo = todo,
                            course = todo.courseId?.let { state.coursesById[it] },
                            hasMissingCourse = todo.courseId != null && state.coursesById[todo.courseId] == null,
                            onToggleDone = { viewModel.toggleDone(todo) },
                            onClick = { viewModel.openEdit(todo) },
                            onDelete = { viewModel.delete(todo) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = 4.dp,
                        // Platz für den FAB
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = state.todos, key = { it.id }) { todo ->
                        TodoRow(
                            todo = todo,
                            course = todo.courseId?.let { state.coursesById[it] },
                            hasMissingCourse = todo.courseId != null && state.coursesById[todo.courseId] == null,
                            onToggleDone = { viewModel.toggleDone(todo) },
                            onClick = { viewModel.openEdit(todo) },
                            onDelete = { viewModel.delete(todo) }
                        )
                    }
                }
            }
        }
    }
    } // end FullWidthContent

    if (state.isAddSheetOpen) {
        AddEditTodoSheet(
            initial = state.editing,
            courses = state.courses,
            onDismiss = viewModel::closeSheet,
            onSave = { id, title, dueDate, courseId ->
                scope.launch { viewModel.save(id, title, dueDate, courseId) }
            },
            onDelete = { viewModel.delete(it) }
        )
    }
}

@Composable
private fun TodosHeader(openCount: Int, totalCount: Int) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 16.dp)
    ) {
        Text(
            text = "Aufgaben",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onSurface
        )
        val subtitle = when {
            totalCount == 0 -> "Sammle hier deine Todos rund ums Studium."
            openCount == 0 -> "Alles erledigt — gönn dir."
            openCount == 1 -> "1 offene Aufgabe"
            else -> "$openCount offene Aufgaben"
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = semantics.onSurfaceMuted,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun TodosEmptyState() {
    val semantics = HiUniColors.semantics
    de.transio.hiuni.core.design.components.EmptyState(
        icon = Icons.Outlined.AssignmentTurnedIn,
        iconAccent = semantics.purple,
        iconSurface = semantics.purpleSurface,
        title = "Noch keine Aufgaben",
        body = "Tippe + für deine erste."
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoRow(
    todo: TodoEntity,
    course: CourseEntity?,
    hasMissingCourse: Boolean,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val semantics = HiUniColors.semantics
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            // Wir akzeptieren nur den End→Start-Swipe (von rechts nach links) als Löschen.
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )
    // Reset wenn das gleiche Item wiederverwendet wird (z. B. nach Cancel-Animation).
    LaunchedEffect(todo.id) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(HiUniRadii.card))
                    .background(semantics.red.copy(alpha = 0.14f))
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Löschen",
                    tint = semantics.red
                )
            }
        }
    ) {
        TodoCard(
            todo = todo,
            course = course,
            hasMissingCourse = hasMissingCourse,
            onToggleDone = onToggleDone,
            onClick = onClick
        )
    }
}

@Composable
private fun TodoCard(
    todo: TodoEntity,
    course: CourseEntity?,
    hasMissingCourse: Boolean,
    onToggleDone: () -> Unit,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val dueChip = rememberDueChip(due = todo.dueDate, isDone = todo.isDone)
    val courseColor = course?.let { courseColorFor(it) }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(HiUniRadii.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TodoCheckbox(checked = todo.isDone, onToggle = onToggleDone)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (todo.isDone) semantics.onSurfaceMuted else colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (todo.isDone) TextDecoration.LineThrough else TextDecoration.None
                )
                if (course != null || hasMissingCourse || dueChip != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (course != null && courseColor != null) {
                            CoursePill(
                                label = courseShortLabel(course),
                                bg = courseColor.bg,
                                fg = courseColor.fg
                            )
                        } else if (hasMissingCourse) {
                            CoursePill(
                                label = "Kurs entfernt",
                                bg = semantics.onSurfaceMuted.copy(alpha = 0.12f),
                                fg = semantics.onSurfaceMuted
                            )
                        }
                        if (dueChip != null) {
                            DuePill(label = dueChip.label, accent = dueChip.accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CoursePill(label: String, bg: Color, fg: Color) {
    Surface(
        shape = RoundedCornerShape(HiUniRadii.pill),
        color = bg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun TodoCheckbox(checked: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        modifier = Modifier.size(26.dp),
        shape = CircleShape,
        color = if (checked) colors.primary else Color.Transparent,
        border = if (checked) null else androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = semantics.onSurfaceMuted.copy(alpha = 0.6f)
        ),
        onClick = onToggle
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Erledigt",
                    tint = colors.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DuePill(label: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(HiUniRadii.pill),
        color = accent.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}
