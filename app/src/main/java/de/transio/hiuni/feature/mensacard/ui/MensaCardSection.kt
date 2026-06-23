package de.transio.hiuni.feature.mensacard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.mensacard.MensaCardUiState
import de.transio.hiuni.feature.mensacard.MensaCardViewModel
import java.time.Duration
import java.time.Instant
import java.util.Locale

@Composable
fun MensaCardSection(
    modifier: Modifier = Modifier,
    viewModel: MensaCardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sourceLabel = viewModel.sourceLabel()

    LaunchedEffect(Unit) {
        // Sicherheitsnetz: falls eine vorherige Activity-Instanz Scan aktiv
        // gelassen hat, beim Mount stoppen, damit kein "still scanning"-Geist.
        if (!state.scanning) viewModel.cancelScan()
    }

    MensaCardCard(
        state = state,
        sourceLabel = sourceLabel,
        onScanClick = viewModel::startScan,
        onCancelClick = viewModel::cancelScan,
        modifier = modifier
    )
}

@Composable
private fun MensaCardCard(
    state: MensaCardUiState,
    sourceLabel: String,
    onScanClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.primary,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Dekorativer Halbkreis rechts oben für die Mensa-Card-Optik.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(HiUniRadii.tile))
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Nfc,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mensa-Karte",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = subtitle(state, sourceLabel),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (state.scanning) {
                    ScanInstruction(onCancelClick)
                } else {
                    BalanceRow(state = state, onScanClick = onScanClick)
                }
                if (state.error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.amberSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceRow(state: MensaCardUiState, onScanClick: () -> Unit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (state.hasBalance) "%,.2f €".format(Locale.GERMAN, state.valueEuro)
                else "—,— €",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(HiUniRadii.tile),
            modifier = Modifier.clickable(onClick = onScanClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Nfc,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (state.hasBalance) "Aktualisieren" else "Karte scannen",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ScanInstruction(onCancelClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = "Mensa-Karte an die Geräterückseite halten…",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium
        )
        Surface(
            color = Color.White.copy(alpha = 0.18f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.clickable(onClick = onCancelClick)
        ) {
            Text(
                text = "Abbrechen",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

private fun subtitle(state: MensaCardUiState, sourceLabel: String): String {
    if (!state.hasBalance) return "Karte noch nicht gescannt"
    val scanned = state.scannedAt ?: return sourceLabel.ifBlank { "Guthaben" }
    val ago = relativeAgo(scanned)
    return if (sourceLabel.isBlank()) ago else "$sourceLabel · $ago"
}

private fun relativeAgo(instant: Instant): String {
    val mins = Duration.between(instant, Instant.now()).toMinutes()
    return when {
        mins < 1 -> "gerade eben"
        mins < 60 -> "vor ${mins} Min"
        mins < 60 * 24 -> "vor ${mins / 60} Std"
        else -> "vor ${mins / (60 * 24)} Tagen"
    }
}
