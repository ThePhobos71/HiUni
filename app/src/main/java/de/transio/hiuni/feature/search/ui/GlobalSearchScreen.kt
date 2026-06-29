package de.transio.hiuni.feature.search.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.AssignmentLate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.common.DateTimeUtils
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.design.components.EmptyState
import de.transio.hiuni.core.design.components.HiUniSearchBar
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.email.data.EmailEntity
import de.transio.hiuni.feature.learnweb.data.LearnwebAssignment
import de.transio.hiuni.feature.learnweb.data.LearnwebCourse
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.mensa.data.MealEntity
import de.transio.hiuni.feature.search.GlobalSearchViewModel
import de.transio.hiuni.feature.sport.data.SportEventEntity
import de.transio.hiuni.navigation.Destination
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * App-weite Suche („Spotlight"). Top-Bar mit Suchfeld + ESC-X, darunter eine
 * gruppierte Liste pro Kategorie (Mails, Termine, Kurse, Klausuren, Mensa,
 * Sport). Pro Kategorie zeigen wir maximal die ersten 5 Treffer — das Repo
 * kappt schon, das ist hier nur visuelles Limit-Sicherheit.
 *
 * Tap auf einen Treffer navigiert zum jeweiligen Tab. Pre-Selection per
 * Deep-Link ist nicht implementiert (Phase 2) — der User landet auf dem Tab
 * und findet den Eintrag selbst, was für eine v1-Spotlight ausreichend ist.
 */
@Composable
fun GlobalSearchScreen(
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
    onOpenSportDetail: (Long) -> Unit,
    viewModel: GlobalSearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    // Learnweb-Assignment öffnet direkt die Moodle-Seite im Browser — analog
    // zum Verhalten in [LearnwebScreen]. Course-Tap navigiert dagegen zur
    // App-Liste (Destination.Learnweb), damit der User dort weitere Aktionen
    // wie Pull-to-Refresh hat.
    val openAssignmentUrl: (LearnwebAssignment) -> Unit = { assignment ->
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(assignment.url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HiUniSearchBar(
                query = state.query,
                onQueryChange = viewModel::setQuery,
                onClose = onBack,
                placeholder = "Suche in Mails, Kalender, Kursen …",
                autoFocus = true
            )

            when {
                !state.hasQuery -> EmptyHint()
                state.results.isEmpty && !state.isLoading -> NoResults(query = state.query)
                else -> ResultsList(
                    results = state.results,
                    onOpenEmail = { onNavigate(Destination.Email) },
                    onOpenEvent = { onNavigate(Destination.Calendar) },
                    onOpenCourse = { onNavigate(Destination.Courses) },
                    onOpenExam = { onNavigate(Destination.Exams) },
                    onOpenMeal = { onNavigate(Destination.Mensa) },
                    onOpenSport = { slot -> onOpenSportDetail(slot.supersaasSlotId) },
                    onOpenLearnwebCourse = { onNavigate(Destination.Learnweb) },
                    onOpenLearnwebAssignment = openAssignmentUrl
                )
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    val colors = MaterialTheme.colorScheme
    EmptyState(
        icon = Icons.Outlined.Search,
        iconAccent = colors.primary,
        iconSurface = colors.primaryContainer,
        title = "Tippe um zu suchen",
        body = "Spotlight durchsucht Mails, Kalender, Kurse, Klausuren, " +
            "Mensa, Sport und Learnweb gleichzeitig."
    )
}

@Composable
private fun NoResults(query: String) {
    val colors = MaterialTheme.colorScheme
    EmptyState(
        icon = Icons.Outlined.Search,
        iconAccent = colors.onSurfaceVariant,
        iconSurface = HiUniColors.semantics.surfaceAlt,
        title = "Keine Treffer für „$query\""
    )
}

@Composable
private fun ResultsList(
    results: de.transio.hiuni.core.search.GlobalSearchResults,
    onOpenEmail: (EmailEntity) -> Unit,
    onOpenEvent: (CustomEventEntity) -> Unit,
    onOpenCourse: (CourseEntity) -> Unit,
    onOpenExam: (ExamEntity) -> Unit,
    onOpenMeal: (MealEntity) -> Unit,
    onOpenSport: (SportEventEntity) -> Unit,
    onOpenLearnwebCourse: (LearnwebCourse) -> Unit,
    onOpenLearnwebAssignment: (LearnwebAssignment) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (results.emails.isNotEmpty()) {
            categorySection(
                title = "Mails",
                count = results.emails.size,
                items = results.emails,
                key = { it.rowId },
                content = { email -> EmailRow(email, onClick = { onOpenEmail(email) }) }
            )
        }
        if (results.events.isNotEmpty()) {
            categorySection(
                title = "Termine",
                count = results.events.size,
                items = results.events,
                key = { it.id },
                content = { event -> EventRow(event, onClick = { onOpenEvent(event) }) }
            )
        }
        if (results.courses.isNotEmpty()) {
            categorySection(
                title = "Kurse",
                count = results.courses.size,
                items = results.courses,
                key = { it.id },
                content = { course -> CourseRow(course, onClick = { onOpenCourse(course) }) }
            )
        }
        if (results.exams.isNotEmpty()) {
            categorySection(
                title = "Klausuren",
                count = results.exams.size,
                items = results.exams,
                key = { it.rowId },
                content = { exam -> ExamRow(exam, onClick = { onOpenExam(exam) }) }
            )
        }
        if (results.mensaMeals.isNotEmpty()) {
            categorySection(
                title = "Mensa",
                count = results.mensaMeals.size,
                items = results.mensaMeals,
                // Composite-Key, da MealEntity keinen PK-Long hat — sourceId+locationId
                // ist eindeutig im Window.
                key = { "${it.locationId}-${it.sourceId}" },
                content = { meal -> MealRow(meal, onClick = { onOpenMeal(meal) }) }
            )
        }
        if (results.sportEvents.isNotEmpty()) {
            categorySection(
                title = "Sport",
                count = results.sportEvents.size,
                items = results.sportEvents,
                key = { it.supersaasSlotId },
                content = { slot -> SportRow(slot, onClick = { onOpenSport(slot) }) }
            )
        }
        if (results.learnwebCourses.isNotEmpty()) {
            categorySection(
                title = "Learnweb-Kurse",
                count = results.learnwebCourses.size,
                items = results.learnwebCourses,
                key = { it.courseId },
                content = { course ->
                    LearnwebCourseRow(course, onClick = { onOpenLearnwebCourse(course) })
                }
            )
        }
        if (results.learnwebAssignments.isNotEmpty()) {
            categorySection(
                title = "Learnweb-Abgaben",
                count = results.learnwebAssignments.size,
                items = results.learnwebAssignments,
                key = { it.eventId },
                content = { assignment ->
                    LearnwebAssignmentRow(
                        assignment,
                        onClick = { onOpenLearnwebAssignment(assignment) }
                    )
                }
            )
        }
    }
}

/**
 * Mini-DSL: ein Kategorie-Header + die zugehörigen Items in einer LazyListScope-
 * Sequenz. Spart in [ResultsList] wiederholtes `item { … } items(…) { … }`.
 */
private inline fun <T> androidx.compose.foundation.lazy.LazyListScope.categorySection(
    title: String,
    count: Int,
    items: List<T>,
    noinline key: (T) -> Any,
    crossinline content: @Composable (T) -> Unit
) {
    item(key = "header-$title") {
        CategoryHeader(title = title, count = count)
    }
    items(items = items, key = key) { item ->
        content(item)
    }
}

@Composable
private fun CategoryHeader(title: String, count: Int) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        Text(
            text = "($count)",
            style = MaterialTheme.typography.bodyMedium,
            color = semantics.onSurfaceMuted
        )
    }
}

@Composable
private fun ResultRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    meta: String? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile))
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!meta.isNullOrBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun EmailRow(email: EmailEntity, onClick: () -> Unit) {
    val from = email.fromName?.takeIf { it.isNotBlank() } ?: email.fromAddress
    ResultRow(
        icon = Icons.Outlined.Email,
        title = email.subject.ifBlank { "(kein Betreff)" },
        subtitle = from,
        meta = DateTimeUtils.formatRelativeDay(email.receivedAt),
        onClick = onClick
    )
}

