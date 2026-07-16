package de.transio.hiuni.feature.mensacard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.mensacard.MensaCardViewModel

/**
 * Schmaler Streifen oben in [MensaScreen] mit aktuellem Guthaben + Pfeil.
 * Tap öffnet die volle [MensaCardScreen]. Die NFC-Scan-Logik lebt komplett
 * in der vollen Screen — der Teaser ist nur Anzeige.
 */
@Composable
fun MensaCardTeaser(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MensaCardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    Surface(
        color = semantics.amber,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "Mensa-Karte öffnen",
                role = Role.Button,
                onClick = onOpen
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile - 4.dp))
                    .background(Color.White.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Nfc,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mensa-Karte",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (state.hasBalance) formatEuroShort(state.primaryBalanceMilliEuro)
                    else "Karte scannen",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

private fun formatEuroShort(milliEuro: Int): String =
    "%,.2f €".format(java.util.Locale.GERMAN, milliEuro / 1000.0)
