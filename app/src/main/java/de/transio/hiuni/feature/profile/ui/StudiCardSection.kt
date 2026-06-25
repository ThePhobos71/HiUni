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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.transio.hiuni.core.auth.UserProfile
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii

/**
 * Digitale Studi-Karte — soll sich anfühlen wie ein echter Plastikausweis im Wallet.
 *
 * Layout:
 *  - Außen: Surface mit großzügig gerundeten Ecken + Soft-Shadow, Hintergrund als
 *    diagonaler Gradient von `colors.primary` (oben links) zu einer leicht abgedunkelten
 *    Variante (unten rechts). Plus dezente Deko-Kreise (Disc-Pattern) hinter dem Inhalt
 *    für Brand-Look — kein Bild-Asset nötig.
 *  - Oben: zweizeiliger Header: "UNI HILDESHEIM" (klein, gespacet) + "Studierendenausweis"
 *    in titleMedium, gegenüber eine grüne OFFLINE-Pill als Status.
 *  - Mitte: voller Name (headlineMedium, fett), darunter Matrikel-Label + Matrikelnummer
 *    monospaced in 4er-Gruppen.
 *  - Unten: weißer Barcode-Streifen auf eigenem Surface (für Scan-Kontrast), darüber
 *    der Code-128-Barcode + die wiederholte Matrikel-Klartext-Zeile.
 *  - Ganz unten: kleine Footnote "Funktioniert ohne Internet".
 *
 * Edge-Cases:
 *  - Keine Matrikel → kompakter "nicht verfügbar"-Hinweis.
 *  - Barcode-Encode scheitert → ganze Section wird ausgeblendet (selten).
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
    val barcodeHeightPx = with(density) { 64.dp.roundToPx() }
    val barcodeWidthPx = 720

    val barcode = rememberCode128Bitmap(
        content = matrikel,
        widthPx = barcodeWidthPx,
        heightPx = barcodeHeightPx
    ) ?: return

    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    val gradient = Brush.linearGradient(
        colors = listOf(colors.primary, colors.primary.copy(alpha = 0.78f)),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(HiUniRadii.big),
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(HiUniRadii.big),
                clip = false
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(HiUniRadii.big))
                .background(gradient)
        ) {
            // Dezente Deko-Kreise im Hintergrund (Brand-Akzent ohne Asset)
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
            )
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .align(Alignment.BottomStart)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
                // ── Header ─────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "UNIVERSITÄT HILDESHEIM",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onPrimary.copy(alpha = 0.7f),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Studierendenausweis",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(HiUniRadii.pill)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(semantics.green)
                            )
                            Text(
                                text = "OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // ── Name + Matrikel ────────────────────────────────────
                Text(
                    text = profile.fullName?.takeIf { it.isNotBlank() } ?: "Studi*in",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.onPrimary,
                    maxLines = 2
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "MATRIKEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimary.copy(alpha = 0.6f),
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatMatrikelGrouped(matrikel),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 3.sp
                    ),
                    color = colors.onPrimary
                )

                Spacer(Modifier.height(22.dp))

                // ── Barcode-Streifen (eigenes weißes Surface für Scan-Kontrast) ──
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(HiUniRadii.card),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Image(
                            bitmap = barcode,
                            contentDescription = "Code-128-Barcode mit Matrikelnummer",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            contentScale = ContentScale.FillBounds,
                            filterQuality = FilterQuality.None
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = matrikel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 4.sp
                            ),
                            color = Color.Black.copy(alpha = 0.75f),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Funktioniert ohne Internet · Halte den Code unter den Scanner",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimary.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