@Composable
private fun EventRow(event: CustomEventEntity, onClick: () -> Unit) {
    val subtitleParts = buildList {
        event.location?.takeIf { it.isNotBlank() }?.let { add(it) }
        event.description?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    ResultRow(
        icon = Icons.Outlined.CalendarMonth,
        title = event.title,
        subtitle = subtitleParts.joinToString(" · ").ifBlank { null },
        meta = DateTimeUtils.formatRelativeDay(event.startTime),
        onClick = onClick
    )
}

@Composable
private fun CourseRow(course: CourseEntity, onClick: () -> Unit) {
    val subtitle = listOfNotNull(
        course.moduleAbbreviation?.takeIf { it.isNotBlank() },
        course.professor.takeIf { it.isNotBlank() }
    ).joinToString(" · ").ifBlank { null }
    ResultRow(
        icon = Icons.Outlined.MenuBook,
        title = course.name,
        subtitle = subtitle,
        meta = course.lsfCode,
        onClick = onClick
    )
}

@Composable
private fun ExamRow(exam: ExamEntity, onClick: () -> Unit) {
    val dateFmt = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)
    ResultRow(
        icon = Icons.Outlined.AssignmentLate,
        title = exam.moduleName.ifBlank { exam.pruefungstext },
        subtitle = exam.semester,
        meta = exam.examDate?.format(dateFmt),
        onClick = onClick
    )
}

