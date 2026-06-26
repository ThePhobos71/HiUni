package de.transio.hiuni.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.settings.DisplayNameViewModel

@Composable
fun DisplayNameCard(viewModel: DisplayNameViewModel = hiltViewModel()) {
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
                        imageVector = Icons.Outlined.Badge,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Anzeigename",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface
                    )
                    Text(
                        text = "Wie soll dich die App auf der Startseite begrüßen?",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Live-Vorschau
            Surface(
                color = colors.primaryContainer,
                shape = RoundedCornerShape(HiUniRadii.tile)
            ) {
                Text(
                    text = "Hi ${state.currentGreeting}!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            ModeOption(
                label = "Erster Vorname",
                preview = state.firstNamePreview ?: "Studi",
                selected = state.mode == SettingsDataStore.DISPLAY_NAME_MODE_FIRST,
                onClick = { viewModel.setMode(SettingsDataStore.DISPLAY_NAME_MODE_FIRST) }
            )
            if (state.hasMultipleFirstNames) {
                ModeOption(
                    label = "Alle Vornamen",
                    preview = state.fullVornamePreview ?: state.firstNamePreview ?: "Studi",
                    selected = state.mode == SettingsDataStore.DISPLAY_NAME_MODE_ALL,
                    onClick = { viewModel.setMode(SettingsDataStore.DISPLAY_NAME_MODE_ALL) }
                )
            }
            ModeOption(
                label = "Eigener Name",
                preview = state.customName.ifBlank { "(unten eintragen)" },
                selected = state.mode == SettingsDataStore.DISPLAY_NAME_MODE_CUSTOM,
                onClick = { viewModel.setMode(SettingsDataStore.DISPLAY_NAME_MODE_CUSTOM) }
            )

            if (state.mode == SettingsDataStore.DISPLAY_NAME_MODE_CUSTOM) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.customName,
                    onValueChange = { viewModel.setCustom(it) },
                    label = { Text("Eigener Anzeigename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ModeOption(
    label: String,
    preview: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = if (selected) colors.primaryContainer.copy(alpha = 0.5f) else colors.surface,
        shape = RoundedCornerShape(HiUniRadii.tile),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = colors.onSurface
                )
                Text(
                    text = "Hi $preview!",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}
