package de.transio.hiuni.feature.calendar.ui

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.calendar.export.CalendarExportAction
import de.transio.hiuni.feature.calendar.export.CalendarExportViewModel
import de.transio.hiuni.feature.calendar.export.IcsExportCategory
import de.transio.hiuni.feature.calendar.export.IcsExportRange
import timber.log.Timber

/**
 * Sheet „Als .ics teilen": Auswahl der Quellen (Mehrfachauswahl) und des
 * Zeitraums, danach Teilen über den System-Chooser oder Speichern als Datei.
 * Pills hand-gestylt wie [de.transio.hiuni.feature.settings.ui.ChipRow].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IcsExportSheet(
    onDismiss: () -> Unit,
    viewModel: CalendarExportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val context = LocalContext.current

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ICS_MIME)
    ) { uri -> viewModel.writeToDocument(uri) }

    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                is CalendarExportAction.Share -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = ICS_MIME
                        putExtra(Intent.EXTRA_STREAM, action.uri)
                        putExtra(Intent.EXTRA_TITLE, action.fileName)
                        // ClipData sorgt dafür, dass die Leseberechtigung auch über
                        // den Chooser hinweg an die Ziel-App weitergereicht wird.
                        clipData = ClipData.newRawUri(action.fileName, action.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(send, "Stundenplan teilen"))
                    }.onFailure { Timber.w(it, "Teilen-Chooser ließ sich nicht öffnen") }
                }
                is CalendarExportAction.PickSaveTarget ->
                    runCatching { saveLauncher.launch(action.fileName) }
                        .onFailure {
                            Timber.w(it, "Dokument-Auswahl ließ sich nicht öffnen")
                            viewModel.writeToDocument(null)
                        }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 4.dp, bottom = 28.dp)
        ) {
            Text(
                text = "Als .ics teilen",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Exportiert eine Kalenderdatei für Google, Apple oder Outlook. " +
                    "Einmalige Kopie, spätere Änderungen in HiUni werden nicht übertragen.",
                style = MaterialTheme.typography.bodySmall,
                color = semantics.onSurfaceMuted
            )

            SectionLabel("Was")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                IcsExportCategory.entries.forEach { category ->
                    ExportPill(
                        label = category.label,
                        active = category in state.categories,
                        role = Role.Checkbox,
                        onClick = { viewModel.toggleCategory(category) }
                    )
                }
            }

            SectionLabel("Zeitraum")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                IcsExportRange.entries.forEach { range ->
                    ExportPill(
                        label = range.label,
                        active = range == state.range,
                        role = Role.RadioButton,
                        onClick = { viewModel.selectRange(range) }
                    )
                }
            }

            state.message?.let {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted
                )
            }

            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = viewModel::save,
                    enabled = !state.isWorking,
                    modifier = Modifier.weight(1f)
                ) { Text("Speichern") }
                Button(
                    onClick = viewModel::share,
                    enabled = !state.isWorking,
                    modifier = Modifier.weight(1f)
                ) { Text(if (state.isWorking) "Wird erstellt…" else "Teilen") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(18.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportPill(
    label: String,
    active: Boolean,
    role: Role,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = if (active) colors.primary else semantics.surfaceAlt,
        shape = RoundedCornerShape(HiUniRadii.pill),
        onClick = onClick,
        modifier = Modifier.semantics {
            this.role = role
            selected = active
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) colors.onPrimary else semantics.onSurfaceMuted,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp)
        )
    }
}

private const val ICS_MIME = "text/calendar"