@Composable
private fun MealRow(meal: MealEntity, onClick: () -> Unit) {
    val dateFmt = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)
    ResultRow(
        icon = Icons.Outlined.LocalDining,
        title = meal.name,
        subtitle = meal.category,
        meta = meal.date.format(dateFmt),
        onClick = onClick
    )
}

@Composable
private fun SportRow(slot: SportEventEntity, onClick: () -> Unit) {
    ResultRow(
        icon = Icons.Outlined.SportsBasketball,
        title = slot.title,
        subtitle = slot.location,
        meta = DateTimeUtils.formatRelativeDay(slot.startTime),
        onClick = onClick
    )
}

@Composable
private fun LearnwebCourseRow(course: LearnwebCourse, onClick: () -> Unit) {
    ResultRow(
        icon = Icons.Outlined.School,
        title = course.name,
        subtitle = "Learnweb",
        onClick = onClick
    )
}

@Composable
private fun LearnwebAssignmentRow(assignment: LearnwebAssignment, onClick: () -> Unit) {
    ResultRow(
        icon = Icons.AutoMirrored.Outlined.Assignment,
        title = assignment.title,
        subtitle = formatLearnwebDueRelative(assignment.dueEpoch),
        meta = formatLearnwebDueDate(assignment.dueEpoch),
        onClick = onClick
    )
}

// Kurz-Formatter für die Spotlight-Cards. Wir replizieren bewusst nicht den
// vollen Subtitle aus [LearnwebScreen] — in der Suche ist Platz knapp und das
// Datum landet als „meta" rechts.
private val LEARNWEB_DUE_DATE = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)

private fun formatLearnwebDueRelative(dueEpoch: Long): String {
    val zone = ZoneId.systemDefault()
    val now = Instant.now()
    val due = Instant.ofEpochMilli(dueEpoch)
    if (due.isBefore(now)) return "abgelaufen"
    val minutes = Duration.between(now, due).toMinutes()
    return when {
        minutes < 60L -> "in $minutes Min"
        minutes < 24L * 60 -> "in ${minutes / 60} Std"
        minutes < 48L * 60 -> "morgen"
        minutes < 14L * 24 * 60 -> "in ${minutes / (24 * 60)} Tagen"
        else -> due.atZone(zone).format(LEARNWEB_DUE_DATE)
    }
}

private fun formatLearnwebDueDate(dueEpoch: Long): String =
    Instant.ofEpochMilli(dueEpoch)
        .atZone(ZoneId.systemDefault())
        .format(LEARNWEB_DUE_DATE)
