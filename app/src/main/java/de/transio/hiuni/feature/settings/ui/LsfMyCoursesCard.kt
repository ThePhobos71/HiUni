package de.transio.hiuni.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.settings.LsfMyCoursesViewModel

@Composable
fun LsfMyCoursesCard(viewModel: LsfMyCoursesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = colors.primaryContainer,
                    shape = RoundedCornerShape(HiUniRadii.tile)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.School,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kurse aus LSF",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface
                    )
                    Text(
                        text = "Importiert alle angemeldeten/zugelassenen Veranstaltungen des aktuellen Semesters automatisch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            state.lastResult?.let { result ->
                Surface(
                    color = colors.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(HiUniRadii.tile),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = "${result.imported} neu · ${result.updated} aktualisiert · ${result.pruned} entfernt",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (result.detailsFetched > 0) {
                            Text(
                                text = "${result.detailsFetched} Detailseite${if (result.detailsFetched == 1) "" else "n"} geladen",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.primary
                            )
                        }
                        if (result.semester.isNotBlank()) {
                            Text(
                                text = "Aktuelles Semester: ${result.semester}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.primary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            state.errorMessage?.let { err ->
                Surface(
                    color = semantics.redSurface,
                    shape = RoundedCornerShape(HiUniRadii.tile),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.red,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = { viewModel.sync() },
                enabled = state.hasSession && !state.syncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.onPrimary
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Synchronisiere …")
                } else {
                    Text(
                        if (state.hasSession) "Kurse jetzt importieren"
                        else "Erst Uni-Login durchführen"
                    )
                }
            }
        }
    }
}
