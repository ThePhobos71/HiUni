package de.transio.hiuni.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.design.components.HiUniTopBar
import de.transio.hiuni.core.update.ReleaseInfo
import de.transio.hiuni.feature.settings.AppUpdateUiState
import de.transio.hiuni.feature.settings.AppUpdateViewModel
import de.transio.hiuni.feature.settings.UpdatePhase

/**
 * App-Updates: installierte Version, Suche nach neuen GitHub-Releases, Changelog,
 * Download mit Fortschritt und Installation. Auch Ziel der Mitteilung
 * „HiUni X.Y.Z verfügbar".
 */
@Composable
fun AppUpdateSettingsScreen(
    onBack: () -> Unit,
    viewModel: AppUpdateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val lifecycleOwner = LocalLifecycleOwner.current

    // Rückkehr aus „Unbekannte Apps installieren": mit Erlaubnis weiter installieren.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            HiUniTopBar(
                title = "App-Updates",
                onBack = onBack,
                subtitle = "Neue Versionen aus GitHub Releases"
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { UpdateStatusCard(state, viewModel) }
                item {
                    AutoCheckCard(
                        enabled = state.autoCheck,
                        available = state.phase != UpdatePhase.DebugBuild,
                        onToggle = viewModel::setAutoCheck
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun UpdateStatusCard(state: AppUpdateUiState, viewModel: AppUpdateViewModel) {
    val semantics = HiUniColors.semantics
    val uriHandler = LocalUriHandler.current
    SectionCard(
        icon = Icons.Outlined.SystemUpdate,
        title = "App-Updates",
        subtitle = "Installiert: Version ${state.currentVersionName}"
    ) {
        when (val phase = state.phase) {
            UpdatePhase.DebugBuild -> {
                StatusPill("Debug-Build", semantics.surfaceAlt, semantics.onSurfaceMuted)
                Spacer(Modifier.height(8.dp))
                MutedText("Updates nur in Release-Builds")
            }
            UpdatePhase.Idle, UpdatePhase.Checking -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    MutedText("Suche nach Updates…")
                }
            }
            UpdatePhase.UpToDate -> {
                StatusPill("Aktuell", semantics.greenSurface, semantics.green)
                Spacer(Modifier.height(8.dp))
                MutedText("Du nutzt die neueste Version.")
                ActionRow { TextButton(onClick = viewModel::checkNow) { Text("Nach Updates suchen") } }
            }
            is UpdatePhase.Available -> {
                ReleaseHeader(phase.release, pill = "Neue Version")
                if (phase.skipped) {
                    Spacer(Modifier.height(6.dp))
                    MutedText("Du hast diese Version übersprungen.")
                }
                Changelog(phase.release)
                ActionRow {
                    TextButton(onClick = { uriHandler.openUri(phase.release.htmlUrl) }) { Text("Auf GitHub") }
                    if (!phase.skipped) {
                        TextButton(onClick = viewModel::skipVersion) { Text("Überspringen") }
                    }
                    PrimaryPill("Herunterladen", onClick = viewModel::download)
                }
            }
            is UpdatePhase.Downloading -> {
                ReleaseHeader(phase.release, pill = "Lädt ${phase.percent} %")
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { phase.percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is UpdatePhase.ReadyToInstall -> {
                ReleaseHeader(phase.release, pill = "Bereit zur Installation")
                Spacer(Modifier.height(8.dp))
                MutedText("Download geprüft: Signatur und Version passen.")
                ActionRow { PrimaryPill("Installieren", onClick = viewModel::install) }
            }
            is UpdatePhase.NeedsInstallPermission -> {
                ReleaseHeader(phase.release, pill = "Erlaubnis nötig")
                Spacer(Modifier.height(8.dp))
                MutedText(
                    "Erlaube HiUni einmalig, Apps zu installieren " +
                        "(„Unbekannte Apps installieren“). Danach geht es automatisch weiter."
                )
                ActionRow {
                    PrimaryPill("Erlaubnis erteilen", onClick = viewModel::openInstallPermissionSettings)
                }
            }
            is UpdatePhase.Installing -> {
                ReleaseHeader(phase.release, pill = "Installation läuft")
                Spacer(Modifier.height(8.dp))
                MutedText("Bitte im Systemdialog bestätigen. HiUni startet danach neu.")
            }
            is UpdatePhase.Failed -> {
                StatusPill("Fehler", semantics.redSurface, semantics.red)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = phase.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                ActionRow {
                    TextButton(
                        onClick = if (phase.release != null) viewModel::download else viewModel::checkNow
                    ) { Text("Erneut versuchen") }
                }
            }
        }
    }
}

@Composable
private fun AutoCheckCard(enabled: Boolean, available: Boolean, onToggle: (Boolean) -> Unit) {
    SectionCard(
        icon = Icons.Outlined.Autorenew,
        title = "Automatisch nach Updates suchen",
        subtitle = "Alle paar Stunden im Hintergrund, mit Mitteilung bei neuer Version"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MutedText(
                when {
                    !available -> "Nur in Release-Builds"
                    enabled -> "Aktiv"
                    else -> "Aus"
                }
            )
            Switch(checked = enabled && available, enabled = available, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun ReleaseHeader(release: ReleaseInfo, pill: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = release.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            modifier = Modifier.weight(1f, fill = false)
        )
        StatusPill(pill, colors.primaryContainer, colors.primary)
    }
}

@Composable
private fun Changelog(release: ReleaseInfo) {
    val text = formatChangelog(release.changelog)
    if (text.isBlank()) return
    Spacer(Modifier.height(10.dp))
    Surface(
        color = HiUniColors.semantics.surfaceAlt,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun StatusPill(text: String, background: Color, foreground: Color) {
    Surface(color = background, shape = RoundedCornerShape(50)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PrimaryPill(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.primary, shape = RoundedCornerShape(50), onClick = onClick) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.onPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun MutedText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = HiUniColors.semantics.onSurfaceMuted
    )
}

@Composable
private fun ActionRow(content: @Composable () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/**
 * Release-Text ist Markdown. Für die Karte reicht eine einfache Aufbereitung:
 * Überschriften ohne `#`, Listenpunkte als `•`, Fett-Markierungen weg.
 */
internal fun formatChangelog(markdown: String): String =
    markdown.lines()
        .map { line ->
            val trimmed = line.trimEnd()
            when {
                trimmed.trimStart().startsWith("#") -> trimmed.trimStart().trimStart('#').trim()
                trimmed.trimStart().startsWith("- ") || trimmed.trimStart().startsWith("* ") ->
                    "• " + trimmed.trimStart().substring(2).trim()
                else -> trimmed
            }.replace("**", "").replace("`", "")
        }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
