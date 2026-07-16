package de.transio.hiuni.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.School
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.auth.CasLoginContract
import de.transio.hiuni.core.auth.CasState
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.settings.CasLoginViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val obtainedFmt = DateTimeFormatter
    .ofPattern("d. MMM yyyy · HH:mm", Locale.GERMAN)

@Composable
fun CasLoginCard(viewModel: CasLoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    val launcher = rememberLauncherForActivityResult(CasLoginContract()) { success ->
        viewModel.onLoginResult(success)
    }

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
                        text = "Uni-Login (LSF + Bib)",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface
                    )
                    val subline = when (val s = state) {
                        is CasState.Authenticated -> {
                            val whenStr = s.obtainedAt.atZone(ZoneId.systemDefault()).format(obtainedFmt)
                            val who = s.username?.let { " · $it" } ?: ""
                            "Angemeldet seit $whenStr Uhr$who"
                        }
                        is CasState.NeedsReauth -> "Sitzung abgelaufen — bitte erneut anmelden"
                        CasState.NeedsLogin -> "Einmalig im Browser anmelden, danach läuft Sync im Hintergrund"
                    }
                    Text(
                        text = subline,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    is CasState.Authenticated -> {
                        TextButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Abmelden", color = semantics.red, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = { launcher.launch(Unit) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Erneut anmelden") }
                    }
                    is CasState.NeedsReauth -> {
                        Button(
                            onClick = { launcher.launch(Unit) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Erneut anmelden") }
                    }
                    CasState.NeedsLogin -> {
                        Button(
                            onClick = { launcher.launch(Unit) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Mit Uni-Account anmelden") }
                    }
                }
            }

            if (state is CasState.Authenticated) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.testLsfConnection() },
                    enabled = !ui.testing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (ui.testing) "Teste LSF …" else "LSF-Verbindung testen")
                }
                ui.lastTestResult?.let { result ->
                    val message = if (result.ok) {
                        val name = result.username?.let { " · $it" } ?: ""
                        val role = result.role?.let { " ($it)" } ?: ""
                        "LSF erreichbar$name$role"
                    } else {
                        result.errorMessage ?: "Verbindung fehlgeschlagen"
                    }
                    val tint = if (result.ok) semantics.green else semantics.red
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (result.ok) Icons.Outlined.Check else Icons.Outlined.Close,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = tint
                        )
                    }
                }
            }
        }
    }
}
