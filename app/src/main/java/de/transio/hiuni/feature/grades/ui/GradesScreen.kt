package de.transio.hiuni.feature.grades.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.design.HiUniSemanticColors
import de.transio.hiuni.core.design.components.EmptyState
import de.transio.hiuni.core.design.components.ErrorState
import de.transio.hiuni.core.design.components.HiUniSkeletonList
import de.transio.hiuni.core.design.components.HiUniTopBar
import de.transio.hiuni.feature.grades.GradesUiState
import de.transio.hiuni.feature.grades.GradesViewModel
import de.transio.hiuni.feature.grades.SemesterSection
import de.transio.hiuni.feature.grades.data.GradeEntity
import de.transio.hiuni.feature.grades.data.GradeStatus
import de.transio.hiuni.ui.responsive.FullWidthContent
import java.util.Locale

@Composable
fun GradesScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: GradesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val snackbarHostState = remember { SnackbarHostState() }

    // Fehler mit Cache → dezente Snackbar (nicht doppelt zum ErrorState feuern:
    // nur wenn hasContent). Danach im VM konsumieren.
    LaunchedEffect(state.errorMessage, state.hasContent) {
        val err = state.errorMessage ?: return@LaunchedEffect
        if (state.hasContent) {
            snackbarHostState.showSnackbar(err)
            viewModel.consumeError()
        }
    }

    FullWidthContent {
        Scaffold(
            containerColor = colors.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                HiUniTopBar(title = "Noten", onBack = onBack)

                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    when {
                        // 1. CAS-Session fehlt und kein Cache → Login-Hinweis.
                        state.isAuthRequired && !state.hasContent ->
                            AuthRequiredCard(onOpenLogin = onOpenLogin)

                        // 2. Fehler und kein Cache → ErrorState mit Retry.
                        state.errorMessage != null && !state.hasContent -> ErrorState(
                            iconSurface = semantics.redSurface,
                            iconAccent = semantics.red,
                            title = "Verbindung fehlgeschlagen",
                            body = state.errorMessage,
                            onRetry = { viewModel.refresh() }
                        )

                        // 3. Erster Load ohne Cache → Skeleton.
                        state.isLoading && !state.hasContent ->
                            HiUniSkeletonList(modifier = Modifier.fillMaxSize())

                        // 4. Fertig geladen, aber leer → Empty mit LSF-Login-Hinweis.
                        !state.hasContent -> EmptyGradesState(
                            isRefreshing = state.isRefreshing,
                            onRefresh = { viewModel.refresh() },
                            onOpenLogin = onOpenLogin
                        )

                        // 5. Content.
                        else -> GradesContent(state = state, semantics = semantics)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Content
// ---------------------------------------------------------------------------

@Composable
private fun GradesContent(
    state: GradesUiState,
    semantics: HiUniSemanticColors
) {
    // Aufklapp-Zustand pro Semester: neuestes (Index 0) offen, ältere zu.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var legendOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "gpa-hero") {
            GpaHero(gpa = state.gpa, semantics = semantics)
        }

        item(key = "ects-progress") {
            EctsProgress(
                totalLp = state.totalLp ?: 0,
                progress = state.ectsProgress,
                semantics = semantics
            )
        }

        state.semesters.forEachIndexed { index, section ->
            val isOpen = expanded[section.semester] ?: (index == 0)
            item(key = "sem-header-${section.semester}") {
                SemesterHeader(
                    section = section,
                    expanded = isOpen,
                    semantics = semantics,
                    onToggle = { expanded[section.semester] = !isOpen }
                )
            }
            if (isOpen) {
                section.grades.forEach { grade ->
                    item(key = "grade-${grade.rowId}") {
                        GradeRow(grade = grade, semantics = semantics)
                    }
                }
            }
        }

        item(key = "legend") {
            Spacer(Modifier.height(4.dp))
            GradeScaleLegend(
                expanded = legendOpen,
                semantics = semantics,
                onToggle = { legendOpen = !legendOpen }
            )
        }

        item(key = "spacer-bottom") { Spacer(Modifier.height(80.dp)) }
    }
}

// ---------------------------------------------------------------------------
// GPA-Hero — im Stil des CountdownHero (primaryContainer-Surface, großer Wert)
// ---------------------------------------------------------------------------

@Composable
private fun GpaHero(gpa: Double?, semantics: HiUniSemanticColors) {
    val colors = MaterialTheme.colorScheme
    val gpaColor = gpa?.let { gradeColor(it, semantics, colors.onPrimaryContainer) }
        ?: colors.onPrimaryContainer

    Surface(
        color = colors.primaryContainer,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Notendurchschnitt".uppercase(Locale.GERMAN),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onPrimaryContainer.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = gpa?.let { formatGrade(it) } ?: "–",
                style = MaterialTheme.typography.displayMedium,
                color = gpaColor,
                fontWeight = FontWeight.Bold
            )
            if (gpa == null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Noch kein Durchschnitt ausgewiesen",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ECTS-Fortschritt — flacher Balken im Design-Kit-Stil
// ---------------------------------------------------------------------------

@Composable
private fun EctsProgress(
    totalLp: Int,
    progress: Float,
    semantics: HiUniSemanticColors
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Leistungspunkte",
                    style = MaterialTheme.typography.labelMedium,
                    color = semantics.onSurfaceMuted,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$totalLp / ${GradesUiState.TARGET_LP} LP",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            // Flacher, hand-gestylter Track + Fill (kein Stock-M3-ProgressIndicator).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(HiUniRadii.smallPill))
                    .background(semantics.onSurfaceMuted.copy(alpha = 0.16f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(HiUniRadii.smallPill))
                        .background(colors.primary)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Semester-Sektion
// ---------------------------------------------------------------------------

@Composable
private fun SemesterHeader(
    section: SemesterSection,
    expanded: Boolean,
    semantics: HiUniSemanticColors,
    onToggle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = if (expanded) "Semester zuklappen" else "Semester aufklappen",
                role = Role.Button,
                onClick = onToggle
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.semester,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${section.passedLp} LP",
                style = MaterialTheme.typography.labelMedium,
                color = semantics.onSurfaceMuted,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.size(10.dp))
            // Chevron nach Repo-Konvention (▾ offen / ▸ zu) — hier als Material-Icon.
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = semantics.onSurfaceMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun GradeRow(grade: GradeEntity, semantics: HiUniSemanticColors) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = grade.titel,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill(status = grade.status, semantics = semantics)
                    if (grade.bonusLp > 0) {
                        Text(
                            text = "${grade.bonusLp} LP",
                            style = MaterialTheme.typography.labelMedium,
                            color = semantics.onSurfaceMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (grade.versuch > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${grade.versuch}. Versuch",
                        style = MaterialTheme.typography.labelSmall,
                        color = semantics.onSurfaceMuted
                    )
                }
            }
            // Note fett, rechtsbündig.
            Text(
                text = grade.note?.let { formatGrade(it) } ?: "–",
                style = MaterialTheme.typography.titleMedium,
                color = grade.note?.let { gradeColor(it, semantics, colors.onSurface) }
                    ?: semantics.onSurfaceMuted,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Status-Pill — hand-gestylte Surface+Text-Pill (kein M3-Chip)
// ---------------------------------------------------------------------------

@Composable
private fun StatusPill(status: GradeStatus, semantics: HiUniSemanticColors) {
    val (label, bg, fg) = when (status) {
        GradeStatus.PASSED -> Triple("bestanden", semantics.greenSurface, semantics.green)
        GradeStatus.FAILED -> Triple("nicht bestanden", semantics.redSurface, semantics.red)
        GradeStatus.REGISTERED -> Triple("angemeldet", semantics.surfaceAlt, semantics.onSurfaceMuted)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(HiUniRadii.pill)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Notenskala-Legende — aufklappbare Fußsektion
// ---------------------------------------------------------------------------

@Composable
private fun GradeScaleLegend(
    expanded: Boolean,
    semantics: HiUniSemanticColors,
    onToggle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = if (expanded) "Legende zuklappen" else "Legende aufklappen",
                        role = Role.Button,
                        onClick = onToggle
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notenskala",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = semantics.onSurfaceMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LegendRow("1,0 – 1,5", "sehr gut", semantics.green, semantics)
                    LegendRow("1,6 – 2,5", "gut", semantics.green, semantics)
                    LegendRow("2,6 – 3,5", "befriedigend", semantics.amber, semantics)
                    LegendRow("3,6 – 4,0", "ausreichend", semantics.amber, semantics)
                    LegendRow("5,0", "nicht bestanden", semantics.red, semantics)
                }
            }
        }
    }
}

@Composable
private fun LegendRow(
    range: String,
    label: String,
    accent: Color,
    semantics: HiUniSemanticColors
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = range,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = semantics.onSurfaceMuted
        )
    }
}

// ---------------------------------------------------------------------------
// Auth-Required-Karte
// ---------------------------------------------------------------------------

@Composable
private fun AuthRequiredCard(onOpenLogin: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    EmptyState(
        icon = Icons.Outlined.School,
        iconAccent = colors.primary,
        iconSurface = colors.primaryContainer,
        title = "Mit Uni-Login anmelden",
        body = "Um deinen Notenspiegel aus dem LSF zu laden, melde dich erst über " +
            "die Einstellungen mit deiner RZ-Kennung an.",
        action = {
            TextButton(onClick = onOpenLogin) {
                Text("Zu den Einstellungen")
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Empty-State
// ---------------------------------------------------------------------------

@Composable
private fun EmptyGradesState(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenLogin: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    EmptyState(
        icon = Icons.Outlined.School,
        iconAccent = colors.primary,
        iconSurface = colors.primaryContainer,
        title = "Noch keine Noten",
        body = "Sobald dein LSF-Notenspiegel synchronisiert ist, erscheinen deine " +
            "Leistungen hier. Prüfe, ob du in den Einstellungen mit deiner RZ-Kennung angemeldet bist.",
        action = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = onRefresh, enabled = !isRefreshing) {
                    Text(if (isRefreshing) "Synchronisiere…" else "Jetzt synchronisieren")
                }
                TextButton(onClick = onOpenLogin) {
                    Text("Zum LSF-Login")
                }
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Deutsche Komma-Darstellung einer Note (2.7 → "2,7"). */
private fun formatGrade(value: Double): String =
    String.format(Locale.GERMAN, "%.1f", value)

/**
 * Farbe nach Notenstufe (deutsche Skala):
 *  - ≤ 2,5 → grün (sehr gut / gut)
 *  - ≤ 4,0 → amber (befriedigend / ausreichend)
 *  - > 4,0 → rot (nicht bestanden)
 * `neutral` ist der Fallback-Ton, falls die Note exakt 0/ungültig wäre.
 */
private fun gradeColor(value: Double, semantics: HiUniSemanticColors, neutral: Color): Color = when {
    value <= 0.0 -> neutral
    value <= 2.5 -> semantics.green
    value <= 4.0 -> semantics.amber
    else -> semantics.red
}
