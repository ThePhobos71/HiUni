package de.transio.hiuni.feature.learnweb.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.learnweb.LearnwebUiState
import de.transio.hiuni.feature.learnweb.LearnwebViewModel
import de.transio.hiuni.feature.learnweb.data.LearnwebCourse
import de.transio.hiuni.ui.responsive.FullWidthContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnwebScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: LearnwebViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        val err = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(err)
        viewModel.consumeError()
    }

    val openCourse: (LearnwebCourse) -> Unit = remember(context) {
        { course ->
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(course.url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    FullWidthContent {
        Scaffold(
            containerColor = colors.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LearnwebHeader(state = state)
                        Spacer(Modifier.height(10.dp))
                        LearnwebBody(
                            state = state,
                            onOpenCourse = openCourse,
                            onOpenSettings = onOpenSettings
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LearnwebHeader(state: LearnwebUiState) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Learnweb",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            val (label, color, bg) = when {
                !state.isAuthenticated -> Triple("OFFLINE", semantics.red, semantics.redSurface)
                state.courses.isEmpty() -> Triple("0 KURSE", semantics.amber, semantics.amberSurface)
                else -> Triple(
                    "${state.courses.size} KURSE",
                    semantics.green,
                    semantics.greenSurface
                )
            }
            StatusPill(text = label, color = color, background = bg)
        }
        Text(
            text = "Eingeschriebene Moodle-Kurse",
            style = MaterialTheme.typography.bodySmall,
            color = semantics.onSurfaceMuted,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun LearnwebBody(
    state: LearnwebUiState,
    onOpenCourse: (LearnwebCourse) -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    if (state.courses.isEmpty()) {
        when {
            !state.isAuthenticated -> EmptyState(
                icon = { Icon(Icons.Outlined.School, contentDescription = null) },
                title = "Mit Uni-Login anmelden",
                message = "Um deine Learnweb-Kurse zu sehen, melde dich erst über die Einstellungen mit deiner RZ-Kennung an.",
                primaryActionLabel = "Zu den Einstellungen",
                onPrimaryAction = onOpenSettings
            )
            !state.initialSyncDone -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            else -> EmptyState(
                icon = { Icon(Icons.Outlined.School, contentDescription = null) },
                title = "Keine Kurse gefunden",
                message = "In deinem Learnweb-Dashboard sind keine eingeschriebenen Kurse zu sehen. " +
                    "Falls das nicht stimmt, ziehe zum Aktualisieren nach unten."
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(state.courses, key = { it.courseId }) { course ->
            CourseRow(course = course, onClick = { onOpenCourse(course) })
        }
    }
}

@Composable
private fun CourseRow(course: LearnwebCourse, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Tippen zum Öffnen",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = "Im Browser öffnen",
                tint = semantics.onSurfaceMuted
            )
        }
    }
}



@Composable
private fun EmptyState(
    icon: @Composable () -> Unit,
    title: String,
    message: String,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null
) {
    val semantics = HiUniColors.semantics
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = semantics.onSurfaceMuted
            )
            if (primaryActionLabel != null && onPrimaryAction != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onPrimaryAction) {
                    Text(primaryActionLabel)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    background: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

