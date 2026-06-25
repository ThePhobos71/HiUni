package de.transio.hiuni.feature.profile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.transio.hiuni.core.auth.UserProfile
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii

/**
 * Digitale Studi-Karte. Zeigt einen QR-Code und einen Code-128-Barcode mit der
 * Matrikelnummer als Inhalt — beides offline gerendert via ZXing-Core.
 *
 * Layout (oben nach unten): Header-Row (Badge + Titel + OFFLINE-Pill), QR-Code 200×200,
 * monospaced Matrikelnummer in 4er-Gruppen, Code-128-Barcode full-width × 70.dp, Footnote.
 *
 * Edge-Cases:
 * - Keine Matrikel → kompakter "nicht verfügbar"-Hinweis statt der Karte.
 * - QR scheitert → QR wird ausgeblendet, Rest bleibt.
 * - Barcode scheitert → Barcode wird ausgeblendet, Rest bleibt.
 * - Beide scheitern → ganze Section wird ausgeblendet.
 */
@Composable
fun StudiCardSection(
    profile: UserProfile,
    modifier: Modifier = Modifier
) {
    val matrikel = profile.matrikel?.trim().orEmpty()
    if (matrikel.isBlank()) {
        EmptyStudiCard(modifier = modifier)
        return
    }

    val density = LocalDensity.current
    val barcodeHeightPx = with(density) { 70.dp.roundToPx() }
    // Wir wissen die Breite des Inhalts vor dem Layout nicht — 720px ist ausreichend
    // hochauflösend für die meisten Phone-Breiten (~360–420dp ≈ 720–1260px) und ZXing
    // skaliert die Module gleichmäßig.
    val barcodeWidthPx = 720

    val barcode = rememberCode128Bitmap(
        content = matrikel,
        widthPx = barcodeWidthPx,
        heightPx = barcodeHeightPx
    ) ?: return

    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(HiUniRadii.tile))
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Badge,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "Studierendenausweis",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = semantics.greenSurface,
                    shape = RoundedCornerShape(HiUniRadii.pill)
                ) {
                    Text(
                        text = "OFFLINE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = semantics.green,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Matrikel — Monospace, in 4er-Gruppen
            Text(
                text = formatMatrikelGrouped(matrikel),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                ),
                color = colors.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))
            Image(
                bitmap = barcode,
                contentDescription = "Code-128-Barcode mit Matrikelnummer",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.None
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Diese Karte enthält nur deine Matrikelnummer und funktioniert ohne Internet.",
                style = MaterialTheme.typography.bodySmall,
                color = semantics.onSurfaceMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EmptyStudiCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile))
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Badge,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Studierendenausweis",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Matrikelnummer nicht verfügbar — bitte erst einloggen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}

/**
 * Formatiert "00403556" → "0040 3556". Bei nicht-4-teilbaren Längen werden die letzten
 * Stellen als kürzerer Block angehängt.
 */
private fun formatMatrikelGrouped(matrikel: String): String =
    matrikel.chunked(4).joinToString(" ")
